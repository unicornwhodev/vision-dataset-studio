package com.unicornwhodev.visiondatasetstudio.domain.batch

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import java.io.File
import com.squareup.moshi.JsonClass
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson

@JsonClass(generateAdapter=true)
data class MaintenanceReceipt(val action:String,val projectId:Long,val batchNumber:Int?=null,val sampleIds:List<String>,val stage:String="prepared")

/** Destructive local operations. Callers must obtain explicit confirmation before invoking them. */
class ProjectMaintenance(private val context:Context,private val db:AppDatabase,private val storage:StorageManager,private val interruptionPoint:(String)->Unit={}) {
    private val receiptFile=File(context.filesDir,"maintenance/pending.json")
    private val receiptAdapter=StudioJson.moshi.adapter(MaintenanceReceipt::class.java)
    private fun receipt(value:MaintenanceReceipt){receiptFile.parentFile?.mkdirs();val tmp=File(receiptFile.path+".tmp");tmp.writeText(receiptAdapter.toJson(value));check(tmp.renameTo(receiptFile))}
    suspend fun resumePending() { val pending=receiptFile.takeIf{it.isFile}?.let{receiptAdapter.fromJson(it.readText())}?:return
        val databaseDone=pending.stage=="database_committed" || when(pending.action){"batch"->db.batchDao().getBatchSync(pending.projectId,pending.batchNumber!!)==null;"delete"->db.projectDao().getProjectSync(pending.projectId)==null;else->db.sampleDao().getAllSamples(pending.projectId).isEmpty()&&db.batchDao().getLatestBatchSync(pending.projectId)==null}
        if(!databaseDone)when(pending.action){"batch"->resetBatch(pending.projectId,pending.batchNumber!!);"project"->resetProject(pending.projectId);"delete"->deleteProject(pending.projectId)}
        else { storage.purgeVerifiedBatchMedia(pending.sampleIds);if(pending.action=="batch")deleteBatchFiles(pending.projectId,pending.batchNumber!!)else deleteProjectFiles(pending.projectId,pending.action=="delete");receiptFile.delete() }
    }
    suspend fun resetBatch(projectId:Long,batchNumber:Int) {
        val training=com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining(context).readBatch(projectId,batchNumber)
        check(training?.phase !in setOf("queued","training","evaluating")) { "Interrompez explicitement l’apprentissage avant de réinitialiser son lot" }
        val samples=db.sampleDao().getSamplesForBatchSync(projectId,batchNumber)
        receipt(MaintenanceReceipt("batch",projectId,batchNumber,samples.map{it.sampleId}))
        val rewind=samples.mapNotNull{it.sourceOrdinal}.minOrNull()
        db.withTransaction {
            val project=db.projectDao().getProjectSync(projectId) ?: error("Projet absent")
            db.annotationDao().deleteBatchAnnotations(projectId,batchNumber)
            db.auditDao().deleteBatchLogs(projectId,batchNumber)
            db.imageIdentityDao().deleteBatch(projectId,batchNumber)
            db.sampleDao().deleteBatchSamples(projectId,batchNumber)
            db.batchDao().deleteBatch(projectId,batchNumber)
            if(rewind!=null)db.projectDao().saveProject(project.copy(lastRowCursor=minOf(project.lastRowCursor,rewind),updatedAt=System.currentTimeMillis()))
        }
        receipt(MaintenanceReceipt("batch",projectId,batchNumber,samples.map{it.sampleId},"database_committed"));interruptionPoint("after_database")
        storage.purgeVerifiedBatchMedia(samples.map{it.sampleId})
        deleteBatchFiles(projectId,batchNumber)
        receiptFile.delete()
    }

    suspend fun resetProject(projectId:Long) {
        val samples=db.sampleDao().getAllSamples(projectId)
        receipt(MaintenanceReceipt("project",projectId,sampleIds=samples.map{it.sampleId}))
        WorkManager.getInstance(context).cancelUniqueWork("vds-training-$projectId").result.get()
        db.withTransaction {
            val project=db.projectDao().getProjectSync(projectId) ?: error("Projet absent")
            db.annotationDao().deleteProjectAnnotations(projectId);db.auditDao().deleteProjectLogs(projectId)
            db.sampleDao().deleteProjectSamples(projectId);db.batchDao().deleteProjectBatches(projectId)
            db.sourceEntryDao().clear(projectId);db.imageIdentityDao().deleteProject(projectId)
            val settings=ProjectSettings.read(project).copy(sourceIndexReady=false)
            db.projectDao().saveProject(project.copy(lastRowCursor=0,settingsJson=ProjectSettings.write(settings),updatedAt=System.currentTimeMillis()))
        }
        receipt(MaintenanceReceipt("project",projectId,sampleIds=samples.map{it.sampleId},stage="database_committed"));interruptionPoint("after_database")
        storage.purgeVerifiedBatchMedia(samples.map{it.sampleId});deleteProjectFiles(projectId,false)
        com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining(context).cleanupOrphanedCandidates(db)
        receiptFile.delete()
    }

    suspend fun deleteProject(projectId:Long) {
        val samples=db.sampleDao().getAllSamples(projectId)
        receipt(MaintenanceReceipt("delete",projectId,sampleIds=samples.map{it.sampleId}))
        WorkManager.getInstance(context).cancelUniqueWork("vds-training-$projectId").result.get()
        db.withTransaction {
            db.annotationDao().deleteProjectAnnotations(projectId);db.auditDao().deleteProjectLogs(projectId)
            db.sampleDao().deleteProjectSamples(projectId);db.batchDao().deleteProjectBatches(projectId)
            db.sourceEntryDao().clear(projectId);db.imageIdentityDao().deleteProject(projectId);db.projectDao().deleteProject(projectId)
        }
        receipt(MaintenanceReceipt("delete",projectId,sampleIds=samples.map{it.sampleId},stage="database_committed"));interruptionPoint("after_database")
        storage.purgeVerifiedBatchMedia(samples.map{it.sampleId});deleteProjectFiles(projectId,true)
        com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining(context).cleanupOrphanedCandidates(db)
        // Shared model profiles and their referenced weights are intentionally untouched.
        receiptFile.delete()
    }

    private fun deleteBatchFiles(projectId:Long,batchNumber:Int) {
        storage.batchExportDir(projectId,batchNumber).deleteRecursively();storage.batchArchiveFile(projectId,batchNumber).delete()
        File(context.filesDir,"training/$projectId-batch-$batchNumber.json").delete()
    }
    private fun deleteProjectFiles(projectId:Long,deleteReceipts:Boolean) {
        storage.exportsDir.listFiles()?.filter{it.name.startsWith("p-$projectId-batch-")}?.forEach{it.deleteRecursively()}
        File(context.filesDir,"embeddings/$projectId").deleteRecursively();File(context.filesDir,"corrections/$projectId.json").delete()
        if(deleteReceipts)com.unicornwhodev.visiondatasetstudio.domain.inference.InferenceReceiptStore(context.filesDir).deleteProject(projectId)
        File(context.filesDir,"training").listFiles()?.filter{it.name=="$projectId.json" || it.name.startsWith("$projectId-batch-")}?.forEach{it.delete()}
        storage.clearTempFiles()
    }
}
