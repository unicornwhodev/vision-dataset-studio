package com.unicornwhodev.visiondatasetstudio.domain.inference

import android.graphics.Bitmap
import android.util.Base64
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Optional client, not an embedded VLM. No HF token, no redirects, loopback only. */
class LocalModelClient {
    private val moshi=com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi
    private val any=moshi.adapter(Any::class.java)
    suspend fun run(bitmap:Bitmap,c:ModelConfig):List<ModelProposal> = withContext(Dispatchers.IO) {
        LocalCallContract.validate(c)
        val contractHash=java.security.MessageDigest.getInstance("SHA-256").digest(c.toString().toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}.take(16)
        val provenance="model_local_http:${c.httpModel}:$contractHash"
        val bytes=ByteArrayOutputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.JPEG,90,out));out.toByteArray() }
        check(bytes.size<=12*1024*1024) { "Image encodée trop grande pour l’appel local" }
        val image=Base64.encodeToString(bytes,Base64.NO_WRAP)
        val vars=mapOf("\$image_base64" to image,"\$image_data_url" to "data:image/jpeg;base64,$image", "\$model" to c.httpModel,"\$prompt" to c.prompt,"\$width" to bitmap.width,"\$height" to bitmap.height,"\$mime" to "image/jpeg")
        val template=if(c.requestTemplate.isBlank()) mapOf("image_base64" to "\$image_base64","model" to "\$model","prompt" to "\$prompt") else any.fromJson(c.requestTemplate) ?: error("Requête JSON vide")
        val body=any.toJson(LocalCallContract.replace(template,vars)).toRequestBody("application/json".toMediaType())
        val client=OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = okhttp3.Dns.SYSTEM.lookup(hostname).also { addresses ->
                require(addresses.isNotEmpty() && addresses.all { it.isLoopbackAddress }) { "Adresse locale non loopback" }
            }
        }).connectTimeout(10,TimeUnit.SECONDS).readTimeout(c.httpTimeoutSeconds.toLong(),TimeUnit.SECONDS).callTimeout(c.httpTimeoutSeconds.toLong(),TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
        client.newCall(Request.Builder().url(c.endpoint).post(body).build()).execute().use { r ->
            check(r.isSuccessful) { "Serveur local HTTP ${r.code}; aucune proposition créée" }
            val raw=r.body ?: error("Réponse locale vide")
            val data=raw.byteStream().use { stream -> val out=ByteArrayOutputStream();val b=ByteArray(8192);while(true){val n=stream.read(b);if(n<0)break;check(out.size()+n<=4*1024*1024){"Réponse locale trop volumineuse"};out.write(b,0,n)};out.toString("UTF-8") }
            val selected=LocalCallContract.select(any.fromJson(data),c.responsePath) ?: error("Chemin de réponse introuvable : ${c.responsePath}")
            if(c.httpOutputMode=="caption_text") {
                require(selected is String && selected.isNotBlank() && selected.length<=32_000)
                listOf(ModelProposal("caption","",1f,text=selected,source=provenance))
            } else {
                val target=if(selected is String) any.fromJson(selected) else selected
                val adapter=moshi.adapter<List<ModelProposal>>(Types.newParameterizedType(List::class.java,ModelProposal::class.java)).failOnUnknown()
                val proposals=adapter.fromJsonValue(target) ?: error("Tableau de propositions attendu")
                GroundingProposalContract.validateAndFilter(proposals,ModelContract.outputTypes(c),c.threshold)
                    .map{it.copy(source=provenance)}
            }
        }
    }
}
