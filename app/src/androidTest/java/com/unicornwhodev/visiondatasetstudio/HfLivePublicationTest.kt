package com.unicornwhodev.visiondatasetstudio

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.domain.batch.*
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import com.unicornwhodev.visiondatasetstudio.domain.inference.LiteRtEngine
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicitly authorized NEW private QA repository only. Never uses the user's app credential or corpus. */
class HfLivePublicationTest {
    @Test fun privateQaPublicationConcurrentReservationsAndReadback()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue("Separate authorization for a new private QA repository is required",args.getString("hfLiveQa")=="authorized")
        val repo=requireNotNull(args.getString("hfQaRepository"))
        require(repo.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]*/vision-dataset-studio-qa-[0-9]{8}[a-z0-9-]*")))
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val credential=File(target.filesDir,"qa-private/hf-token")
        val token=credential.readText().trim();credential.delete()
        require(token.isNotEmpty())
        val hf=HfApiClient{token}
        val identity=hf.verifyToken();assertTrue(identity.isValid)
        val owner=repo.substringBefore('/')
        assertTrue("The authorized repository must belong to this account or one of its organizations",
            owner==identity.username || owner in identity.orgs)
        if(args.getString("hfReuseQa")!="true") {
            assertFalse("Never use a pre-existing repository without explicit QA reuse",hf.checkDatasetAccess(repo).exists)
            assertTrue(hf.createDatasetRepo(repo,true))
        }
        assertTrue(hf.checkDatasetAccess(repo).isPrivate)
        val case=requireNotNull(args.getString("faultCase")).also{require(it.matches(Regex("[a-f0-9]{12}")))}
        val root=File(target.filesDir,"qa-evidence/hf-live/$case");check(!root.exists());root.mkdirs()
        val context=object:ContextWrapper(target) {
            override fun getFilesDir()=File(root,"app-files").apply{mkdirs()}
            override fun getCacheDir()=File(root,"app-cache").apply{mkdirs()}
            override fun getSharedPreferences(name:String,mode:Int)=target.getSharedPreferences("hf_live_qa_$name",mode)
        }
        val db=Room.inMemoryDatabaseBuilder(target,AppDatabase::class.java).build()
        val storage=StorageManager(context);val inference=LiteRtEngine()
        try {
            val base=ProjectEntity(id=910001,name="Synthetic publication QA",hfSourceRepo="qa/synthetic-source-$case",hfDestRepo=repo,classesCsv="rectangle",activeTasksCsv="CLASSIFICATION",diskBudgetMb=1024)
            val a=ProcessingSettings(collaborationEnabled=true,collaborationWorkerId="qa-worker-a",hfWebDataset=false,hfCoco=false,hfYolo=false,hfVl=false)
            val b=a.copy(collaborationWorkerId="qa-worker-b")
            val entry=SourceEntryEntity(base.id,0,"synthetic-image","https://example.invalid/never-fetched.png")
            val coordinator=WorkClaimCoordinator(context,hf)
            val claims=awaitAll(async{a to coordinator.claim(base,a,listOf(entry),1)},async{b to coordinator.claim(base,b,listOf(entry),1)})
            assertEquals("Exactly one worker must reserve the image",1,claims.count{it.second.entries.size==1})
            val policy=claims.single{it.second.entries.isNotEmpty()}.first
            val project=base.copy(settingsJson=ProjectSettings.write(policy))
            db.projectDao().saveProject(project)
            val image=storage.getImageFile("synthetic-image","png")
            Bitmap.createBitmap(17,13,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.CYAN)}.let{bitmap->image.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
            val sample=SampleEntity("synthetic-image",base.id,1,"synthetic-image",0,sourceFileUrl=null,localImagePath=image.path,imageWidth=17,imageHeight=13,sha256=HashUtils.computeSha256(image),acquisitionStatus="AVAILABLE",annotationStatus="VALIDATED",syncStatus="NOT_EXPORTED")
            db.batchDao().insertOrReplace(BatchEntity(base.id,1,"IN_PROGRESS",totalCases=1))
            db.sampleDao().insertSamples(listOf(sample));ImageIdentity.accept(db,sample,ImageIdentity.pixelSha256(image))
            val engine=BatchEngine(db,storage,hf,inference,DatasetExporters(storage,hf))
            engine.saveSampleAnnotations(sample.sampleId,SampleAnnotations(tags=listOf(TagTarget("reviewed-tag","rectangle",isHumanVerified=true))))
            val published=engine.publishAndVerifyBatch(base.id,1)
            assertTrue(published.toString(),published.success)
            val batch=db.batchDao().getBatchSync(base.id,1)!!;assertEquals("VERIFIED",batch.status);assertNotNull(batch.hfCommitSha)
            val sha=hf.resolveRevision(repo)
            assertTrue(engine.publishAndVerifyBatch(base.id,1).success)
            assertEquals("Resuming a verified publication must not create a second commit",sha,hf.resolveRevision(repo))
            assertTrue(coordinator.claim(base,a,listOf(entry),1).entries.isEmpty())
            assertTrue(coordinator.claim(base,b,listOf(entry),1).entries.isEmpty())
            assertEquals(1,engine.purgeReviewedBatch(base.id,1,true));assertFalse(image.exists())
            assertEquals(sample.sampleId,db.imageIdentityDao().owner(base.id,"file_sha256",sample.sha256!!)!!.firstSampleId)
            File(root,"result.json").writeText(StudioJson.moshi.adapter(Any::class.java).indent("  ").toJson(mapOf(
                "private_qa_repository" to true,"synthetic_images" to 1,"concurrent_workers" to 2,"reservation_winners" to 1,
                "android_publication_and_readback" to true,"repeat_did_not_republish" to true,"cleanup_after_remote_verification" to true,"dedup_history_retained" to true)))
        } finally {inference.close();db.close()}
    }
}
