package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
import android.graphics.Bitmap
import android.os.Build
import android.os.Debug
import com.unicornwhodev.visiondatasetstudio.core.workflow.PerformanceStats
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Measurements belong to this device and this one image, never a public model benchmark. */
object DeviceBenchmark {
    fun memory():Map<String,Long> {
        val info=Debug.MemoryInfo();Debug.getMemoryInfo(info)
        val runtime=Runtime.getRuntime()
        return mapOf("pss_bytes" to info.totalPss*1024L,"java_heap_used_bytes" to runtime.totalMemory()-runtime.freeMemory(),
            "native_heap_allocated_bytes" to Debug.getNativeHeapAllocatedSize())
    }
    suspend fun run(engine:LiteRtEngine,bitmap:Bitmap,config:ModelConfig,loadMs:Double,modelHash:String,
                    repetitions:Int=10,progress:(Int,Int)->Unit = {_,_->}):Map<String,Any> {
        require(repetitions in 5..50)
        val before=memory()
        repeat(3) { coroutineContext.ensureActive();engine.runInference(bitmap,config).orThrow() }
        val times=mutableListOf<Double>();val native=mutableListOf<Double>();val samples=mutableListOf<Map<String,Long>>()
        repeat(repetitions) { index ->
            coroutineContext.ensureActive()
            val start=System.nanoTime();engine.runInference(bitmap,config).orThrow()
            check(engine.lastError==null){engine.lastError ?: tr("Échec", "Failed")}
            times+=(System.nanoTime()-start)/1e6
            engine.lastNativeDurationNanos?.let{native+=it/1e6}
            samples+=memory();progress(index+1,repetitions)
        }
        val contractHash=java.security.MessageDigest.getInstance("SHA-256").digest(config.toString().toByteArray()).joinToString(""){"%02x".format(it)}
        return mapOf("schema_version" to 1,"measured_at_epoch_ms" to System.currentTimeMillis(),"android_sdk" to Build.VERSION.SDK_INT,
            "device_model" to Build.MODEL,"supported_abis" to Build.SUPPORTED_ABIS.toList(),"runtime" to config.runtime,
            "threads" to config.threads,"model_sha256" to modelHash,"contract_sha256" to contractHash,
            "warmups" to 3,"repetitions" to repetitions,"image_width_decoded" to bitmap.width,"image_height_decoded" to bitmap.height,
            "model_load_wall_ms" to loadMs,"wall_ms" to times,"p50_wall_ms" to PerformanceStats.percentile(times,0.5),
            "p95_wall_ms" to PerformanceStats.percentile(times,0.95),"native_invoke_ms" to native,
            "memory_before" to before,"memory_after_each_run" to samples,
            "max_sampled_pss_bytes" to samples.maxOf{it.getValue("pss_bytes")},
            "scope" to "One decoded image; no image decode/storage/network included for LiteRT. PSS sampled AFTER inference, not an absolute peak. HTTP measures client+server latency but client-process memory only. Debug/release build can affect results. No accuracy evaluation.")
    }
}
