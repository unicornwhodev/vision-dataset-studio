package com.unicornwhodev.visiondatasetstudio

import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining
import com.unicornwhodev.visiondatasetstudio.ui.NavigationHistory
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.unicornwhodev.visiondatasetstudio.data.model.MaskTarget

class StabilizationTest {
    @Test fun documentationNeverControlsRuntimeIntegrity() {
        val dir=createTempDir();val readme=File(dir,"README.md").apply{writeText("changed remotely")}
        val weight=File(dir,"model.tflite").apply{writeText("weights")}
        val hashes=mapOf("README.md" to "new-doc-sha", "model.tflite" to "weight-sha")
        val manifest=mapOf<String,Any>(
            "README.md" to mapOf("sha256" to "old-doc-sha","bytes" to 1),
            "model.tflite" to mapOf("sha256" to "weight-sha","bytes" to weight.length()))
        CommunityModelInstaller.verifyManifest(dir,hashes,manifest,setOf("model.tflite"))
        readme.delete();dir.deleteRecursively()
    }

    @Test fun protectedRuntimeArtifactsAreStrict() {
        val dir=createTempDir();File(dir,"model.tflite").writeText("weights")
        val manifest=mapOf<String,Any>("model.tflite" to mapOf("sha256" to "expected","bytes" to 7))
        assertFails { CommunityModelInstaller.verifyManifest(dir,mapOf("model.tflite" to "changed"),manifest,setOf("model.tflite")) }
        assertFails { CommunityModelInstaller.verifyManifest(dir,emptyMap(),manifest,setOf("model.tflite")) }
        assertFails { CommunityModelInstaller.verifyManifest(dir,emptyMap(),emptyMap<String,Any>(),setOf("runtime_contract.json")) }
        val runtime=File(dir,"model_config.json").apply{writeText("{}")}
        assertFails { CommunityModelInstaller.verifyManifest(dir,mapOf("model_config.json" to "sha"),emptyMap<String,Any>(),emptySet()) }
        val tokenizer=File(dir,"tokenizer.json").apply{writeText("{}")}
        assertFails { CommunityModelInstaller.verifyManifest(dir,mapOf("tokenizer.json" to "sha"),emptyMap<String,Any>(),emptySet()) }
        runtime.delete();tokenizer.delete()
        dir.deleteRecursively()
    }

    @Test fun annotationCompatibilityIsExplicit() {
        val embedding=ModelCapabilities(setOf(ModelCapability.EMBEDDING),QualificationStatus.UNTESTED)
        assertFalse(embedding.producesAnnotations);assertFalse(embedding.supports("DETECTION"))
        assertFalse(ModelCapabilities(setOf(ModelCapability.INSPECTION_ONLY),QualificationStatus.UNTESTED).supports("DETECTION"))
        assertTrue(ModelCapabilities(setOf(ModelCapability.DETECTION),QualificationStatus.QUALIFIED).supports("DETECTION"))
        assertTrue(ModelCapabilities(setOf(ModelCapability.POINTING),QualificationStatus.QUALIFIED).supports("POINTING"))
        assertFalse(ModelCapabilities(setOf(ModelCapability.POINTING),QualificationStatus.QUALIFIED).supports("DETECTION"))
    }

    @Test fun capabilitiesComeFromContractWithoutInventingQualification() {
        val trainable=ModelConfig(task="classification",adapter="classification",labels=listOf("a"),training=TrainingContract(
            inferOutputs=listOf("scores"),targetShape=listOf(1,1)))
        val capabilities=ModelCapabilities.fromConfig(trainable)
        assertTrue(ModelCapability.CLASSIFICATION in capabilities.values)
        assertTrue(ModelCapability.TRAINING in capabilities.values)
        assertEquals(QualificationStatus.UNTESTED,capabilities.qualification)
        assertEquals(QualificationStatus.INFERENCE_ONLY,ModelCapabilities.fromConfig(trainable,QualificationStatus.INFERENCE_ONLY).qualification)
    }

    @Test fun modelContractRequiresEveryActiveTaskAndSupportsMultiplePoints() {
        val point=ModelConfig(task="pointing",adapter="points",outputMode="points",labels=listOf("target"))
        val box=ModelConfig.defaultDetectionPreset(listOf("target"))
        val multitask=ModelConfig(task="multitask",adapter="fireviewer_dinov3_multitask",labels=listOf("target"))
        assertTrue(ModelContract.supportsTasks(point,"POINTING"))
        assertTrue(ModelContract.supportsTasks(point,"POINTING_MULTI"))
        assertFalse(ModelContract.supportsTasks(box,"POINTING_MULTI"))
        assertTrue(ModelContract.supportsTasks(multitask,"POINTING_MULTI"))
        assertFalse(ModelContract.supportsTasks(box,"DETECTION,CAPTIONING"))
        assertFalse(ModelContract.supportsTasks(ModelConfig(adapter="embedding"),"DETECTION"))
        assertFalse(ModelContract.supportsTasks(ModelConfig(adapter="inspect_only"),"DETECTION"))
        assertFalse(ModelContract.supportsTasks(ModelConfig(runtime="local_http",task="multitask",labels=listOf("target")),"GROUNDING"))
    }

