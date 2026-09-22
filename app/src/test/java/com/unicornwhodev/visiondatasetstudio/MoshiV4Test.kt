package com.unicornwhodev.visiondatasetstudio
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.data.hf.*
import com.unicornwhodev.visiondatasetstudio.domain.inference.ModelConfig
import org.junit.Assert.*
import org.junit.Test

/** Exercises generated adapters and the fallback with REAL Moshi, not the CLI annotation marker. */
class MoshiV4Test {
    @Test fun annotationsRoundTrip() {
        val data=SampleAnnotations(points=listOf(PointTarget("p",0.2f,0.8f,"object",isHumanVerified=true,explicitlyAdjusted=true,modelX=0.1f,modelY=0.7f)),
            boxes=listOf(BoxTarget("b",0.1f,0.2f,0.7f,0.9f,"object")),captions=listOf(CaptionTarget("c","Forêt et fumée","fr")),
            vqaList=listOf(VqaTarget("q","Où ?","À gauche",targetIds=listOf("p"))),counts=listOf(CountingTarget("n","object",1,linkedInstanceIds=listOf("b"))))
        val adapter=StudioJson.moshi.adapter(SampleAnnotations::class.java)
        assertEquals(data,adapter.fromJson(adapter.toJson(data)))
    }
    @Test fun historicalDefaultsRemainUnverified() {
        val a=StudioJson.moshi.adapter(SampleAnnotations::class.java).fromJson("{\"points\":[{\"id\":\"p\",\"x\":0.1,\"y\":0.2,\"label\":\"object\"}]}")!!
        assertFalse(a.points.single().isHumanVerified);assertFalse(a.points.single().explicitlyAdjusted)
    }
    @Test fun pointingMultiKeepsFourPerTargetStatesAndLegacyBooleans() {
        val points=PointLocalizationState.entries.mapIndexed{i,state->PointTarget("p$i",.1f*i,.2f,"object",localizationState=state,isAbsent=state==PointLocalizationState.ABSENT,isAbstained=state in setOf(PointLocalizationState.UNLOCALIZABLE,PointLocalizationState.UNCERTAIN))}
        val adapter=StudioJson.moshi.adapter(SampleAnnotations::class.java)
        val restored=adapter.fromJson(adapter.toJson(SampleAnnotations(points=points)))!!
        assertEquals(PointLocalizationState.entries,restored.points.map{it.effectiveLocalizationState})
        val legacy=adapter.fromJson("{\"points\":[{\"id\":\"a\",\"x\":0.0,\"y\":0.0,\"label\":\"x\",\"isAbsent\":true},{\"id\":\"u\",\"x\":0.0,\"y\":0.0,\"label\":\"x\",\"isAbstained\":true}]}")!!
        assertEquals(listOf(PointLocalizationState.ABSENT,PointLocalizationState.UNLOCALIZABLE),legacy.points.map{it.effectiveLocalizationState})
    }
    @Test fun canonicalSampleKeepsFourPointStatesThroughExportJson() {
        val states=PointLocalizationState.entries
        val annotations=SampleAnnotations(points=states.mapIndexed{i,state->PointTarget("p$i",.1f*i,.2f,"object").withLocalizationState(state)})
        val sample=CanonicalDatasetSample("sample","asset","local","revision","","train",0,null,"train",CanonicalMediaInfo("image.jpg",10,10,"image/jpeg","sha",null,null),annotations,"VALIDATED",CanonicalAuditInfo(1,2,"human",false))
        val adapter=StudioJson.moshi.adapter(CanonicalDatasetSample::class.java)
        val exported=createTempFile(suffix=".jsonl")
        exported.writeText(adapter.toJson(sample)+"\n")
        val restored=adapter.fromJson(exported.useLines{it.single()})!!
        exported.delete()
        assertEquals(states,restored.annotations.points.map{it.effectiveLocalizationState})
    }
    @Test fun contractRejectsUnknownKeys() {
        assertThrows(com.squareup.moshi.JsonDataException::class.java) { StudioJson.moshi.adapter(ModelConfig::class.java).failOnUnknown().fromJson("{\"unimplemented_runtime_switch\":true}") }
    }
    @Test fun receiptKeepsLongSizes() {
        val r=RemoteReceipt(files=listOf(RemoteFileDigest("batches/a/data.tar",8_000_000_001L,"a".repeat(64))))
        val a=StudioJson.moshi.adapter(RemoteReceipt::class.java)
        assertEquals(r,a.fromJson(a.toJson(r)))
    }
    @Test fun reflectionFallbackForEntitiesKeepsDefaults() {
        val adapter=StudioJson.moshi.adapter(ProjectEntity::class.java)
        val p=ProjectEntity(id=7,name="Atelier",settingsJson="{}")
        assertEquals(p,adapter.fromJson(adapter.toJson(p)))
    }
    @Test fun nullForNonNullFieldIsRejected() {
        assertThrows(com.squareup.moshi.JsonDataException::class.java) { StudioJson.moshi.adapter(PointTarget::class.java).fromJson("{\"id\":null,\"x\":0.0,\"y\":0.0,\"label\":\"x\"}") }
    }
}
