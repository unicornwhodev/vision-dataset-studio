package com.unicornwhodev.visiondatasetstudio

import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import org.junit.Assert.*
import org.junit.Test

class FunctionalAuditTest {
    @Test fun rtmdetDecodesReorderedPyramidOutputsAndAppliesThreshold() {
        val config=ModelConfig(adapter="rtmdet",inputWidth=32,inputHeight=32,labels=listOf("object"),threshold=.5f)
        fun map(side:Int)=TensorValues(listOf(1,side,side,5),FloatArray(side*side*5){if(it%5==0)-20f else 1f})
        val fine=map(4).also { it.values[(1*4+1)*5]=4f }
        val outputs=mapOf(0 to map(1),1 to fine,2 to map(2))
        val transform=InputTransform.create(32,32,32,32,"letterbox")
        val result=ModelAdapters.decode(outputs,config,transform)
        assertEquals(1,result.size)
        assertEquals(0f,result.single().xmin,1e-6f)
        assertEquals(.5f,result.single().xmax,1e-6f)
        assertTrue(ModelAdapters.decode(outputs,config.copy(threshold=.999f),transform).isEmpty())
    }

    @Test fun rtmdetRejectsMissingOrAmbiguousFeatureMaps() {
        val config=ModelConfig(adapter="rtmdet",inputWidth=32,inputHeight=32,labels=listOf("object"))
        val tensor=TensorValues(listOf(1,4,4,5),FloatArray(80))
        val transform=InputTransform.create(32,32,32,32,"letterbox")
        for(outputs in listOf(mapOf(0 to tensor),mapOf(0 to tensor,1 to tensor))) {
            try { ModelAdapters.decode(outputs,config,transform);fail("Invalid feature pyramid accepted") }
            catch(_:IllegalStateException) { }
        }
    }

    @Test fun clipPreservesCustomPromptsInsteadOfReplacingThemWithTheDefault() {
        assertEquals("a photo of cat",ModelPrompts.clipCandidate("","cat"))
        assertEquals("a sketch of cat",ModelPrompts.clipCandidate("a sketch of {label}","cat"))
        assertEquals("a sketch of cat",ModelPrompts.clipCandidate("a sketch of","cat"))
        assertEquals("cat beside cat",ModelPrompts.clipCandidate("{label} beside {label}","cat"))
    }
    @Test fun unsupportedTextPromptsAreRejectedInsteadOfSilentlyIgnored() {
        assertTrue(runCatching { ModelContract.validate(ModelConfig.defaultDetectionPreset().copy(prompt="find cats")) }.isFailure)
        ModelContract.validate(ModelConfig(task="classification",adapter="tinyclip",bundleKind="tinyclip",labels=listOf("cat"),prompt="a sketch of {label}"))
    }

    @Test fun workflowTitlesAndValidationMessagesFollowLocaleChanges() {
        val previous=java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.FRENCH)
            assertEquals("Légendes",com.unicornwhodev.visiondatasetstudio.core.workflow.StudioTask.CAPTIONING.title)
            java.util.Locale.setDefault(java.util.Locale.ENGLISH)
            assertEquals("Captions",com.unicornwhodev.visiondatasetstudio.core.workflow.StudioTask.CAPTIONING.title)
            assertEquals("Point to a target",com.unicornwhodev.visiondatasetstudio.core.workflow.StudioWorkflow.presets.first().title)
            assertEquals("Prompt too long",runCatching { ModelContract.validate(ModelConfig(prompt="x".repeat(16001))) }.exceptionOrNull()!!.message)
        } finally { java.util.Locale.setDefault(previous) }
    }

    @Test fun dynamicYoloUsesDeclaredLabelsNormalizationAndPixelBoxes() {
        val report=mapOf("input_layout" to "NCHW","dynamic_validated" to true,
            "preprocessing" to "RGB float32 in [0,1]; pad to multiples of 32; no resampling embedded",
            "spatial_bounds" to mapOf("alignment" to 32,"tested_max" to 960),
            "checks" to listOf(mapOf("output_shapes" to listOf(listOf(1,6,5040)))))
        val metadata=mapOf("key" to "fixture_yolo11m","task" to "object detection","route" to "onnx2tf_dynamic","report" to report)
        val labels=mapOf("1" to "flame_visible","0" to "smoke_visible")
        val config=RuntimeModelContracts.dynamicYolo(metadata,labels)
        assertEquals(listOf("smoke_visible","flame_visible"),config.labels)
        assertEquals(255f,config.std);assertEquals("pixels",config.coordinates);assertEquals("NCHW",config.inputLayout)
        val tensor=TensorValues(listOf(1,6,1),floatArrayOf(320f,320f,128f,128f,.1f,.9f))
        val result=ModelAdapters.decode(mapOf(0 to tensor),config,InputTransform.create(640,640,640,640,"letterbox"))
        assertEquals("flame_visible",result.single().label);assertEquals(.4f,result.single().xmin,1e-6f)
        assertTrue(runCatching{RuntimeModelContracts.dynamicYolo(metadata,mapOf("2" to "wrong"))}.isFailure)
        assertTrue(runCatching{RuntimeModelContracts.dynamicYolo(metadata+mapOf("report" to report+mapOf("preprocessing" to "unknown")),labels)}.isFailure)
    }

    @Test fun modelWithoutDigestManifestIsInstallableAtAnyRevision() {
        val source=CommunityModelCatalog.Source(repository="qa/models")
        val tree=listOf(com.unicornwhodev.visiondatasetstudio.data.hf.HfTreeItem("models/custom/model.tflite","file",100))
        listOf("main","a".repeat(40)).forEach { revision ->
            val item=CommunityModelCatalog.fromTree(source,revision,tree).single()
            assertTrue(item.installableNow)
            assertEquals(QualificationStatus.UNTESTED,item.entry.capabilities.qualification)
            assertEquals(listOf("model.tflite"),item.entry.expectedFiles)
        }
    }

    @Test fun catalogQualificationNeverTransfersToAnotherRevisionOrRepository() {
        val tree=listOf("model.tflite","artifact_manifest.json").map {
            com.unicornwhodev.visiondatasetstudio.data.hf.HfTreeItem("models/rtmdet_tiny/$it","file",100)
        }
        val source=CommunityModelCatalog.Source()
        fun status(repo:CommunityModelCatalog.Source,sha:String)=CommunityModelCatalog.fromTree(repo,sha,tree).single().entry.capabilities.qualification
        assertEquals(QualificationStatus.INFERENCE_ONLY,status(source,"1244117f490e36ce321d70baa672753caeaef028"))
        assertEquals(QualificationStatus.INFERENCE_ONLY,status(source,"36026262693de56b2cf45a6337a405297bfcfff6"))
        assertEquals(QualificationStatus.UNTESTED,status(source,"a".repeat(40)))
        assertEquals(QualificationStatus.UNTESTED,status(source.copy(repository="qa/fork"),"1244117f490e36ce321d70baa672753caeaef028"))
    }

}
