package com.unicornwhodev.visiondatasetstudio

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/** Opt-in runtime audit. Uses real public weights, isolated files and an in-memory Room DB.
 * The loopback responder and geometric correction rows are explicit synthetic fixtures,
 * not an agent, a trained vision model or an accuracy benchmark. Never writes to HF.
 */
@RunWith(AndroidJUnit4::class)
class FeatureImplementationAuditTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val root get() = File(target.filesDir, "feature-audit").apply { mkdirs() }
    private val hf = HfApiClient { null }
    @Before fun optIn() {
        Assume.assumeTrue("Explicit download opt-in required", InstrumentationRegistry.getArguments().getString("audit_download_models") == "true")
    }
    private fun report(name: String, values: Map<String, Any?>) {
        File(root, "$name.json").writeText(StudioJson.moshi.adapter(Map::class.java).indent("  ").toJson(values))
    }
    private fun fixture(): Bitmap = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(25, 40, 65))
        for (y in 20..95) for (x in 30..110) setPixel(x, y, Color.rgb(225, 165, 75))
    }
    private fun isolated(name: String): Context = object : ContextWrapper(target) {
        private val dir = File(root, name).apply { mkdirs() }
        override fun getFilesDir() = File(dir,"files").apply { mkdirs() }
        override fun getCacheDir() = File(dir,"cache").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(dir,"db/$name").apply { parentFile!!.mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences("feature_audit_${name}", mode)
    }
    private suspend fun model(id: String): Pair<File, ModelConfig> {
        val entry = PublicModelCatalog.entries.single { it.id == id }
        val f = File(root, "$id.tflite")
        val started = System.nanoTime()
        check(hf.downloadImage(entry.url, f, maxBytes=entry.maxBytes)) { "Catalogue download failed: ${entry.url}" }
        val inspected = PublicModelCatalog.inspect(entry, f)
        report("model-$id", mapOf("url" to entry.url, "bytes" to f.length(), "sha256" to HashUtils.computeSha256(f),
            "inspection" to inspected.note, "config" to StudioJson.moshi.adapter(ModelConfig::class.java).toJson(inspected.config),
            "download_and_inspection_ms" to (System.nanoTime()-started)/1_000_000))
        return f to inspected.config
    }
    private fun exerciseModel(id: String) = runBlocking {
        val (file, config) = model(id)
        val bitmap = fixture()
        LiteRtEngine().use { engine ->
            assertTrue(engine.lastError, engine.loadModel(file))
            val result = engine.dryRun(bitmap, config)
            report("inference-$id", mapOf("success" to result.success, "error" to result.error, "backend" to result.backend,
                "latency_ms" to result.latencyMs, "proposal_count" to result.proposals.size, "tensor_report" to engine.tensorReport(),
                "fixture" to "synthetic coloured rectangle; no accuracy claim"))
            assertTrue(result.error, result.success)
            assertTrue(result.proposals.all { it.source.startsWith("model_litert:") })
        }
        bitmap.recycle()
    }
    @Test fun publicSsdDownloadInspectAndInfer() = exerciseModel("ssd-mobilenet-v1")
    @Test fun publicEfficientDetDownloadInspectAndInfer() = exerciseModel("efficientdet-lite0")
    @Test fun publicClassifierDownloadInspectAndInfer() = exerciseModel("mobilenet-v1-classification")

    @Test fun studioPackRetainsPromptAndRejectsUnknownFields() {
        val config=ModelConfig(task="captioning",runtime="local_http",endpoint="http://127.0.0.1:8080/predict",
            prompt="Décrire seulement les éléments visibles. Ne pas inventer.",httpOutputMode="caption_text")
        val pack=StudioPack(name="Recette des consignes",classes=listOf("object"),tasks=listOf("CAPTIONING","CLASSIFICATION"),batchSize=2,model=config)
        pack.validate()
        val adapter=StudioJson.moshi.adapter(StudioPack::class.java).failOnUnknown()
        val json=adapter.toJson(pack)
        val decoded=adapter.fromJson(json)!!
        decoded.validate()
        assertEquals(pack,decoded)
        var rejected=false
        try { adapter.fromJson(json.dropLast(1)+",\"unsupported_step\":true}") }
        catch (_: com.squareup.moshi.JsonDataException) { rejected=true }
        assertTrue("Unsupported workflow fields must not disappear silently",rejected)
        report("studio-pack",mapOf("roundtrip" to true,"prompt_preserved" to true,"unknown_field_rejected" to true,
            "scope" to "actual Android Moshi serializer and validation; SAF/project creation UI not exercised"))
    }

    @Test fun pointCorrectionStoreLearnsPersistsAndResets() = runBlocking {
        val context=isolated("corrections")
        val project=ProjectEntity(id=91903L,name="Correction fixture",activeTasksCsv="POINTING",classesCsv="object")
        val source="model_litert:synthetic-weight-hash:synthetic-contract-hash"
        val rows=(0 until 160).map { index ->
            val x=.15f+(index%13)*.05f;val y=.2f+(index%9)*.06f
            val imageHash=AdaptiveCorrection.hash("correction-fixture-image-$index")
            val sample=SampleEntity(sampleId="correction-$index",projectId=project.id,batchNumber=1,assetId="correction-$index",
                sourceRowIndex=index.toLong(),sourceFileUrl=null,localImagePath=null,imageWidth=160,imageHeight=120,
                sha256=imageHash,acquisitionStatus="AVAILABLE",annotationStatus="VALIDATED",syncStatus="NOT_EXPORTED")
            val point=PointTarget(id="point-$index",x=x+.02f+.04f*(x-.5f),y=y-.015f,label="object",isHumanVerified=true,
                modelScore=.9f,sourceProvenance=source,modelX=x,modelY=y,modelLabel="object",explicitlyAdjusted=true)
            sample to SampleAnnotations(points=listOf(point))
        }
        val store=AdaptiveCorrectionStore(context)
        store.reset(project.id)
        val acceptedOnly=rows.map { (s,a) -> s to a.copy(points=a.points.map { it.copy(explicitlyAdjusted=false) }) }
        store.train(project,acceptedOnly)
        assertTrue(store.read(project.id).groups.isEmpty())
        var unfinishedRejected=false
        try { store.train(project,listOf(rows.first().let { it.first.copy(annotationStatus="IN_PROGRESS") to it.second })) }
        catch (_: IllegalStateException) { unfinishedRejected=true }
        assertTrue(unfinishedRejected)
        val message=store.train(project,rows)
        val reloaded=AdaptiveCorrectionStore(context).read(project.id)
        val group=reloaded.groups.single()
        assertEquals(160,group.examples.size)
        assertNotNull(message,group.head)
        val raw=ModelProposal("point","object",.9f,pointX=.4f,pointY=.6f,source=source)
        val adjusted=AdaptiveCorrection.apply(listOf(raw),reloaded).single()
        assertTrue(adjusted.pointX>raw.pointX)
        assertEquals(raw.pointX,adjusted.modelX!!,0f)
        store.reset(project.id)
        assertTrue(store.read(project.id).groups.isEmpty())
        report("point-corrections",mapOf("examples" to 160,"promoted" to true,"persisted_and_reloaded" to true,
            "accepted_only_excluded" to true,"unfinished_batch_rejected" to true,"reset" to true,"training_report" to message,
            "scope" to "synthetic numeric residuals, not visual model training or generalization"))
    }

    @Test fun realBatchInferencePreservesHumanAndSkipsFinalCases() = runBlocking {
        val (file, inspected) = model("mobilenet-v1-classification")
        val config=inspected.copy(threshold=0f,topK=3) // Exercise proposals on an OOD fixture; not a precision threshold.
        val context=isolated("batch");val storage=StorageManager(context)
        val db=Room.inMemoryDatabaseBuilder(target,AppDatabase::class.java).build()
        val engine=LiteRtEngine();val exporters=DatasetExporters(storage,hf)
        val batch=BatchEngine(db,storage,hf,engine,exporters)
        try {
            val project=ProjectEntity(id=91901L,name="Audit only",activeTasksCsv="CLASSIFICATION",classesCsv=config.labels.joinToString(","))
            db.projectDao().saveProject(project)
            db.batchDao().insertOrReplace(BatchEntity(projectId=project.id,batchNumber=1,status="READY",totalCases=3))
            val image=storage.getImageFile("audit-image","png")
            fixture().let { b -> image.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle() }
            fun sample(id:String,status:String,sync:String="NOT_EXPORTED")=SampleEntity(sampleId=id,projectId=project.id,batchNumber=1,
                assetId=id,sourceRowIndex=0,sourceFileUrl=null,localImagePath=image.path,imageWidth=160,imageHeight=120,
                sha256=HashUtils.computeSha256(image),acquisitionStatus="AVAILABLE",annotationStatus=status,syncStatus=sync)
            val pending=sample("pending","IN_PROGRESS")
            db.sampleDao().insertSamples(listOf(pending,sample("validated","VALIDATED"),sample("verified","PENDING","VERIFIED")))
            val human=TagTarget("human-1","human-reviewed",isHumanVerified=true)
            batch.saveSampleAnnotations("pending",SampleAnnotations(tags=listOf(human)))
            assertTrue(engine.lastError,engine.loadModel(file))
            assertEquals(1,batch.runBatchInference(project.id,1,config,replaceExistingProposals=true))
            val annotations=batch.getSampleAnnotations("pending")
            assertTrue(annotations.tags.contains(human))
            assertEquals(3,annotations.tags.count { !it.isHumanVerified && it.sourceProvenance.startsWith("model_litert:") })
            assertEquals("PROPOSALS_AVAILABLE",db.sampleDao().getSampleSync("pending")!!.annotationStatus)
            assertEquals("VALIDATED",db.sampleDao().getSampleSync("validated")!!.annotationStatus)
            assertNull(db.annotationDao().getAnnotationSync("validated"))
            assertNull(db.annotationDao().getAnnotationSync("verified"))
            report("batch",mapOf("processed" to 1,"machine_tags" to 3,"human_tag_preserved" to true,"final_cases_skipped" to true))
        } finally { engine.close();db.close() }
    }

    @Test fun promptIsTransportedToLocalFixtureAndResponseDecoded() = runBlocking {
        val server=ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));server.soTimeout=20000
        val executor=Executors.newSingleThreadExecutor()
        val request=executor.submit<String> {
            server.accept().use { socket ->
                socket.soTimeout=20000
                val reader=socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val headers=mutableListOf<String>();while(true){val line=reader.readLine() ?: error("EOF");if(line.isEmpty())break;headers.add(line)}
                val length=headers.first { it.startsWith("Content-Length:",true) }.substringAfter(':').trim().toInt()
                val chars=CharArray(length);var offset=0;while(offset<length){val n=reader.read(chars,offset,length-offset);check(n>0);offset+=n}
                val response="{\"choices\":[{\"message\":{\"content\":\"Fixture locale de recette\"}}]}".toByteArray()
                socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray());write(response);flush() }
                String(chars)
            }
        }
        val bitmap=fixture()
        try {
            val prompt="Audit instruction: describe only visible content."
            val config=ModelConfig(task="captioning",runtime="local_http",endpoint="http://127.0.0.1:${server.localPort}/predict",
                prompt=prompt,httpModel="test-responder-not-a-model",httpOutputMode="caption_text",responsePath="choices.0.message.content",
                requestTemplate="""{"messages":[{"role":"system","content":"${'$'}prompt"}],"image":"${'$'}image_data_url","model":"${'$'}model"}""")
            val result=LocalModelClient().run(bitmap,config)
            val raw=request.get(20,TimeUnit.SECONDS)
            val received=StudioJson.moshi.adapter(Any::class.java).fromJson(raw) as Map<*,*>
            val messages=received["messages"] as List<*>
            assertEquals(prompt,(messages.single() as Map<*,*>)["content"])
            assertTrue((received["image"] as String).startsWith("data:image/jpeg;base64,"))
            assertEquals("Fixture locale de recette",result.single().text)
            assertTrue(result.single().source.startsWith("model_local_http:"))
            report("local-http",mapOf("prompt_delivered" to true,"jpeg_delivered" to true,"response_path_decoded" to true,"server" to "synthetic HTTP responder, not an LLM or agent"))
        } finally { bitmap.recycle();server.close();executor.shutdownNow() }
    }

    @Test fun localExportContainsCanonicalAndRequestedProjections() = runBlocking {
        val context=isolated("export");val storage=StorageManager(context);val exporters=DatasetExporters(storage,hf)
        val image=storage.getImageFile("audit-export","png")
        fixture().let { b -> image.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle() }
        val project=ProjectEntity(id=91902L,name="Audit export",classesCsv="object",activeTasksCsv="DETECTION,CAPTIONING,VQA")
        val sample=SampleEntity(sampleId="audit-export",projectId=project.id,batchNumber=1,assetId="audit-export",sourceRowIndex=0,
            sourceFileUrl=null,localImagePath=image.path,imageWidth=160,imageHeight=120,sha256=HashUtils.computeSha256(image),
            acquisitionStatus="AVAILABLE",annotationStatus="VALIDATED",syncStatus="NOT_EXPORTED")
        val annotations=SampleAnnotations(boxes=listOf(BoxTarget("box-1",.1f,.2f,.7f,.8f,"object",true)),
            captions=listOf(CaptionTarget("caption-1","Image synthetique",isHumanVerified=true)),
            vqaList=listOf(VqaTarget("qa-1","Type d'image ?","Synthetique",isHumanVerified=true)))
        val result=exporters.packageBatchToLocalZip(project,1,listOf(sample to annotations))
        assertTrue(result.error,result.success)
        val zip=result.zipFile!!
        val names=ZipFile(zip).use { z -> z.entries().asSequence().map { it.name }.toList() }
        assertTrue(names.any { it.endsWith("annotations.jsonl") })
        assertTrue(names.any { it.endsWith("coco.json") })
        assertTrue(names.any { it.endsWith("dataset.yaml") })
        assertTrue(names.any { it.endsWith("vqa.jsonl") })
        assertTrue(names.any { it.endsWith(".tar") })
        assertEquals(sample.sha256,HashUtils.computeSha256(image))
        assertTrue(exporters.verifyPreparedPackage(storage.batchExportDir(project.id,1)))
        report("export",mapOf("zip" to zip.path,"sha256" to HashUtils.computeSha256(zip),"entries" to names,"original_unchanged" to true))
    }
}
