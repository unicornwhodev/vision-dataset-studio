package com.unicornwhodev.visiondatasetstudio

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.domain.batch.BatchEngine
import com.unicornwhodev.visiondatasetstudio.domain.batch.ImageIdentity
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtEngine
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.Random

/** Real private Hub commits. The host kills the app after the server accepts a commit,
 * before its response reaches BatchEngine. No mock server or user corpus is involved. */
class HfFaultPublicationTest {
    private val target get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val args get()=InstrumentationRegistry.getArguments()
    private val case get()=requireNotNull(args.getString("faultCase")).also { require(it.matches(Regex("[a-f0-9]{12}"))) }
    private val repo get()=requireNotNull(args.getString("hfQaRepository")).also {
        require(args.getString("hfLiveQa")=="authorized")
        require(it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]*/vision-dataset-studio-qa-[0-9]{8}[a-z0-9-]*")))
    }
    private val root get()=File(target.filesDir,"qa-evidence/hf-faults/$case")
    private val context get()=object:ContextWrapper(target) {
        override fun getFilesDir()=File(root,"studio/files").apply{mkdirs()}
        override fun getCacheDir()=File(root,"studio/cache").apply{mkdirs()}
    }
    private fun open()=Room.databaseBuilder(target,AppDatabase::class.java,"qa-hf-faults-$case.db").build()
    private fun token():String {
        val file=File(target.filesDir,"qa-private/hf-token")
        return file.readText().trim().also { check(file.delete());require(it.isNotBlank()) }
    }
    private fun proof(name:String, data:JSONObject)=File(root,"$name.json").writeText(data.toString(2))
    private suspend fun verifyOriginals(db:AppDatabase) {
        val expected=JSONArray(File(root,"expected.json").readText())
        for(index in 0 until expected.length()) {
            val item=expected.getJSONObject(index);val id=item.getString("sample_id")
            assertEquals(item.getString("annotation"),db.annotationDao().getAnnotationSync(id)!!.dataJson)
            assertEquals(item.getString("sha256"),HashUtils.computeSha256(File(item.getString("image"))))
        }
    }

