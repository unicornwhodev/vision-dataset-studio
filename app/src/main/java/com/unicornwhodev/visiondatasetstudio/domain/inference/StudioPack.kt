package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.workflow.StudioTask
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter=true)
data class StudioPack(val schema:String="vision-studio-pack/1",val name:String,val classes:List<String>,val tasks:List<String>,val batchSize:Int=100,val model:ModelConfig?=null) {
    fun validate() {
        require(schema=="vision-studio-pack/1" && name.isNotBlank() && name.length<=200)
        require(classes.isNotEmpty() && classes.size<=10000 && classes.all{it.isNotBlank() && !it.contains(',') && it.length<=200} && classes.distinct().size==classes.size)
        require(tasks.isNotEmpty() && tasks.all{task->StudioTask.entries.any{it.name==task}} && tasks.distinct().size==tasks.size)
        require(!tasks.contains("POINTING") || !tasks.contains("POINTING_MULTI"))
        require(batchSize in 1..1000);model?.let(ModelContract::validate)
    }
}
