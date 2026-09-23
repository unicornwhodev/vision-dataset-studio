package com.unicornwhodev.visiondatasetstudio

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.training.TrainingTargets
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

/** Opt-in fixture is compiled without training on the host; all optimizer steps below run in Android. */
class OnDeviceTrainingTest {
    @Test fun internalVisualWeightsTrainPersistAndReloadOnAndroid() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.filesDir,"training-fixture")
        val model=File(root,"trainable-vision-fixture.tflite")
        assumeTrue("Stage the compiled untrained fixture using the documented QA command",model.isFile)
        val config=StudioJson.moshi.adapter(ModelConfig::class.java).fromJson(File(root,"model_config.json").readText())!!
        ModelContract.validate(config)
        val red=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(230,20,15))}
        val blue=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(10,25,235))}
        val r=floatArrayOf(1f,0f);val b=floatArrayOf(0f,1f)
        var checkpoint:CheckpointReceipt?=null;var expected=emptyList<Float>();var beforeWeights="";var afterWeights=""
        var initial=0.0;var finalLoss=0.0
        try {
            LiteRtTrainingSession(model,config).use { session ->
                initial=(TrainingTargets.validationLoss(session.infer(red),r,config)+TrainingTargets.validationLoss(session.infer(blue),b,config))/2
                beforeWeights=requireNotNull(session.weightProbe())
                repeat(24){session.train(red,r,.05f);session.train(blue,b,.05f)}
                afterWeights=requireNotNull(session.weightProbe())
                assertNotEquals("Internal visual kernel must change, not only the classifier",beforeWeights,afterWeights)
                finalLoss=(TrainingTargets.validationLoss(session.infer(red),r,config)+TrainingTargets.validationLoss(session.infer(blue),b,config))/2
                assertTrue("Supervised loss must decrease: $initial -> $finalLoss",finalLoss<initial*.5)
                expected=session.infer(red).single().values.toList()
                checkpoint=session.save(File(root,"checkpoint-${System.nanoTime()}"))
            }
            LiteRtTrainingSession(model,config).use { fresh ->
                assertEquals(beforeWeights,fresh.weightProbe())
                fresh.restore(requireNotNull(checkpoint))
                assertEquals(afterWeights,fresh.weightProbe())
                assertArrayEquals(expected.toFloatArray(),fresh.infer(red).single().values,1e-6f)
            }
            // The same learned checkpoint is also consumed by the application's regular inference engine.
            val receipt=com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining.saveCheckpointReceipt(model,requireNotNull(checkpoint))
            kotlinx.coroutines.runBlocking {
                LiteRtEngine().use { engine ->
                    assertTrue(engine.loadModel(model));val predictions=engine.runInference(red,config.copy(trainingCheckpoint=receipt)).orThrow()
                    assertNull(engine.lastError);assertEquals("rouge",predictions.maxBy{it.score}.label)
                    val untrained=engine.runInference(red,config).orThrow()
                    assertNull(engine.lastError)
                    assertNotEquals("Changing the selected checkpoint must reset the cached session",predictions.map{it.score},untrained.map{it.score})
                    val restored=engine.runInference(red,config.copy(trainingCheckpoint=receipt)).orThrow()
                    assertNull(engine.lastError)
                    assertEquals(predictions.map{it.score},restored.map{it.score})
                }
            }
            File(root,"android-training-evidence.json").writeText(StudioJson.moshi.adapter(Any::class.java).indent("  ").toJson(mapOf(
                "page_size" to android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE),
                "supported_abis" to android.os.Build.SUPPORTED_ABIS.toList(),
                "runtime" to "Android LiteRT Interpreter", "optimizer_steps" to 48,"initial_loss" to initial,"final_loss" to finalLoss,
                "internal_weights_before" to beforeWeights,"internal_weights_after" to afterWeights,"checkpoint_reload_matches" to true,
                "production_hf_models_trained" to false,"fixture" to "synthetic red/blue images; untrained two-layer visual network")))
        } finally {red.recycle();blue.recycle()}
    }
}