    @Test fun prepareAndPublishAwaitingRealProcessKill()=runBlocking {
        check(!root.exists());root.mkdirs()
        val credential=token()
        val client=OkHttpClient.Builder().addNetworkInterceptor { chain ->
            val response=chain.proceed(chain.request())
            if(chain.request().method=="POST" && chain.request().url.encodedPath=="/api/datasets/$repo/commit/main" && response.isSuccessful) {
                proof("server-accepted",JSONObject().put("http_status",response.code).put("response_not_delivered_to_engine",true))
                // A bounded gate allows the host to force-stop the actual process at this exact boundary.
                Thread.sleep(180000)
                response.close()
                throw IOException("Host did not stop the process at the accepted-commit boundary")
            }
            response
        }.build()
        val hf=HfApiClient(client){credential}
        val access=hf.checkDatasetAccess(repo);assertTrue(access.exists);assertTrue(access.isPrivate)
        val db=open();val storage=StorageManager(context);val runtime=LiteRtEngine()
        try {
            val policy=ProcessingSettings(hfWebDataset=false,hfCoco=false,hfYolo=false,hfVl=false,timeoutSeconds=300)
            val project=ProjectEntity(id=720001,name="Synthetic lost response QA",hfDestRepo=repo,classesCsv="synthetic",
                activeTasksCsv="CLASSIFICATION",settingsJson=ProjectSettings.write(policy))
            db.projectDao().saveProject(project)
            db.batchDao().insertOrReplace(BatchEntity(project.id,1,"IN_PROGRESS",totalCases=2))
            val engine=BatchEngine(db,storage,hf,runtime,DatasetExporters(storage,hf));val expected=JSONArray()
            repeat(2) { index ->
                val id="hf-fault-$case-$index";val image=storage.getImageFile(id,"png")
                val random=Random(index.toLong())
                val pixels=IntArray(512*512){0xff000000.toInt() or random.nextInt(0x1000000)}
                Bitmap.createBitmap(pixels,512,512,Bitmap.Config.ARGB_8888).let { bitmap ->
                    image.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) };bitmap.recycle()
                }
                val sample=SampleEntity(id,project.id,1,id,index.toLong(),sourceFileUrl=null,
                    localImagePath=image.path,imageWidth=512,imageHeight=512,sha256=HashUtils.computeSha256(image),
                    acquisitionStatus="AVAILABLE",annotationStatus="IN_PROGRESS",syncStatus="NOT_EXPORTED")
                db.sampleDao().insertSamples(listOf(sample))
                assertNull(ImageIdentity.accept(db,sample,ImageIdentity.pixelSha256(image)))
                engine.saveSampleAnnotations(id,SampleAnnotations(tags=listOf(TagTarget("human-$index","synthetic",isHumanVerified=true))))
                engine.validateSample(id,1)
                expected.put(JSONObject().put("sample_id",id).put("image",image.path).put("sha256",HashUtils.computeSha256(image))
                    .put("annotation",db.annotationDao().getAnnotationSync(id)!!.dataJson))
            }
            File(root,"expected.json").writeText(expected.toString(2))
            val result=engine.publishAndVerifyBatch(project.id,1)
            assertTrue(result.toString(),result.success)
            fail("This phase must be interrupted by a real process kill after server acceptance")
        } finally {runtime.close();db.close()}
    }

    @Test fun reconcileLostResponseWithoutDuplicateCommit()=runBlocking {
        check(File(root,"server-accepted.json").isFile)
        val credential=token();val hf=HfApiClient{credential};val db=open();val runtime=LiteRtEngine()
        try {
            val before=db.batchDao().getBatchSync(720001,1)!!
            assertEquals("PUBLISHING",before.status);assertNull(before.hfCommitSha)
            verifyOriginals(db)
            val head=hf.resolveRevision(repo)
            val storage=StorageManager(context)
            val result=BatchEngine(db,storage,hf,runtime,DatasetExporters(storage,hf)).publishAndVerifyBatch(720001,1)
            assertTrue(result.toString(),result.success)
            assertEquals(head,hf.resolveRevision(repo))
            assertEquals(head,db.batchDao().getBatchSync(720001,1)!!.hfCommitSha)
            assertEquals("VERIFIED",db.batchDao().getBatchSync(720001,1)!!.status)
            verifyOriginals(db)
            proof("reconciled",JSONObject().put("no_duplicate_commit",true).put("human_annotations_preserved",true)
                .put("images_preserved",true).put("remote_readback_verified",true).put("commit",head))
        } finally {runtime.close();db.close()}
    }

    @Test fun staleParentIsRejectedByRealHub()=runBlocking {
        check(File(root,"reconciled.json").isFile)
        val credential=token();val hf=HfApiClient{credential}
        val parent=hf.resolveRevision(repo)
        val first=File(root,"conflict-first.json").apply{writeText("{\"synthetic\":true,\"worker\":1}")}
        val second=File(root,"conflict-second.json").apply{writeText("{\"synthetic\":true,\"worker\":2}")}
        val prefix="qa-conflict/$case"
        hf.requirePathsAbsent(repo,parent,listOf("$prefix/first.json","$prefix/second.json"))
        val accepted=hf.uploadBatchFiles(repo,commitMessage="Synthetic QA conflict baseline",files=listOf("$prefix/first.json" to first),expectedParentCommit=parent)
        assertTrue(accepted.toString(),accepted.success)
        val conflict=hf.uploadBatchFiles(repo,commitMessage="Synthetic QA stale parent",files=listOf("$prefix/second.json" to second),expectedParentCommit=parent)
        assertFalse(conflict.success);assertTrue(conflict.toString(),conflict.conflict)
        assertEquals(accepted.commitSha,hf.resolveRevision(repo))
        proof("conflict",JSONObject().put("stale_parent_rejected",true).put("no_silent_rebase",true).put("local_candidate_retained",second.isFile))
    }
}
