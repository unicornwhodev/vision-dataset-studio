package com.unicornwhodev.visiondatasetstudio

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.data.hf.HfTreeItem
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class HfModelRuntimeTest {
    private fun run(id:String) = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.filesDir,"hf-runtime-fixture/$id")
        assumeTrue("Stage the pinned, public HF fixture",File(root,"artifact_manifest.json").isFile)
        val spec=CommunityModelCatalog.entries.single{it.id==id}
        val revision=(File(root,"revision.txt").takeIf{it.isFile} ?: File(context.filesDir,"hf-runtime-fixture/revision.txt")).readText().trim()
        val item=CommunityModelCatalog.Availability(spec,revision,root.walkTopDown().filter{it.isFile}.map{HfTreeItem("models/$id/"+it.relativeTo(root).invariantSeparatorsPath,"file",it.length())}.toList(),true,true,"QA")
        val file=if(spec.expectedFiles.size==1)File(root,spec.expectedFiles.single()) else {
            val manifest=BundleManifest(kind=id,revision=revision,files=root.walkTopDown().filter{it.isFile && it.name!="bundle.json" && !it.name.startsWith("android-")}.associate{it.relativeTo(root).invariantSeparatorsPath to com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils.computeSha256(it)})
            File(root,"bundle.json").apply{writeText(StudioJson.moshi.adapter(BundleManifest::class.java).toJson(manifest))}
        }
        val base=CommunityModelCatalog.suggestedConfig(item,file)
        val config=when(id){"tinyclip"->base.copy(labels=listOf("red rectangle","blue circle"),threshold=0f);"efficientvit_sam"->base.copy(promptBox=listOf(.2f,.2f,.7f,.8f),threshold=0f);else->base}
        val image=Bitmap.createBitmap(128,96,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(image);canvas.drawColor(Color.rgb(30,40,60));canvas.drawRect(24f,18f,92f,78f,android.graphics.Paint().apply{color=Color.RED})
        val start=System.nanoTime()
        try {
            LiteRtEngine().use{engine->
                assertTrue(engine.lastError,engine.loadModel(file));val out=engine.runInference(image,config)
                assertNull(engine.lastError,engine.lastError)
                if(id in setOf("dinov2","repvit_m1","hgnetv2_b0","convformer_s18","tinyclip"))assertTrue(requireNotNull(engine.lastEmbedding).all(Float::isFinite))
                if(id=="dinov2")assertEquals(listOf(1,256,384),engine.lastPatches?.shape)
                if(id=="tinyclip")assertEquals(2,out.size)
                if(id=="efficientvit_sam")assertNotNull(engine.lastMask)
                File(root,"android-result.json").writeText(StudioJson.moshi.adapter(Any::class.java).indent("  ").toJson(mapOf("model" to id,"revision" to revision,"runtime" to "Android LiteRT 1.4.2 CPU / Select TF Ops 2.16.1","success" to true,"proposals" to out.size,"embedding_size" to engine.lastEmbedding?.size,"milliseconds" to (System.nanoTime()-start)/1_000_000,"accuracy_evaluated" to false,"note" to engine.lastNote)))
            }
        }finally{image.recycle()}
    }
    @Test fun dinov2()=run("dinov2")
    @Test fun convformer()=run("convformer_s18")
    @Test fun repvit()=run("repvit_m1")
    @Test fun hgnet()=run("hgnetv2_b0")
    @Test fun vitpose()=run("vitpose")
    @Test fun rfdetr()=run("rfdetr")
    @Test fun tinyclip()=run("tinyclip")
    @Test fun sam()=run("efficientvit_sam")
    @Test fun florence()=run("florence2")
}
