package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import com.squareup.moshi.JsonClass
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import java.io.File
import java.util.UUID

@JsonClass(generateAdapter=true)
data class InferenceReceipt(val id:String=UUID.randomUUID().toString(),val projectId:Long,val sampleId:String?,val batchNumber:Int?,
    val timestamp:Long=System.currentTimeMillis(),val outcome:String,val diagnostics:InferenceDiagnostics)

/** Durable, append-only technical receipts. Batch purge never touches this directory. */
class InferenceReceiptStore(filesDir:File) {
    private val root=File(filesDir,"inference-receipts").apply{mkdirs()}
    private val adapter=StudioJson.moshi.adapter(InferenceReceipt::class.java)
    fun write(projectId:Long,sampleId:String?,batchNumber:Int?,result:InferenceResult):File {
        val outcome=when(result){is InferenceResult.Success->"success";is InferenceResult.Empty->"empty";is InferenceResult.Failure->"failure"}
        val receipt=InferenceReceipt(projectId=projectId,sampleId=sampleId,batchNumber=batchNumber,outcome=outcome,diagnostics=result.diagnostics)
        val dir=File(root,projectId.toString()).apply{mkdirs()};val final=File(dir,"${receipt.timestamp}-${receipt.id}.json");val temp=File(dir,".${receipt.id}.tmp")
        temp.outputStream().use{out->out.write(adapter.toJson(receipt).toByteArray());out.fd.sync()}
        check(temp.renameTo(final)){temp.delete();tr("Impossible d’écrire le reçu d’inférence", "Cannot write inference receipt")};return final
    }
    fun list(projectId:Long):List<InferenceReceipt> = File(root,projectId.toString()).listFiles{f->f.extension=="json"}?.sortedBy{it.name}?.map{adapter.fromJson(it.readText())?:error(tr("Reçu invalide", "Invalid receipt"))}.orEmpty()
    fun deleteProject(projectId:Long)=File(root,projectId.toString()).deleteRecursively()
}
