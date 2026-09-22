package com.unicornwhodev.visiondatasetstudio

import com.unicornwhodev.visiondatasetstudio.domain.inference.*
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
    @Test fun inferenceReceiptIsDurableAndDistinguishesEmpty() {
        val root=createTempDir();val diagnostics=InferenceDiagnostics("sha","embedding","embedding",listOf(1,3,224,224),threshold=.5f,proposalCount=0,emptyReason="encoder only")
        val store=InferenceReceiptStore(root);val file=store.write(9,"sample",2,InferenceResult.Empty("encoder only",diagnostics))
        assertTrue(file.isFile);assertEquals("empty",store.list(9).single().outcome)
        root.deleteRecursively()
    }

    private fun assertFails(block:()->Unit) { try { block();fail("failure expected") } catch(_:IllegalArgumentException) {} catch(_:IllegalStateException) {} }
}