    @Test fun trainingStorageEstimateIncludesPrimaryAndAuxiliaryTargets() {
        val contract=TrainingContract(targetShape=listOf(1,10),auxiliaryTargets=mapOf("presence" to AuxiliaryTarget(listOf(1,3))))
        val bytes=OnDeviceTraining.estimateRequiredBytes(100,listOf(20,30),ModelConfig(training=contract))
        assertEquals(3*100+50+2*(40+12)+16L*1024*1024,bytes)
    }

    @Test fun everyMaskToolStartsFromTheSameAspectPreservingRaster() {
        listOf(1920 to 1080,1080 to 1920,900 to 900).forEach { (width,height) ->
            val brush=MaskCodec.empty("brush","object",width,height)
            val polygon=MaskCodec.polygon(MaskCodec.empty("polygon","object",width,height),listOf(.1f to .1f,.9f to .1f,.5f to .9f))
            val lasso=MaskCodec.polygon(MaskCodec.empty("lasso","object",width,height),listOf(.2f to .2f,.8f to .2f,.5f to .8f))
            val fill=MaskCodec.fill(MaskCodec.empty("fill","object",width,height),.5f,.5f)
            assertEquals(brush.width to brush.height,polygon.width to polygon.height)
            assertEquals(brush.width to brush.height,lasso.width to lasso.height)
            assertEquals(brush.width to brush.height,fill.width to fill.height)
            assertEquals(512,maxOf(brush.width,brush.height))
        }
        assertEquals(512 to 288,MaskCodec.rasterSizeForImage(1920,1080))
        assertEquals(288 to 512,MaskCodec.rasterSizeForImage(1080,1920))
        assertEquals(512 to 512,MaskCodec.rasterSizeForImage(900,900))
    }

    @Test fun navigationUsesHistoryWithoutDuplicates() {
        val nav=NavigationHistory("Home");nav.navigate("Models");nav.navigate("Training")
        assertEquals("Models",nav.back());assertEquals("Home",nav.back())
        nav.navigate("Controls");nav.navigate("Setup");nav.navigate("Setup")
        assertEquals("Controls",nav.back())
    }

    @Test fun brushSizeChangesPaintedAreaAndEraserRemovesPixels() {
        val empty=MaskTarget("m","object",100,100,listOf(10_000),isHumanVerified=true)
        val small=MaskCodec.stroke(empty,.5f,.5f,.5f,.5f,.01f,false)
        val large=MaskCodec.stroke(empty,.5f,.5f,.5f,.5f,.08f,false)
        assertTrue(MaskCodec.decode(large).count{it}>MaskCodec.decode(small).count{it})
        val erased=MaskCodec.stroke(large,.5f,.5f,.5f,.5f,.08f,true)
        assertEquals(0,MaskCodec.decode(erased).count{it})
    }
    @Test fun advancedMaskOperationsAreComplete() {
        val empty=MaskTarget("m","object",20,20,listOf(400),true)
        val polygon=MaskCodec.polygon(empty,listOf(.1f to .1f,.4f to .1f,.4f to .4f,.1f to .4f))
        val second=MaskCodec.polygon(empty,listOf(.6f to .6f,.9f to .6f,.9f to .9f,.6f to .9f))
        val merged=MaskCodec.merge(listOf(polygon,second),"merged","object")
        assertEquals(2,MaskCodec.split(merged){"part-$it"}.size)
        assertTrue(MaskCodec.decode(MaskCodec.fill(empty,.5f,.5f)).all{it})
    }
    @Test fun cocoMaskProjectionIsColumnMajorAndMatchesAreaAndBox() {
        val mask=MaskTarget("m","object",2,2,MaskCodec.encode(booleanArrayOf(true,false,false,false)),true)
        val projection=MaskCodec.projectCoco(mask,4,4)
        assertEquals(listOf(4,4),projection.segmentation["size"])
        assertEquals(listOf(0,0,2,2),projection.bbox)
        assertEquals(4,projection.area)
        val runs=(projection.segmentation["counts"] as List<*>).map{(it as Number).toInt()}
        val decoded=BooleanArray(16);var cursor=0
        runs.forEachIndexed{i,n->if(i%2==1)decoded.fill(true,cursor,cursor+n);cursor+=n}
        assertArrayEquals(booleanArrayOf(true,true,false,false,true,true,false,false,false,false,false,false,false,false,false,false),decoded)
    }
    @Test fun inferenceReceiptIsDurableAndDistinguishesEmpty() {
        val root=createTempDir();val diagnostics=InferenceDiagnostics("sha","embedding","embedding",listOf(1,3,224,224),threshold=.5f,proposalCount=0,emptyReason="encoder only")
        val store=InferenceReceiptStore(root);val file=store.write(9,"sample",2,InferenceResult.Empty("encoder only",diagnostics))
        assertTrue(file.isFile);assertEquals("empty",store.list(9).single().outcome)
        root.deleteRecursively()
    }

    private fun assertFails(block:()->Unit) { try { block();fail("failure expected") } catch(_:IllegalArgumentException) {} catch(_:IllegalStateException) {} }
}
