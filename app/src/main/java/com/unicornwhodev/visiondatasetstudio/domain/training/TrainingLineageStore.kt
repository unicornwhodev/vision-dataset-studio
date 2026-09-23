package com.unicornwhodev.visiondatasetstudio.domain.training

import android.content.Context
import android.util.AtomicFile
import com.squareup.moshi.JsonClass
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.domain.inference.CheckpointReceipt
import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig
import java.io.File
import java.security.MessageDigest

@JsonClass(generateAdapter=true)
data class LearnedTrainingModel(val runId:String,val generation:Int,val modelFile:String,
    val modelSha256:String,val checkpoint:CheckpointReceipt)

@JsonClass(generateAdapter=true)
data class TrainingLineage(val id:String,val projectId:Long,val originalModelFile:String,
    val originalModelSha256:String,val originalConfig:ModelConfig,val learned:LearnedTrainingModel?=null)

/** The original is immutable. A single durable pointer advances only after checkpoint validation. */
class TrainingLineageStore(context:Context) {
    private val root=File(context.filesDir,"training/lineages")
    private val adapter=StudioJson.moshi.adapter(TrainingLineage::class.java)
    private val configAdapter=StudioJson.moshi.adapter(ModelConfig::class.java)
    private val runAdapter=StudioJson.moshi.adapter(DeviceTrainingRun::class.java)
    private fun file(id:String):File {
        require(id.matches(Regex("[0-9a-f]{64}")))
        return File(root,"$id.json")
    }
    fun read(id:String):TrainingLineage? {
        val file=file(id)
        if(!file.exists() && !File(file.path+".bak").exists())return null
        return AtomicFile(file).openRead().bufferedReader().use{adapter.fromJson(it.readText())}.also {
            require(it?.id==id){tr("Filiation du modèle invalide", "Invalid model lineage")}
        }
    }
    fun all():List<TrainingLineage> = root.listFiles().orEmpty()
        .map{it.name.removeSuffix(".bak")}.filter{it.matches(Regex("[0-9a-f]{64}\\.json"))}.distinct()
        .mapNotNull{read(it.removeSuffix(".json"))}

    /** Read-only: selecting the original or any saved generation continues the same learned version. */
    fun resolve(projectId:Long,source:File,config:ModelConfig):TrainingLineage {
        val canonical=source.canonicalPath
        val stored=all().firstOrNull{it.projectId==projectId &&
            File(it.originalModelFile).canonicalPath==canonical && it.originalConfig==config}
        val receipt=File(source.parentFile,"training-run.json")
        val generation=if(receipt.isFile)AtomicFile(receipt).openRead().bufferedReader().use{runAdapter.fromJson(it.readText())} else null
        val inherited=generation?.takeIf{it.projectId==projectId && it.lineageId.isNotBlank() &&
            File(it.modelFile).canonicalPath==canonical && it.config.copy(trainingCheckpoint="")==config.copy(trainingCheckpoint="")}
            ?.let{read(it.lineageId) ?: error(tr("Filiation entraînée absente ; reprise refusée", "Learned lineage missing; continuation refused"))}
        val lineage=stored ?: inherited ?: run {
            val sha=HashUtils.computeSha256(source)
            val key="$projectId\u0000$canonical\u0000$sha\u0000${configAdapter.toJson(config)}"
            val id=MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
            TrainingLineage(id,projectId,canonical,sha,config)
        }
        require(lineage.projectId==projectId)
        val original=File(lineage.originalModelFile)
        require(original.isFile && HashUtils.computeSha256(original)==lineage.originalModelSha256) {
            tr("Modèle original absent ou altéré ; reprise refusée", "Original model missing or modified; continuation refused")
        }
        lineage.learned?.let {
            val learned=File(it.modelFile)
            require(learned.isFile && HashUtils.computeSha256(learned)==it.modelSha256) {
                tr("Version entraînée absente ou altérée ; aucun retour silencieux à l’original", "Learned version missing or modified; no silent reset to the original")
            }
        }
        return lineage
    }
    fun preserve(lineage:TrainingLineage) {
        if(read(lineage.id)==null)write(lineage)
    }
    fun complete(run:DeviceTrainingRun) {
        if(run.lineageId.isBlank())return // Existing receipts remain readable and resumable.
        require(run.phase=="completed" && run.checkpoint!=null)
        val current=requireNotNull(read(run.lineageId))
        require(HashUtils.computeSha256(File(current.originalModelFile))==current.originalModelSha256) {
            tr("Le modèle original a été altéré", "The original model was modified")
        }
        require(HashUtils.computeSha256(File(run.modelFile))==run.modelSha256)
        if(current.learned?.runId==run.id)return
        require(current.projectId==run.projectId && current.learned?.runId==run.parentRunId &&
            run.generation==(current.learned?.generation ?: 0)+1) {
            tr("La version entraînée a changé ; reprise concurrente refusée", "Learned version changed; concurrent continuation refused")
        }
        require(File(run.modelFile).canonicalPath!=File(current.originalModelFile).canonicalPath)
        write(current.copy(learned=LearnedTrainingModel(run.id,run.generation,run.modelFile,run.modelSha256,run.checkpoint)))
    }
    private fun write(lineage:TrainingLineage) {
        root.mkdirs()
        val atomic=AtomicFile(file(lineage.id));val stream=atomic.startWrite()
        try{stream.write(adapter.toJson(lineage).toByteArray(Charsets.UTF_8));atomic.finishWrite(stream)}
        catch(e:Exception){atomic.failWrite(stream);throw e}
    }
}
