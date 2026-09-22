package com.unicornwhodev.visiondatasetstudio

import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.batch.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Deterministic competing-worker transport fixture; no request reaches the Hub. */
class WorkClaimConcurrencyTest {
    @get:Rule val temp=TemporaryFolder()
    private val project=ProjectEntity(id=19,name="QA claims",hfSourceRepo="qa/source",hfDestRepo="qa/destination")
    private val settings=ProcessingSettings(collaborationEnabled=true,collaborationWorkerId="worker-a")
    private val candidate=SourceEntryEntity(19,0,"image-a","https://example.invalid/image.png")
    private val before="a".repeat(40);private val after="b".repeat(40)
    private val json=StudioJson.moshi.adapter(Any::class.java)
    private fun claim(path:String,owner:String="worker-b",state:String="CLAIMED")=StudioJson.moshi.adapter(RemoteWorkClaim::class.java).toJson(
        RemoteWorkClaim(state=state,owner=owner,sourceKey=path.substringAfter("/claims/").substringBefore('/'),assetId="image-a",sourceOrdinal=0,claimedAt=1,expiresAt=Long.MAX_VALUE))
    private fun api(handler:(Request)->Pair<Int,String>)=HfApiClient(OkHttpClient.Builder().addInterceptor{ chain ->
        val (status,body)=handler(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("QA fixture").body(body.toResponseBody()).build()
    }.build()){null}

    @Test fun concurrentReservationCannotBeOverwrittenWithANewerParent()=runBlocking {
        var head=before;val parents=mutableListOf<String>();val reads=mutableListOf<String>()
        val client=api { request ->
            val path=request.url.encodedPath
            when {
                "/revision/" in path -> 200 to "{\"sha\":\"$head\"}"
                "/resolve/" in path -> {
                    reads+=path.substringAfter("/resolve/").substringBefore('/')
                    // Worker B commits immediately after A starts reading its snapshot.
                    val existed=head==after || "/$after/" in path
                    head=after
                    if(existed)200 to claim(path) else 404 to "missing"
                }
                "/preupload/" in path -> {
                    val data=json.fromJson(Buffer().also{request.body!!.writeTo(it)}.readUtf8()) as Map<*,*>
                    val files=(data["files"] as List<*>).map{ mapOf("path" to (it as Map<*,*>)["path"],"uploadMode" to "regular") }
                    200 to json.toJson(mapOf("files" to files))
                }
                "/commit/" in path -> {
                    val header=json.fromJson(Buffer().also{request.body!!.writeTo(it)}.readUtf8().lineSequence().first()) as Map<*,*>
                    val parent=(header["value"] as Map<*,*>)["parentCommit"] as String;parents+=parent
                    if(parent!=head)409 to "competing commit" else 200 to "{\"commitOid\":\"$after\"}"
                }
                else -> error("Unexpected fixture route")
            }
        }
        val result=WorkClaimCoordinator(temp.root,client).claim(project,settings,listOf(candidate),1)
        assertTrue(result.entries.isEmpty());assertEquals(1,result.skipped)
        assertEquals(listOf(before),parents);assertEquals(listOf(before,after),reads)
    }

    @Test fun corruptClaimIsNeverTreatedAsUnreserved()=runBlocking {
        var writes=0
        val client=api{request->when {
            "/revision/" in request.url.encodedPath->200 to "{\"sha\":\"$before\"}"
            "/resolve/" in request.url.encodedPath->200 to "{\"state\":\"CLAIMED\"}"
            else->{writes++;200 to "{}"}
        }}
        assertTrue(runCatching{WorkClaimCoordinator(temp.root,client).claim(project,settings,listOf(candidate),1)}.isFailure)
        assertEquals(0,writes)
    }

    @Test fun completionCannotStealAnotherWorkersLeaseAndDoneIsIdempotent()=runBlocking {
        var writes=0;var state="CLAIMED"
        val client=api{request->when {
            "/revision/" in request.url.encodedPath->200 to "{\"sha\":\"$before\"}"
            "/resolve/" in request.url.encodedPath->200 to claim(request.url.encodedPath,state=state)
            else->{writes++;200 to "{}"}
        }}
        val sample=SampleEntity("image-a",19,1,"image-a",0,sourceFileUrl=null,localImagePath=null,acquisitionStatus="AVAILABLE",annotationStatus="VALIDATED",syncStatus="VERIFIED")
        val coordinator=WorkClaimCoordinator(temp.root,client)
        assertTrue(runCatching{coordinator.markDone(project,settings,listOf(sample))}.isFailure)
        state="DONE";coordinator.markDone(project,settings,listOf(sample))
        assertEquals(0,writes)
    }
}
