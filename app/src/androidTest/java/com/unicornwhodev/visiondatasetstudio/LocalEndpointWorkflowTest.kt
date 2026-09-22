package com.unicornwhodev.visiondatasetstudio

import android.graphics.Bitmap
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.domain.inference.*
import com.unicornwhodev.visiondatasetstudio.domain.workflow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real Android loopback requests to a bounded protocol fixture, not an AI quality evaluation. */
class LocalEndpointWorkflowTest {
    @Test fun modelPromptThresholdAndAgentInstructionsReachTheLocalServer()=runBlocking {
        val received=java.util.Collections.synchronizedList(mutableListOf<Map<*,*>>())
        val server=ServerSocket(0,4,InetAddress.getByName("127.0.0.1")).apply{soTimeout=30_000}
        val executor=Executors.newSingleThreadExecutor()
        val worker=executor.submit {
            repeat(3) { index -> server.accept().use { socket ->
                socket.soTimeout=30_000
                val input=BufferedInputStream(socket.getInputStream())
                fun line():String {
                    val bytes=java.io.ByteArrayOutputStream()
                    while(true){val b=input.read();check(b>=0);if(b==10)break;check(bytes.size()<8192);if(b!=13)bytes.write(b)}
                    return bytes.toString("UTF-8")
                }
                check(line().startsWith("POST /"))
                val headers=mutableMapOf<String,String>()
                while(true){val h=line();if(h.isEmpty())break;headers[h.substringBefore(':').lowercase()]=h.substringAfter(':').trim()}
                val length=headers.getValue("content-length").toInt();check(length in 1..1_000_000)
                val body=ByteArray(length);var offset=0
                while(offset<length){val n=input.read(body,offset,length-offset);check(n>0);offset+=n}
                received+=StudioJson.moshi.adapter(Any::class.java).fromJson(body.toString(Charsets.UTF_8)) as Map<*,*>
                assertFalse("Credentials must not be sent to the local agent",headers.containsKey("authorization"))
                val response=if(index<2) """{"predictions":[{"type":"tag","label":"cat","score":0.9},{"type":"tag","label":"dog","score":0.2}]}""" else """{"template":"manual","reason":"Human requested manual review"}"""
                val payload=response.toByteArray()
                socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray());write(payload);flush() }
            } }
        }
        val bitmap=Bitmap.createBitmap(16,12,Bitmap.Config.ARGB_8888)
        try {
            val prompt="Describe \"cats\"\nwithout guessing"
            val config=ModelConfig(task="classification",adapter="classification",runtime="local_http",labels=listOf("cat","dog"),
                endpoint="http://127.0.0.1:${server.localPort}/predict",httpModel="qa-local",threshold=.5f,prompt=prompt,
                requestTemplate="""{"instruction":"${'$'}prompt","image":"${'$'}image_base64","model":"${'$'}model"}""")
            val first=LocalModelClient().run(bitmap,config)
            assertEquals(listOf("cat"),first.map{it.label})
            assertTrue(LocalModelClient().run(bitmap,config.copy(threshold=.95f,prompt="changed prompt")).isEmpty())
            val instructions="Keep uncertain images for a human. Do not automatically approve."
            val choice=LocalWorkflowAgent.propose("http://127.0.0.1:${server.localPort}/plan",instructions,mapOf("PENDING" to 3))
            assertEquals("manual",choice.template)
            worker.get(30,TimeUnit.SECONDS)
            assertEquals(prompt,received[0]["instruction"])
            assertEquals("changed prompt",received[1]["instruction"])
            assertEquals("qa-local",received[0]["model"])
            assertTrue((received[0]["image"] as String).isNotBlank())
            assertEquals(instructions,received[2]["instructions"])
            assertEquals(WorkflowTools.systemPrompt,received[2]["system"])
            assertEquals(WorkflowTools.PROMPT_VERSION,received[2]["prompt_version"])
            assertEquals(3.0,(received[2]["batch_counts"] as Map<*,*>)["PENDING"])
            assertFalse(received[2].containsKey("image"))
            assertEquals(3,(received[2]["templates"] as List<*>).size)
        } finally { bitmap.recycle();server.close();executor.shutdownNow() }
    }
}
