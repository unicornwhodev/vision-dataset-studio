package com.unicornwhodev.visiondatasetstudio.domain.training

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import androidx.work.*
import androidx.room.withTransaction
import com.squareup.moshi.JsonClass
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.ProjectEntity
import com.unicornwhodev.visiondatasetstudio.data.model.SampleAnnotations
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

@JsonClass(generateAdapter=true)
data class TrainingSample(val image:String,val sha256:String,val target:List<Float>,val validation:Boolean)
@JsonClass(generateAdapter=true)
data class DeviceTrainingRun(
    val id:String,val projectId:Long,val modelFile:String,val modelSha256:String,val config:ModelConfig,
    val samples:List<TrainingSample>,val epochs:Int=3,val learningRate:Float=.001f,
    val phase:String="queued",val completedSteps:Int=0,val totalSteps:Int=0,
    val initialLoss:Double?=null,val validationLoss:Double?=null,
    val initialWeightProbe:String?=null,val finalWeightProbe:String?=null,
    val sourceBatchNumber:Int=0,val exportSnapshot:String="",val exportProof:String="",
    val checkpoint:CheckpointReceipt?=null,val error:String?=null,val createdAt:Long=System.currentTimeMillis()
)

class OnDeviceTraining(private val context:Context) {
    private val adapter=StudioJson.moshi.adapter(DeviceTrainingRun::class.java)
    private val configAdapter=StudioJson.moshi.adapter(ModelConfig::class.java)
    private val annotationsAdapter=StudioJson.moshi.adapter(SampleAnnotations::class.java)
    private fun index(projectId:Long)=File(context.filesDir,"training/$projectId.json").apply{parentFile?.mkdirs()}
    private fun readRecord(file:File):DeviceTrainingRun? {
        if(!file.exists() && !File(file.path+".bak").exists())return null
        return adapter.fromJson(AtomicFile(file).openRead().bufferedReader().use{it.readText()})
    }
    fun read(projectId:Long):DeviceTrainingRun? = synchronized(recordLock) {readRecord(index(projectId))}
    fun readBatch(projectId:Long,batchNumber:Int):DeviceTrainingRun? = synchronized(recordLock) {
        readRecord(File(context.filesDir,"training/$projectId-batch-$batchNumber.json"))
            ?: readRecord(index(projectId))?.takeIf{it.sourceBatchNumber==batchNumber}
    }
    fun write(run:DeviceTrainingRun) = synchronized(recordLock) {
        val atomic=AtomicFile(index(run.projectId));val stream=atomic.startWrite()
        try{stream.write(adapter.toJson(run).toByteArray());atomic.finishWrite(stream)}catch(e:Exception){atomic.failWrite(stream);throw e}
        if(run.sourceBatchNumber>0) {
            val batch=AtomicFile(File(context.filesDir,"training/${run.projectId}-batch-${run.sourceBatchNumber}.json"))
            val batchOut=batch.startWrite()
            try{batchOut.write(adapter.toJson(run).toByteArray());batch.finishWrite(batchOut)}catch(e:Exception){batch.failWrite(batchOut);throw e}
        }
        // Each run keeps an immutable identity and its own evolving receipt, even after a later generation starts.
        val receipt=AtomicFile(File(File(run.modelFile).parentFile,"training-run.json"));val out=receipt.startWrite()
        try{out.write(adapter.toJson(run).toByteArray());receipt.finishWrite(out)}catch(e:Exception){receipt.failWrite(out);throw e}
    }
    suspend fun prepare(project:ProjectEntity,batchNumber:Int,epochs:Int=3,learningRate:Float=.001f):DeviceTrainingRun = withContext(Dispatchers.IO) { executionMutex.withLock {
        require(epochs in 1..30 && learningRate in .000001f..1f)
        val old=read(project.id)
        check(old?.phase !in setOf("queued","training","evaluating")){"Un apprentissage est déjà en cours"}
        val config=configAdapter.failOnUnknown().fromJson(project.modelConfigJson ?: error("Contrat modèle absent")) ?: error("Contrat absent")
        ModelContract.validate(config);require(config.training!=null){"La conversion active ne fournit pas encore train/infer/save/restore"}
        require(config.bundleKind.isBlank()){"Le contrat d’apprentissage doit désigner un graphe entraînable unique"}
        val source=File(project.modelPath ?: error("Poids absents"));require(source.isFile)
        val db=AppDatabase.getInstance(context)
        val (batch,rows)=db.withTransaction {
            val batch=db.batchDao().getBatchSync(project.id,batchNumber) ?: error("Lot absent")
            require(batch.status=="VERIFIED" && batch.verificationKind in setOf("local","hf","both")) {
                "Exportez et vérifiez ce lot avant l’apprentissage"
            }
            require(batch.archiveSnapshot==com.unicornwhodev.visiondatasetstudio.domain.batch.BatchSnapshot.compute(db,project.id,batchNumber)) {
                "Les données du lot diffèrent de l’export vérifié"
            }
            val previousBatch=readBatch(project.id,batchNumber)
            check(previousBatch?.exportSnapshot!=batch.archiveSnapshot || previousBatch.phase !in setOf("completed","rejected")) {
                "L’apprentissage de cet export est déjà terminé"
            }
            val samples=db.sampleDao().getSamplesForBatchSync(project.id,batchNumber)
            require(samples.all{it.annotationStatus in setOf("VALIDATED","REJECTED","DUPLICATE")}){"Lot non terminé"}
            batch to samples.filter{it.annotationStatus=="VALIDATED"}.sortedBy{it.sampleId}
                .map{row -> row to (db.annotationDao().getAnnotationSync(row.sampleId)?.dataJson ?: error("Annotations absentes"))}
        }
        require(rows.size.toLong()*config.training.targetShape.fold(1L){a,b->a*b}<=4_000_000L){"Lot trop volumineux pour les cibles de ce modèle sur cet appareil"}
        require(rows.isNotEmpty()){"Aucune image acceptée dans cet export"}
        LiteRtTrainingSession(source,config).use{} // Verify signatures before copying the exported corpus.
        val prepared=rows.map { (row,annotationJson) ->
            val image=File(row.localImagePath!!);require(image.isFile){"Image validée absente du stockage local"}
            val sha=HashUtils.computeSha256(image)
            require(row.sha256==null || row.sha256==sha){"Image modifiée après annotation"}
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(image.path,bounds)
            require(bounds.outWidth>0 && bounds.outHeight>0)
            val a=annotationsAdapter.fromJson(annotationJson) ?: error("Annotations invalides")
            val targets=TrainingTargets.encode(a,config,bounds.outWidth,bounds.outHeight)
            Triple(image,sha,targets.toList())
        }
        val byImage=prepared.groupBy{it.second}
        require(byImage.values.all{group->group.map{it.third}.distinct().size==1}){"Annotations contradictoires pour des fichiers identiques"}
        val unique=byImage.values.map{it.first()}
        val train=unique.count{!holdout(it.second)};val validation=unique.size-train
        require(train>=32 && validation>=8){"Il faut 32 images d’apprentissage et 8 de contrôle ; disponibles : $train / $validation"}
        val needed=source.length()*3+unique.sumOf{it.first.length()}+16L*1024*1024
        val policy=ProjectSettings.read(project)
        require(StorageManager(context).hasAvailableBudget(needed,project.diskBudgetMb,policy.reserveFreeMb)){"Budget disque insuffisant pour conserver les checkpoints et images d’apprentissage"}
        val id=UUID.randomUUID().toString();val directory=File(context.filesDir,"models/training-$id").apply{mkdirs()}
        try {
            val model=File(directory,"model.tflite");source.copyTo(model)
            val samples=unique.map{(image,sha,target)->val copy=File(directory,"images/$sha").apply{parentFile?.mkdirs()};image.copyTo(copy);TrainingSample(copy.path,sha,target,holdout(sha))}
            // A resumed generation starts from the active checkpoint; the original profile is never modified.
            val inherited=if(config.trainingCheckpoint.isBlank())null else readCheckpoint(source,config.trainingCheckpoint)
            val baseConfig=config.copy(trainingCheckpoint="")
            val checkpoint=if(inherited!=null)LiteRtTrainingSession(model,baseConfig).use{session->session.restore(inherited);session.save(File(directory,"inherited"))}else null
            DeviceTrainingRun(id,project.id,model.path,HashUtils.computeSha256(model),baseConfig,samples,epochs,learningRate,totalSteps=train*epochs,sourceBatchNumber=batchNumber,exportSnapshot=batch.archiveSnapshot!!,exportProof=batch.verifiedArchiveSha256 ?: batch.preparedManifestSha256 ?: error("Preuve d’export absente"),checkpoint=checkpoint).also(::write)
        } catch(e:Exception){directory.deleteRecursively();throw e}
    } }
    /** Training never delays a disabled workflow; active/failed runs keep their source lot until explicitly resolved. */
    fun requireCleanupAllowed(project:ProjectEntity,batchNumber:Int,hasAcceptedImages:Boolean) {
        val run=readBatch(project.id,batchNumber)
        val finished=run?.phase in setOf("completed","rejected")
        if(run!=null && !finished)error("Apprentissage du lot non terminé. Reprenez-le avant le nettoyage.")
        if(hasAcceptedImages && ProjectSettings.read(project).continuousTraining && !finished)
            error("Apprentissage optionnel activé : entraînez ce lot exporté avant le nettoyage, ou désactivez l’option.")
    }
    fun releaseBatchImages(projectId:Long,batchNumber:Int) {
        val run=readBatch(projectId,batchNumber) ?: return
        check(run.phase in setOf("completed","rejected")){"Apprentissage non terminé"}
        val models=File(context.filesDir,"models").canonicalFile
        val root=File(run.modelFile).parentFile!!.canonicalFile
        check(root.parentFile==models && root.name=="training-${run.id}")
        val images=File(root,"images")
        check(!images.exists() || images.deleteRecursively()){"Nettoyage des images d’apprentissage incomplet; relancez le nettoyage"}
    }
    fun enqueue(projectId:Long) {
        val run=read(projectId) ?: error("Apprentissage absent")
        val request=OneTimeWorkRequestBuilder<DeviceTrainingWorker>().setInputData(workDataOf("projectId" to projectId,"runId" to run.id))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).addTag("vds-training").build()
        WorkManager.getInstance(context).enqueueUniqueWork("vds-training-$projectId",ExistingWorkPolicy.KEEP,request)
    }
    suspend fun cancel(projectId:Long)=withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork("vds-training-$projectId").result.get()
        executionMutex.withLock {
            read(projectId)?.takeIf{it.phase in setOf("queued","training","evaluating")}?.let{
                write(it.copy(phase="cancelled",error="Interrompu ; dernier checkpoint conservé"))
            }
        }
    }
    suspend fun resume(projectId:Long)=withContext(Dispatchers.IO) { executionMutex.withLock {
        val run=read(projectId) ?: error("Aucun apprentissage à reprendre")
        require(run.phase in setOf("cancelled","failed"))
        write(run.copy(phase="queued",error=null));enqueue(projectId)
    } }
    companion object {
        internal val executionMutex=Mutex()
        private val recordLock=Any()
        fun holdout(sha:String)=sha.take(8).toLong(16)%5==0L
        fun readCheckpoint(model:File,relative:String):CheckpointReceipt {
            val file=File(model.parentFile,relative)
            require(file.canonicalPath.startsWith(model.parentFile!!.canonicalPath+File.separator))
            return (StudioJson.moshi.adapter(CheckpointReceipt::class.java).fromJson(file.readText()) ?: error("Reçu de checkpoint invalide")).also{
                require(File(it.prefix).canonicalPath.startsWith(model.parentFile!!.canonicalPath+File.separator))
            }
        }
        fun saveCheckpointReceipt(model:File,checkpoint:CheckpointReceipt):String {
            val name="active-checkpoint.json";val atomic=AtomicFile(File(model.parentFile,name));val out=atomic.startWrite()
            try{out.write(StudioJson.moshi.adapter(CheckpointReceipt::class.java).toJson(checkpoint).toByteArray());atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e}
            return name
        }
    }
}

class DeviceTrainingWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result = withContext(Dispatchers.Default) { OnDeviceTraining.executionMutex.withLock {
        val store=OnDeviceTraining(applicationContext);val projectId=inputData.getLong("projectId",-1)
        var run=store.read(projectId) ?: return@withLock Result.failure()
        if(run.id!=inputData.getString("runId"))return@withLock Result.success()
        if(run.phase in setOf("completed","rejected","cancelled"))return@withLock Result.success()
        var durableSteps=run.completedSteps
        try {
            require(run.sourceBatchNumber>0 && run.exportSnapshot.isNotBlank() && run.exportProof.isNotBlank()) { "Ancienne préparation sans export vérifié; préparez le lot exporté" }
            val model=File(run.modelFile);require(model.isFile && HashUtils.computeSha256(model)==run.modelSha256)
            run.samples.forEach{require(File(it.image).isFile && HashUtils.computeSha256(File(it.image))==it.sha256){"Snapshot d’apprentissage altéré"}}
            val training=run.samples.filterNot{it.validation};val validation=run.samples.filter{it.validation}
            require(training.size>=32 && validation.size>=8)
            suspend fun alive(){currentCoroutineContext().ensureActive();if(isStopped)throw CancellationException("Apprentissage interrompu")}
            fun bitmap(sample:TrainingSample):android.graphics.Bitmap {
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(sample.image,bounds)
                var scale=1;while(maxOf(bounds.outWidth,bounds.outHeight)/scale>2048)scale*=2
                return BitmapFactory.decodeFile(sample.image,BitmapFactory.Options().apply{inSampleSize=scale}) ?: error("Image d’apprentissage indécodable")
            }
            LiteRtTrainingSession(model,run.config).use { session ->
                run.checkpoint?.let(session::restore)
                suspend fun evaluate():Double {
                    var sum=0.0
                    for(sample in validation){alive();val image=bitmap(sample);try{sum+=TrainingTargets.validationLoss(session.infer(image),sample.target.toFloatArray(),run.config)}finally{image.recycle()}}
                    return sum/validation.size
                }
                if(run.initialLoss==null){run=run.copy(initialLoss=evaluate(),initialWeightProbe=session.weightProbe());store.write(run)}
                run=run.copy(phase="training",error=null);store.write(run)
                val directory=model.parentFile!!
                try {
                    for(step in run.completedSteps until run.totalSteps) {
                        alive();val sample=training[step%training.size];val image=bitmap(sample)
                        try{session.train(image,sample.target.toFloatArray(),run.learningRate)}finally{image.recycle()}
                        run=run.copy(completedSteps=step+1)
                        if((step+1)%8==0 || step+1==run.totalSteps){
                            val previous=run.checkpoint
                            val checkpoint=session.save(File(directory,"checkpoint-${UUID.randomUUID()}"))
                            run=run.copy(checkpoint=checkpoint);durableSteps=run.completedSteps;store.write(run)
                            // Only an obsolete private candidate checkpoint is reclaimed; the active model is separate.
                            previous?.let{val old=File(it.prefix).parentFile!!;if(old.canonicalPath.startsWith(directory.canonicalPath+File.separator))old.deleteRecursively()}
                            setProgress(workDataOf("steps" to run.completedSteps,"total" to run.totalSteps))
                        }
                    }
                    run=run.copy(phase="evaluating");store.write(run)
                    val score=evaluate();val probe=session.weightProbe()
                    val improved=score<requireNotNull(run.initialLoss)*.99 && (probe==null || probe!=run.initialWeightProbe)
                    run=run.copy(phase=if(improved)"completed" else "rejected",validationLoss=score,finalWeightProbe=probe)
                    // Reopen from the serialized checkpoint and independently reproduce validation outputs.
                    val sample=validation.first();val image=bitmap(sample)
                    val before=try{session.infer(image).flatMap{it.values.toList()}}finally{image.recycle()}
                    LiteRtTrainingSession(model,run.config).use{fresh->
                        fresh.restore(requireNotNull(run.checkpoint));val reload=bitmap(sample)
                        val after=try{fresh.infer(reload).flatMap{it.values.toList()}}finally{reload.recycle()}
                        require(before.size==after.size && before.indices.all{kotlin.math.abs(before[it]-after[it])<=1e-5f}){"Checkpoint non reproductible après rechargement"}
                    }
                    store.write(run)
                } catch(e:CancellationException) {
                    // Persist a complete checkpoint before yielding, even when WorkManager cancels execution.
                    withContext(NonCancellable){
                        val checkpoint=session.save(File(directory,"checkpoint-${UUID.randomUUID()}"))
                        run=run.copy(phase="cancelled",checkpoint=checkpoint,error="Interrompu ; checkpoint conservé");store.write(run)
                    }
                    throw e
                }
            }
            Result.success()
        } catch(e:CancellationException){
            // Cancellation can happen while checking images or evaluating the initial model,
            // before the training loop's checkpoint handler is entered.
            if(run.phase!="cancelled") withContext(NonCancellable) {
                store.write(run.copy(phase="cancelled",completedSteps=durableSteps,error="Interrompu ; reprise depuis le dernier checkpoint"))
            }
            throw e
        }
        catch(e:Exception){store.write(run.copy(phase="failed",completedSteps=durableSteps,error=e.message ?: "Échec d’apprentissage"));Result.failure()}
    } }
}
