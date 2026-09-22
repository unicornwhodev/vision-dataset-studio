package com.unicornwhodev.visiondatasetstudio

import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.training.OnDeviceTraining
import com.unicornwhodev.visiondatasetstudio.ui.NavigationHistory
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.core.workflow.StudioTask
import com.unicornwhodev.visiondatasetstudio.domain.validation.AnnotationReview

class StabilizationTest {
    @Test fun absentOrStaleModelManifestDoesNotBlockRuntimeFiles() {
        val dir=createTempDir()
        try {
            File(dir,"model.tflite").writeText("new weights")
            CommunityModelInstaller.verifyRuntimeFiles(dir,setOf("model.tflite"))
            File(dir,"artifact_manifest.json").writeText("""{"model.tflite":{"sha256":"obsolete","bytes":1}}""")
            CommunityModelInstaller.verifyRuntimeFiles(dir,setOf("model.tflite"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun requiredRuntimeFilesMustExistAndRemainWithinInstallation() {
        val dir=createTempDir()
        try {
            assertFails { CommunityModelInstaller.verifyRuntimeFiles(dir,setOf("missing.tflite")) }
            File(dir,"empty.tflite").writeText("")
            assertFails { CommunityModelInstaller.verifyRuntimeFiles(dir,setOf("empty.tflite")) }
            assertFails { CommunityModelInstaller.verifyRuntimeFiles(dir,setOf("../outside.tflite")) }
        } finally { dir.deleteRecursively() }
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

    @Test fun modelContractSupportsHumanOnlyAndPartialProjects() {
        val point=ModelConfig(task="pointing",adapter="points",outputMode="points",labels=listOf("target"))
        val box=ModelConfig.defaultDetectionPreset(listOf("target"))
        val multitask=ModelConfig(task="multitask",adapter="fireviewer_dinov3_multitask",labels=listOf("target"))
        assertTrue(ModelContract.supportsTasks(point,"POINTING"))
        assertTrue(ModelContract.supportsTasks(point,"POINTING_MULTI"))
        assertFalse(ModelContract.supportsTasks(box,"POINTING_MULTI"))
        assertTrue(ModelContract.supportsTasks(multitask,"POINTING_MULTI"))
        assertTrue(ModelContract.supportsTasks(box,"DETECTION,CAPTIONING"))
        assertTrue(ModelContract.compatibility(ModelConfig.defaultClassifierPreset(listOf("a")),"CLASSIFICATION,NEGATIVE").fullyCovered)
        assertFalse(ModelContract.compatibility(ModelConfig.defaultClassifierPreset(listOf("a")),"NEGATIVE").canRun)
        assertTrue(ModelContract.compatibility(box,"DETECTION,NEGATIVE").fullyCovered)
        assertTrue(ModelContract.compatibility(point,"POINTING,NEGATIVE").fullyCovered)
        val partialClassification=ModelContract.compatibility(ModelConfig.defaultClassifierPreset(listOf("a")),"CLASSIFICATION,CAPTIONING")
        assertTrue(partialClassification.canRun);assertFalse(partialClassification.fullyCovered)
        assertTrue(ModelContract.compatibility(ModelConfig(runtime="local_http",task="multitask",labels=listOf("a")),"CLASSIFICATION,CAPTIONING").fullyCovered)
        assertFalse(ModelContract.compatibility(box,"DETECTION,SEGMENTATION").fullyCovered)
        assertFalse(ModelContract.compatibility(point,"POINTING,DETECTION").fullyCovered)
        assertFalse(ModelContract.supportsTasks(ModelConfig(adapter="embedding"),"DETECTION"))
        assertFalse(ModelContract.supportsTasks(ModelConfig(adapter="inspect_only"),"DETECTION"))
        assertFalse(ModelContract.supportsTasks(ModelConfig(runtime="local_http",task="multitask",labels=listOf("target")),"GROUNDING"))
    }

    @Test fun actionsAndExtendedCapabilitiesFollowTheContract() {
        val classifier=ModelConfig.defaultClassifierPreset(listOf("a"))
        assertEquals(ModelAction.PREANNOTATE_PARTIALLY,ModelCapabilities.action(classifier,"CLASSIFICATION,CAPTIONING"))
        assertEquals(ModelAction.COMPUTE_REPRESENTATION,ModelCapabilities.action(ModelConfig(adapter="embedding"),"DETECTION"))
        assertEquals(ModelAction.INSPECT,ModelCapabilities.action(ModelConfig(adapter="inspect_only"),"DETECTION"))
        assertTrue(ModelCapability.VQA in ModelCapabilities.fromConfig(ModelConfig(runtime="local_http",task="vqa")).values)
        assertTrue(ModelCapability.COUNTING in ModelCapabilities.fromConfig(ModelConfig(runtime="local_http",task="counting")).values)
        val grounding=ModelPresets.create("http_grounding",listOf("object"))
        assertTrue(ModelCapability.GROUNDING in ModelCapabilities.fromConfig(grounding).values)
        assertEquals(setOf("grounding","box","point"),ModelContract.compatibility(grounding,"GROUNDING").usableOutputs)
        assertFalse(ModelCapability.GROUNDING in ModelCapabilities.fromConfig(ModelConfig(runtime="local_http",task="grounding")).values)
        assertFails { ModelContract.validate(ModelConfig(runtime="local_http",task="grounding")) }
        assertFalse(ModelCapability.GROUNDING in ModelCapabilities.fromConfig(ModelConfig(bundleKind="florence2")).values)
    }

    @Test fun explicitGroundingLinksResolveToCanonicalTargets() {
        val proposals=listOf(
            ModelProposal("box","object",.9f,.1f,.2f,.7f,.8f,proposalId="region-1"),
            ModelProposal("grounding","",.8f,text="the object",proposalId="phrase-1",linkedProposalIds=listOf("region-1")))
        val merged=ProposalMerger.merge(SampleAnnotations(),proposals,"GROUNDING",replaceTypes=setOf("box","grounding"))
        assertEquals(1,merged.boxes.size);assertEquals(listOf(merged.boxes.single().id),merged.groundings.single().boxIds)
        assertEquals("the object",merged.groundings.single().phrase);assertFalse(merged.groundings.single().isHumanVerified)
        assertFails{ProposalMerger.merge(SampleAnnotations(),proposals.map{if(it.type=="grounding")it.copy(linkedProposalIds=listOf("missing"))else it},"GROUNDING",replaceTypes=setOf("box","grounding"))}
    }

    @Test fun groundingWireContractIsExplicitReferentialAndThresholdSafe() {
        val region=ModelProposal("point","object",.9f,pointX=.4f,pointY=.6f,proposalId="region")
        val phrase=ModelProposal("grounding","",.8f,text="the object",linkedProposalIds=listOf("region"))
        val accepted=GroundingProposalContract.validateAndFilter(listOf(region,phrase),setOf("point","grounding"),.5f)
        assertEquals(2,accepted.size)
        assertFails { GroundingProposalContract.validateAndFilter(listOf(region.copy(proposalId=""),phrase),setOf("point","grounding"),.5f) }
        assertFails { GroundingProposalContract.validateAndFilter(listOf(region.copy(score=.2f),phrase),setOf("point","grounding"),.5f) }
        assertFails { GroundingProposalContract.validateAndFilter(listOf(region,phrase.copy(linkedProposalIds=listOf("missing"))),setOf("point","grounding"),.5f) }
        assertFails { GroundingProposalContract.validateAndFilter(listOf(region,region.copy(type="grounding",text="x",linkedProposalIds=listOf("region"))),setOf("point"),.5f) }
    }

    @Test fun groundingProposalRequiresExplicitHumanReview() {
        val a=SampleAnnotations(
            boxes=listOf(BoxTarget("box",.1f,.1f,.5f,.5f,"object",isHumanVerified=true)),
            groundings=listOf(GroundingTarget("g","the object",boxIds=listOf("box"),sourceProvenance="model_local_http:test")))
        assertTrue(AnnotationReview.problems(a,setOf(StudioTask.GROUNDING)).isNotEmpty())
        assertFalse(AnnotationReview.problems(a.copy(groundings=a.groundings.map{it.copy(isHumanVerified=true)}),setOf(StudioTask.GROUNDING)).isNotEmpty())
    }

    @Test fun instanceLinksAreExplicitValidatedAndReversible() {
        val source=SampleAnnotations(
            boxes=listOf(BoxTarget("b",.1f,.1f,.5f,.5f,"object")),
            masks=listOf(MaskTarget("m","object",2,2,listOf(0,1,3))))
        assertEquals(listOf("m"),InstanceLinks.compatibleTargets(source,"b"))
        val linked=InstanceLinks.link(source,setOf("b","m"),"instance-1")
        assertEquals("instance-1",linked.boxes.single().instanceId)
        assertEquals("instance-1",linked.masks.single().instanceId)
        assertTrue(InstanceLinks.problems(linked).isEmpty())
        val unlinked=InstanceLinks.unlink(linked,"m")
        assertNull(unlinked.masks.single().instanceId);assertEquals("instance-1",unlinked.boxes.single().instanceId)
        assertFails{InstanceLinks.link(source.copy(masks=listOf(source.masks.single().copy(label="other"))),setOf("b","m"),"instance-2")}
        assertTrue(InstanceLinks.problems(linked.copy(masks=linked.masks+linked.masks.single().copy(id="m2"))).isNotEmpty())
    }

    @Test fun maskTopologyDoesNotDuplicateAnInstance() {
        val base=MaskTarget("m","object",3,1,MaskCodec.encode(booleanArrayOf(true,false,true)),instanceId="instance")
        val pieces=MaskCodec.split(base){"p$it"}
        assertEquals(2,pieces.size);assertEquals(1,pieces.count{it.instanceId=="instance"})
        val merged=MaskCodec.merge(pieces,"merged","object")
        assertNull(merged.instanceId)
        val same=listOf(base,base.copy(id="m2"))
        assertEquals("instance",MaskCodec.merge(same,"merged-2","object").instanceId)
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
