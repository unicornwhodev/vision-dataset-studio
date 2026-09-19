package com.unicornwhodev.visiondatasetstudio
import com.unicornwhodev.visiondatasetstudio.data.hf.*
import com.unicornwhodev.visiondatasetstudio.core.storage.DownloadCheckpoint
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Loopback fault injection into the production OkHttp client. Does NOT contact real HF. */
class HfTransferV4Test {
    @get:Rule val temp=TemporaryFolder()
    private fun exercise(block:suspend (MockWebServer,HfApiClient)->Unit) = runBlocking {
        val server=MockWebServer();server.start()
        try {
            val client=OkHttpClient.Builder().readTimeout(3,TimeUnit.SECONDS).retryOnConnectionFailure(false).addInterceptor{ chain ->
                val request=chain.request();val target=server.url(request.url.encodedPath).newBuilder().encodedQuery(request.url.encodedQuery).build()
                chain.proceed(request.newBuilder().url(target).build())
            }.build()
            block(server,HfApiClient(client){"test-only-placeholder"})
        } finally {server.shutdown()}
    }
    @Test fun disconnectedDownloadResumesHashedPrefix()=exercise { server,api ->
        val bytes=ByteArray(256*1024){(it%251).toByte()};val file=File(temp.root,"image.bin")
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)).addHeader("ETag","\"v1\"").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        assertFalse(api.downloadImage("https://storage.googleapis.com/fixture",file,maxBytes=bytes.size.toLong()))
        assertFalse(file.exists());val part=File(file.path+".part");val offset=part.length();assertTrue(offset in 1 until bytes.size.toLong())
        assertNull(server.takeRequest().getHeader("Authorization"))
        server.enqueue(MockResponse().setResponseCode(206).addHeader("ETag","\"v1\"").addHeader("Content-Range","bytes $offset-${bytes.lastIndex}/${bytes.size}").setBody(Buffer().write(bytes,offset.toInt(),bytes.size-offset.toInt())))
        assertTrue(api.downloadImage("https://storage.googleapis.com/fixture",file,maxBytes=bytes.size.toLong()))
        assertArrayEquals(bytes,file.readBytes());assertEquals("bytes=$offset-",server.takeRequest().getHeader("Range"))
        assertFalse(part.exists());assertFalse(File(file.path+".range").exists())
    }
    @Test fun fullResponseRestartsInsteadOfAppending()=exercise { server,api ->
        val file=File(temp.root,"full.bin");val part=File(file.path+".part").apply{writeText("old")}
        DownloadCheckpoint.save(part,File(file.path+".range"),com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles.hash("https://storage.googleapis.com/fixture".byteInputStream()),"\"old\"",8)
        server.enqueue(MockResponse().setBody("new-data").addHeader("ETag","\"new\""))
        assertTrue(api.downloadImage("https://storage.googleapis.com/fixture",file));assertEquals("new-data",file.readText())
    }
    @Test fun wrongRangeNeverOverwritesFinalFile()=exercise { server,api ->
        val file=File(temp.root,"range.bin").apply{writeText("protected")};val part=File(file.path+".part").apply{writeText("old")}
        DownloadCheckpoint.save(part,File(file.path+".range"),com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles.hash("https://storage.googleapis.com/fixture".byteInputStream()),"\"old\"",8)
        server.enqueue(MockResponse().setResponseCode(206).addHeader("ETag","\"old\"").addHeader("Content-Range","bytes 2-7/8").setBody("wrong!"))
        assertFalse(api.downloadImage("https://storage.googleapis.com/fixture",file));assertEquals("protected",file.readText())
    }
    @Test fun overSizeResponseRejected()=exercise {server,api ->
        val file=File(temp.root,"too-large");server.enqueue(MockResponse().setBody("too much"))
        assertFalse(api.downloadImage("https://storage.googleapis.com/fixture",file,maxBytes=2));assertFalse(file.exists())
    }
    @Test fun commitConflictPreservesPinnedParent()=exercise {server,api ->
        val f=File(temp.root,"data.bin").apply{writeText("content")};val parent="a".repeat(40)
        server.enqueue(MockResponse().setBody("{\"files\":[{\"path\":\"batches/data.bin\",\"uploadMode\":\"regular\"}]}"))
        server.enqueue(MockResponse().setResponseCode(409).setBody("conflict"))
        val result=api.uploadBatchFiles("user/test","main","test",listOf("batches/data.bin" to f),parent)
        assertFalse(result.success);assertTrue(result.conflict);assertTrue(f.isFile)
        assertTrue(server.takeRequest().path!!.contains("/preupload/"));val commit=server.takeRequest()
        assertTrue(commit.body.readUtf8().contains("\"parentCommit\":\"$parent\""));assertEquals(2,server.requestCount)
    }
    @Test fun lostCommitReplyIsNotAutomaticallyReplayed()=exercise {server,api ->
        val f=File(temp.root,"data.bin").apply{writeText("content")}
        server.enqueue(MockResponse().setBody("{\"files\":[{\"path\":\"data.bin\",\"uploadMode\":\"regular\"}]}"))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val result=api.uploadBatchFiles("user/test","main","test",listOf("data.bin" to f),"a".repeat(40))
        assertFalse(result.success);assertEquals(2,server.requestCount);assertEquals("content",f.readText())
    }
    @Test fun remoteReceiptVerifiesWithoutLocalPayload()=exercise {server,api ->
        val f=File(temp.root,"data").apply{writeText("payload")};val hash=HashUtils.computeSha256(f);f.delete()
        val sha="b".repeat(40);server.enqueue(MockResponse().setBody("{\"sha\":\"$sha\"}"));server.enqueue(MockResponse().setBody("payload"))
        assertTrue(api.verifyRemoteDigests("user/test",sha,listOf(RemoteFileDigest("data.bin",7,hash))))
    }
    @Test fun occupiedPathIsNeverOverwritten()=exercise {server,api ->
        server.enqueue(MockResponse().setResponseCode(200))
        var refused=false;try{api.requirePathsAbsent("user/test","a".repeat(40),listOf("data.bin"))}catch(_:IllegalStateException){refused=true}
        assertTrue(refused)
    }
}
