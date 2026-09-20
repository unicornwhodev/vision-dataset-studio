package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import org.tensorflow.lite.Interpreter
import java.io.File

/** Honour an explicit converter CPU requirement before the interpreter creates delegates. */
object LiteRtOptions {
    fun forFile(file:File,threads:Int):Interpreter.Options {
        val metadata=File(file.parentFile,"runtime_contract.json")
        val requirement=if(metadata.isFile && metadata.length()<=2*1024*1024) {
            val root=StudioJson.moshi.adapter(Any::class.java).fromJson(metadata.readText()) as? Map<*,*>
            (root?.get("report") as? Map<*,*>)?.get("runtime_requirement")?.toString().orEmpty()
        } else ""
        return Interpreter.Options().setNumThreads(threads).apply {
            if(requirement.contains("disable default XNNPACK",ignoreCase=true))setUseXNNPACK(false)
        }
    }
}
