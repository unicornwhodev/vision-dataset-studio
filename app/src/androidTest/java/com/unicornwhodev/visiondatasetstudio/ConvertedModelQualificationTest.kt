package com.unicornwhodev.visiondatasetstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.data.hf.HfTreeItem
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining
import com.unicornwhodev.visiondatasetstudio.domain.training.TrainingTargets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Opt-in, pinned real conversions. Every optimizer call runs inside this Android process. */
class ConvertedModelQualificationTest {
    @Test fun selectedConversionInfersTrainsAndRestores() = runBlocking {
        val selected=InstrumentationRegistry.getArguments().getString("modelCase")
        assumeTrue("Provide modelCase and stage its checksummed files",selected!=null)
        require(selected!!.matches(Regex("[A-Za-z0-9_.-]+")))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.filesDir,"conversion-qualification/$selected")
        require(root.isDirectory) { "Selected conversion was not staged" }
        val result=linkedMapOf<String,Any?>("case" to selected,"runtime" to "Android LiteRT Interpreter CPU",
            "accuracy_evaluated" to false,"physical_arm_device" to false,"optimizer_on_host" to false)
        val start=System.nanoTime()
        fun phase(value:String) {
            result["phase"]=value;result["elapsed_ms"]=(System.nanoTime()-start)/1_000_000
            File(root,"android-result.json").writeText(StudioJson.moshi.adapter(Any::class.java).indent("  ").toJson(result))
        }
        val image=Bitmap.createBitmap(160,120,Bitmap.Config.ARGB_8888)
        Canvas(image).apply { drawColor(Color.rgb(27,43,69));drawRect(32f,24f,128f,96f,Paint().apply { color=Color.rgb(211,71,38) }) }
        try {
            phase("verify_artifacts")
            val manifest=StudioJson.moshi.adapter(Any::class.java).fromJson(File(root,"artifact_manifest.json").readText()) as Map<*,*>
            manifest.forEach { (name,entry) ->
                val path=File(root,name.toString());require(path.canonicalPath.startsWith(root.canonicalPath+File.separator))
                val meta=entry as Map<*,*>
                require(path.isFile && path.length()==(meta["bytes"] as Number).toLong() && HashUtils.computeSha256(path)==meta["sha256"]) { "Artifact mismatch: $name" }
            }
            val id=File(root,"case-id.txt").readText().trim()
            val revision=File(root,"revision.txt").readText().trim();result["revision"]=revision
            val graphs=root.listFiles()!!.filter { it.extension=="tflite" }.sortedBy { it.name }
            val known=CommunityModelCatalog.entries.singleOrNull { it.id==id }
            val entry=known ?: CommunityModelCatalog.Entry(id,id,"QA","See model card",graphs.map { it.name },"contract","QA")
            val item=CommunityModelCatalog.Availability(entry,revision,root.walkTopDown().filter { it.isFile }.map {
                HfTreeItem("models/$id/"+it.relativeTo(root).invariantSeparatorsPath,"file",it.length())
            }.toList(),true,true,"Pinned QA")
            val model=if(graphs.size==1)graphs.single() else File(root,"bundle.json").apply {
                val files=manifest.keys.associate { name -> name.toString() to HashUtils.computeSha256(File(root,name.toString())) }
                writeText(StudioJson.moshi.adapter(BundleManifest::class.java).toJson(BundleManifest(kind=id,revision=revision,files=files)))
            }
            val originalSha=HashUtils.computeSha256(model);result["model_sha256"]=originalSha
            phase("load_contract")
            var config=CommunityModelCatalog.suggestedConfig(item,model)
            val threads=InstrumentationRegistry.getArguments().getString("modelThreads")?.toInt() ?: config.threads
            require(threads in 1..8);config=config.copy(threads=threads);result["threads"]=threads
            if(id=="tinyclip")config=config.copy(labels=listOf("red rectangle","blue circle"),threshold=0f)
            if(id=="efficientvit_sam")config=config.copy(promptBox=listOf(.2f,.2f,.8f,.8f),threshold=0f)
            result["adapter"]=ModelContract.adapter(config)
            val training=config.training
            if(training==null) {
                phase("inference")
                LiteRtEngine().use { engine ->
                    assertTrue(engine.lastError,engine.loadModel(model,config.threads))
                    val proposals=engine.runInference(image,config).orThrow()
                    assertNull(engine.lastError,engine.lastError)
                    assertTrue(proposals.all { it.score.isFinite() })
                    if(ModelContract.adapter(config)=="embedding")assertTrue(requireNotNull(engine.lastEmbedding).isNotEmpty())
                    if(config.bundleKind=="efficientvit_sam")assertNotNull(engine.lastMask)
                    result["proposals"]=proposals.size;result["embedding_values"]=engine.lastEmbedding?.size
                    result["training"]="not_exposed_by_conversion"
                }
            } else {
                result["training_scope"]=training.scope
                var checkpoint:CheckpointReceipt?=null
                var after=emptyList<TensorValues>()
                var target=FloatArray(0);var auxiliary=emptyMap<String,FloatArray>()
                phase("inference_before_training")
                LiteRtTrainingSession(model,config).use { session ->
                    val before=session.infer(image)
                    result["output_shapes"]=before.map { it.shape }
                    assertTrue(before.isNotEmpty() && before.all { it.values.isNotEmpty() && it.values.all(Float::isFinite) })
                    val annotations=correction(config,before)
                    target=TrainingTargets.encode(annotations,config,image.width,image.height)
                    auxiliary=TrainingTargets.auxiliary(annotations,config)
                    phase("train_step")
                    val loss=session.train(image,target,.001f,auxiliary)
                    assertTrue(loss.isFinite());result["training_loss"]=loss
                    phase("inference_after_training")
                    after=session.infer(image)
                    val delta=maxDifference(before,after)
                    assertTrue("Training must change model outputs",delta>0f)
                    result["output_max_change"]=delta
                    val decoded=ModelAdapters.decode(after.mapIndexed { i,t -> i to t }.toMap(),config.signatureConfig(),InputTransform.create(image.width,image.height,config.inputWidth,config.inputHeight,ModelContract.resize(config),config.cropFraction))
                    assertTrue(decoded.all { it.score.isFinite() });result["proposals"]=decoded.size
                    phase("save_checkpoint")
                    checkpoint=session.save(File(root,"checkpoint-${System.nanoTime()}"))
                    result["checkpoint_files"]=checkpoint!!.files.size
                }
                phase("restore_checkpoint")
                LiteRtTrainingSession(model,config).use { fresh ->
                    fresh.restore(requireNotNull(checkpoint))
                    val restored=fresh.infer(image)
                    assertEquals(after.size,restored.size)
                    after.indices.forEach { i ->
                        assertEquals(after[i].shape,restored[i].shape)
                        after[i].values.indices.forEach { j ->
                            assertEquals(after[i].values[j],restored[i].values[j],1e-5f*(1+abs(after[i].values[j])))
                        }
                    }
                    result["restore_max_difference"]=maxDifference(after,restored)
                    phase("resume_training")
                    result["resumed_loss"]=fresh.train(image,target,.001f,auxiliary).also { assertTrue(it.isFinite()) }
                    val resumed=fresh.save(File(root,"resumed-${System.nanoTime()}"))
                    assertNotEquals(checkpoint!!.files,resumed.files)
                    result["optimizer_steps_on_android"]=2
                }
                val receipt=OnDeviceTraining.saveCheckpointReceipt(model,requireNotNull(checkpoint))
                phase("application_inference_from_checkpoint")
                LiteRtEngine().use { engine ->
                    assertTrue(engine.lastError,engine.loadModel(model,config.threads))
                    val restoredProposals=engine.runInference(image,config.copy(trainingCheckpoint=receipt)).orThrow()
                    assertNull(engine.lastError,engine.lastError)
                    assertTrue(restoredProposals.all { it.score.isFinite() })
                    assertEquals((result["proposals"] as Int),restoredProposals.size)
                    result["application_checkpoint_loaded"]=true
                }
                result["training"]="passed"
            }
            assertEquals("Original download must stay unchanged",originalSha,HashUtils.computeSha256(model))
            result["success"]=true;phase("complete")
        } catch(error:Throwable) {
            result["success"]=false;result["error"]=error.javaClass.simpleName+": "+error.message
            phase(result["phase"].toString());throw error
        } finally { image.recycle() }
    }

    private fun maxDifference(a:List<TensorValues>,b:List<TensorValues>):Float {
        assertEquals(a.size,b.size);var delta=0f
        a.indices.forEach { i -> assertEquals(a[i].shape,b[i].shape);assertEquals(a[i].values.size,b[i].values.size)
            a[i].values.indices.forEach { j -> require(a[i].values[j].isFinite() && b[i].values[j].isFinite());delta=max(delta,abs(a[i].values[j]-b[i].values[j])) }
        }
        return delta
    }

    private fun correction(c:ModelConfig,before:List<TensorValues>):SampleAnnotations = when(c.training!!.targetEncoding) {
        "one_hot" -> {
            val scores=before[c.signatureConfig().outputIndex].values
            val index=scores.indices.minBy { scores[it] }
            SampleAnnotations(tags=listOf(TagTarget("qa-tag",c.labels[index],isHumanVerified=true)))
        }
        "boxes_xyxy_class_mask" -> SampleAnnotations(boxes=listOf(BoxTarget("qa-box",.2f,.2f,.8f,.8f,c.labels.first(),isHumanVerified=true)))
        "heatmap_nchw" -> SampleAnnotations(points=c.labels.mapIndexed { index,label -> PointTarget("qa-point-$index",.4f,.6f,label,isHumanVerified=true) })
        "segmentation_point_valid_mask_nchw" -> {
            val mask=BooleanArray(64*48) { i -> i%64 in 13..50 && i/64 in 10..37 }
            val presence=c.training!!.auxiliaryTargets.getValue("presence").labels
            SampleAnnotations(points=listOf(PointTarget("qa-point",.5f,.6f,c.spatialLabel,isHumanVerified=true)),
                masks=listOf(MaskTarget("qa-mask",c.spatialLabel,64,48,MaskCodec.encode(mask),isHumanVerified=true)),
                tags=listOf(TagTarget("qa-presence",presence.first(),isHumanVerified=true)),quality=QualityAuditTarget(verifiedNegativeQueries=presence.drop(1)))
        }
        else -> error("No qualification target for ${c.training!!.targetEncoding}")
    }
}
