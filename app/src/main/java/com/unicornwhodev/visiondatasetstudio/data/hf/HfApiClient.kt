package com.unicornwhodev.visiondatasetstudio.data.hf

import com.squareup.moshi.JsonClass
import com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles
import com.unicornwhodev.visiondatasetstudio.core.workflow.RangeSafety
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.util.Properties
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import com.unicornwhodev.visiondatasetstudio.core.workflow.StudioWorkflow
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class HfApiClient(
    private val tokenProvider: () -> String?
) {
    private var client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    /** Test-only transport injection; production endpoints/credential policy remain unchanged. */
    internal constructor(client: OkHttpClient, tokenProvider: () -> String?) : this(tokenProvider) {
        this.client = client.newBuilder().retryOnConnectionFailure(false).build()
    }

    fun configureTimeout(seconds:Int) {
        require(seconds in 10..300)
        client=client.newBuilder().readTimeout(seconds.toLong(),TimeUnit.SECONDS).writeTimeout(seconds.toLong(),TimeUnit.SECONDS).callTimeout(seconds.toLong(),TimeUnit.SECONDS).build()
    }
    suspend fun resolveRevision(repoId:String,revision:String="main"):String = withContext(Dispatchers.IO) {
        val repo=StudioWorkflow.normalizeRepo(repoId) ?: error("Dépôt invalide")
        require(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.validRevision(revision))
        val url="https://huggingface.co/api/datasets/$repo/revision/".toHttpUrlOrNull()!!.newBuilder().addPathSegment(revision).build().toString()
        client.newCall(newRequestBuilder(url).build()).execute().use { r->
            check(r.isSuccessful){"Branche/révision inaccessible (HTTP ${r.code})"}
            val sha=parseObject(r.body?.let(::boundedJson) ?: error("Réponse vide"))["sha"] as? String ?: error("SHA source absent")
            require(sha.matches(Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")));sha
        }
    }
    fun resolveUrl(repoId:String,revision:String,path:String):String {
        val repo=StudioWorkflow.normalizeRepo(repoId) ?: error("Dépôt invalide")
        require(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.safeRelativePath(path))
        return "https://huggingface.co/datasets/$repo/resolve/".toHttpUrlOrNull()!!.newBuilder().addPathSegment(revision).addPathSegments(path).build().toString()
    }

    val moshi: Moshi = com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi

    private fun isHfDomain(host: String): Boolean = host.lowercase() in setOf(
        "huggingface.co", "hf.co", "datasets-server.huggingface.co")

    private fun newRequestBuilder(url: String): Request.Builder {
        val parsed = url.toHttpUrlOrNull()
        require(parsed != null && parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty()) { "HTTPS sans identifiants dans l’URL requis" }
        val builder = Request.Builder().url(url)
        val token = tokenProvider()
        val parsedUri = url.toHttpUrlOrNull()
        // Security requirement #13: Only attach Bearer token to Hugging Face hostnames!
        if (parsedUri != null && isHfDomain(parsedUri.host) && !token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        builder.header("User-Agent", "VisionDatasetStudio-Android/4.2")
        return builder
    }

    suspend fun verifyToken(): HfWhoAmIResult = withContext(Dispatchers.IO) {
        val request = newRequestBuilder("https://huggingface.co/api/whoami-v2").get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    return@withContext HfWhoAmIResult(isValid = false, error = "Token invalide ou expiré (401)")
                }
                if (!response.isSuccessful) {
                    return@withContext HfWhoAmIResult(isValid = false, error = "Erreur HTTP ${response.code}")
                }
                val body = response.body?.let(::boundedJson) ?: ""
                val adapter = moshi.adapter(WhoAmIResponse::class.java)
                val parsed = adapter.fromJson(body)
                HfWhoAmIResult(
                    isValid = true,
                    username = parsed?.name ?: parsed?.username ?: "Utilisateur HF",
                    email = parsed?.email,
                    orgs = parsed?.orgs?.map { it.name } ?: emptyList()
                )
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            HfWhoAmIResult(isValid = false, error = e.localizedMessage ?: "Erreur réseau")
        }
    }

    suspend fun checkDatasetAccess(repoId: String): HfRepoAccessResult = withContext(Dispatchers.IO) {
        val cleanRepo = repoId.trim().removePrefix("https://huggingface.co/datasets/").removePrefix("datasets/")
        val url = "https://huggingface.co/api/datasets/$cleanRepo"
        val request = newRequestBuilder(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.let(::boundedJson) ?: ""
                        val isPrivate = body.contains("\"private\":true")
                        val isGated = body.contains("\"gated\":true") || body.contains("\"gated\":\"auto\"")
                        val shaMatch = Regex("\"sha\":\"([a-f0-9]+)\"").find(body)?.groupValues?.get(1)
                        HfRepoAccessResult(
                            exists = true,
                            isPrivate = isPrivate,
                            isGated = isGated,
                            sha = shaMatch,
                            message = "Accessible (${if (isPrivate) "Privé" else "Public"}${if (isGated) ", Gated" else ""})"
                        )
                    }
                    401 -> HfRepoAccessResult(exists = false, isUnauthorized = true, message = "Accès non autorisé (401) : Token requis ou droits insuffisants")
                    403 -> HfRepoAccessResult(exists = false, isForbidden = true, message = "Accès interdit (403) : Dépôt restreint ou gated non accepté")
                    404 -> HfRepoAccessResult(exists = false, message = "Dépôt introuvable (404)")
                    else -> HfRepoAccessResult(exists = false, message = "Code HTTP ${response.code}")
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            HfRepoAccessResult(exists = false, message = e.localizedMessage ?: "Erreur réseau")
        }
    }

    suspend fun fetchViewerSplits(repoId: String): HfSplitsResult = withContext(Dispatchers.IO) {
        val cleanRepo = repoId.trim().removePrefix("https://huggingface.co/datasets/").removePrefix("datasets/")
        val url = "https://datasets-server.huggingface.co/splits".toHttpUrlOrNull()!!.newBuilder().addQueryParameter("dataset", cleanRepo).build().toString()
        val request = newRequestBuilder(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext HfSplitsResult(
                        success = false,
                        error = "Dataset Viewer indisponible (${response.code}). Ce dataset utilise peut-être des fichiers directs."
                    )
                }
                val body = response.body?.let(::boundedJson) ?: ""
                val adapter = moshi.adapter(ViewerSplitsResponse::class.java)
                val parsed = adapter.fromJson(body)
                val splits = parsed?.splits?.map {
                    DatasetSplitItem(config = it.config, split = it.split, numRows = it.num_rows)
                } ?: emptyList()
                HfSplitsResult(success = true, splits = splits)
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            HfSplitsResult(success = false, error = e.localizedMessage)
        }
    }

    suspend fun fetchViewerRows(
        repoId: String,
        config: String,
        split: String,
        offset: Long,
        length: Int = 100,
        where: String = "",
        orderBy: String = ""
    ): HfRowsResult = withContext(Dispatchers.IO) {
        val cleanRepo = repoId.trim().removePrefix("https://huggingface.co/datasets/").removePrefix("datasets/")
        require(length in 1..100 && offset >= 0)
        val url = ("https://datasets-server.huggingface.co/" + if(where.isNotBlank() || orderBy.isNotBlank()) "filter" else "rows").toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("dataset", cleanRepo).addQueryParameter("config", config)
            .addQueryParameter("split", split).addQueryParameter("offset", offset.toString())
            .addQueryParameter("length", length.toString()).apply { if(where.isNotBlank()) addQueryParameter("where",where); if(orderBy.isNotBlank()) addQueryParameter("orderby",orderBy) }.build().toString()
        val request = newRequestBuilder(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext HfRowsResult(
                        success = false,
                        error = "Erreur lecture /rows HTTP ${response.code}: ${response.message}"
                    )
                }
                val body = response.body?.let(::boundedJson) ?: ""
                val adapter = moshi.adapter(ViewerRowsResponse::class.java)
                val parsed = adapter.fromJson(body)
                val rows = parsed?.rows?.map { rowWrapper ->
                    ViewerRowData(
                        rowIdx = rowWrapper.row_idx,
                        rowData = rowWrapper.row,
                        truncatedCells = rowWrapper.truncated_cells ?: emptyList()
                    )
                } ?: emptyList()
                val columns = parsed?.features?.map { it.name } ?: emptyList()
                if (parsed?.partial == true) return@withContext HfRowsResult(false, error = "Le Viewer ne couvre qu’une partie du corpus. Import suspendu pour ne pas annoncer un parcours complet.")

                HfRowsResult(
                    success = true,
                    columns = columns,
                    rows = rows,
                    numRowsPerDataset = parsed?.num_rows_per_page ?: length
                )
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            HfRowsResult(success = false, error = e.localizedMessage)
        }
    }

    /** Strong ETag + If-Range resume. A partial response can NEVER become the final file. */
    suspend fun downloadImage(url: String, destFile: File, onProgress: ((Long, Long) -> Unit)? = null,
                              maxBytes: Long = 64L * 1024 * 1024): Boolean = withContext(Dispatchers.IO) {
        require(maxBytes > 0)
        val temp = File(destFile.parentFile, destFile.name + ".part")
        val stateFile = File(destFile.parentFile, destFile.name + ".range")
        val state = Properties()
        if (stateFile.isFile) runCatching { stateFile.inputStream().use { state.load(it) } }.onFailure { state.clear() }
        val identity = url.toHttpUrlOrNull()?.newBuilder()?.fragment(null)?.build()?.toString()?.let {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }
        val previousTag = state.getProperty("etag")
        val knownTotal = state.getProperty("total")?.toLongOrNull()
        val recovered = com.unicornwhodev.visiondatasetstudio.core.storage.DownloadCheckpoint.recover(temp,state,identity,maxBytes)
        val resume = recovered != null
        if (!resume) { temp.delete(); stateFile.delete(); state.clear() }
        val offset = if (resume) temp.length() else 0L
        try {
            coroutineContext.ensureActive()
            val request = newRequestBuilder(url).header("Accept-Encoding", "identity").apply {
                if (resume) { header("Range", "bytes=$offset-"); header("If-Range", previousTag!!) }
            }.get().build()
            client.newCall(request).execute().use { response ->
                if (response.code == 416) { temp.delete(); stateFile.delete(); return@withContext false }
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val range = RangeSafety.parse(response.header("Content-Range"))
                val tag = response.header("ETag")
                val append = response.code == 206
                if (append) {
                    check(RangeSafety.validResume(offset, previousTag, tag, range, maxBytes) && range!!.total == knownTotal) {
                        temp.delete(); stateFile.delete(); "Réponse partielle incohérente; reprise refusée"
                    }
                } else check(response.code == 200) { "Réponse téléchargement inattendue" }
                val start = if (append) offset else 0L
                val total = if (append) range!!.total else body.contentLength()
                check(total <= maxBytes) { "Fichier trop volumineux" }
                if (!append) { temp.delete(); stateFile.delete() }
                val canCheckpoint=RangeSafety.strongEtag(tag) && total>0
                var copied = start
                val context = coroutineContext
                body.byteStream().use { input -> FileOutputStream(temp, append).use { output ->
                    val bytes = ByteArray(64 * 1024)
                    var checkpointAt=start
                    try { while (true) {
                        context.ensureActive()
                        val n = input.read(bytes); if (n < 0) break
                        check(n.toLong() <= maxBytes - copied) { "Plafond de téléchargement atteint" }
                        output.write(bytes, 0, n); copied += n
                        onProgress?.invoke(copied, total)
                        if(canCheckpoint && copied-checkpointAt>=4L*1024*1024) {
                            output.flush();output.fd.sync()
                            com.unicornwhodev.visiondatasetstudio.core.storage.DownloadCheckpoint.save(temp,stateFile,identity!!,tag!!,total)
                            checkpointAt=copied
                        }
                    } } finally {
                        // Also checkpoint a short, safely written prefix after a connection loss.
                        output.flush();output.fd.sync()
                        if(canCheckpoint && temp.length() in 1..total)
                            com.unicornwhodev.visiondatasetstudio.core.storage.DownloadCheckpoint.save(temp,stateFile,identity!!,tag!!,total)
                    }
                } }
                check(copied > 0 && (total < 0 || copied == total)) { "Téléchargement incomplet" }
                java.nio.file.Files.move(temp.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                stateFile.delete()
                true
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { false
        } finally {
            // Preserve resumable bytes across timeouts/process restarts, never an unvalidated body.
            if (!stateFile.isFile || temp.length() > maxBytes) { temp.delete(); stateFile.delete() }
        }
    }

    suspend fun createDatasetRepo(repoId: String, isPrivate: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val cleanRepo = repoId.trim().removePrefix("https://huggingface.co/datasets/").removePrefix("datasets/")
        val parts = cleanRepo.split("/")
        val name = parts.last()
        val org = if (parts.size > 1) parts.first() else null

        val payload = buildString {
            append("{\"type\":\"dataset\",\"name\":\"$name\",\"private\":$isPrivate")
            if (org != null) append(",\"organization\":\"$org\"")
            append("}")
        }
        val request = newRequestBuilder("https://huggingface.co/api/repos/create")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 409 // 409 = already exists
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            false
        }
    }

    /** Native Hub preupload + Git LFS + NDJSON commit. Real-account integration tests remain required. */
    suspend fun uploadBatchFiles(repoId: String, branch: String = "main", commitMessage: String,
                                files: List<Pair<String, File>>, expectedParentCommit: String): HfUploadResult = withContext(Dispatchers.IO) {
        try {
            val repo = StudioWorkflow.normalizeRepo(repoId, destination = true) ?: error("Dépôt de destination invalide")
            require(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.validRevision(branch)) { "Branche invalide" }
            val encodedBranch=java.net.URLEncoder.encode(branch,"UTF-8").replace("+","%20")
            val parentCommit = expectedParentCommit
            require(parentCommit.matches(Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}"))) { "Parent de publication non épinglé" }
            require(files.isNotEmpty() && files.map { it.first }.distinct().size == files.size)
            files.forEach { (path, file) ->
                require(!path.startsWith('/') && path.split('/').none { it == ".." || it.isEmpty() } && !path.contains('\\'))
                require(file.isFile) { "Fichier local absent" }
            }
            val modes = mutableMapOf<String, String>()
            for (chunk in files.chunked(100)) {
                val payload = mapOf("files" to chunk.map { (path, file) ->
                    val sample = file.inputStream().use { input -> val b = ByteArray(512); val n = input.read(b).coerceAtLeast(0); b.copyOf(n) }
                    mapOf("path" to path, "size" to file.length(), "sample" to Base64.getEncoder().encodeToString(sample))
                })
                val response = postJson("https://huggingface.co/api/datasets/$repo/preupload/$encodedBranch", payload)
                for (entry in response["files"] as? List<*> ?: error("Réponse preupload incomplète")) {
                    val e = entry as? Map<*, *> ?: error("Réponse preupload invalide")
                    check(e["shouldIgnore"] != true) { "Le dépôt ignore un fichier demandé. Publication arrêtée." }
                    modes[e["path"] as String] = e["uploadMode"] as String
                }
            }
            val hashes = files.associate { (path, file) -> path to HashUtils.computeSha256(file) }
            for ((path, file) in files) {
                when (if (file.length() == 0L) "regular" else modes[path]) {
                    "lfs" -> uploadLfs(repo, file, hashes.getValue(path), branch)
                    "regular" -> Unit
                    else -> error("Mode de transfert non pris en charge; aucun commit créé.")
                }
            }
            // Small fixed-size base64 blocks (multiples of 3) avoid loading an entire TAR into RAM.
            val body = object : RequestBody() {
                override fun contentType() = "application/x-ndjson".toMediaType()
                override fun writeTo(sink: BufferedSink) {
                    sink.writeUtf8(json(mapOf("key" to "header", "value" to mapOf("summary" to commitMessage, "description" to "Vision Dataset Studio — reviewed batch", "parentCommit" to parentCommit))) + "\n")
                    for ((path, file) in files) {
                        if (modes[path] == "lfs" && file.length() > 0L) {
                            sink.writeUtf8(json(mapOf("key" to "lfsFile", "value" to mapOf("path" to path, "algo" to "sha256", "oid" to hashes.getValue(path), "size" to file.length()))) + "\n")
                        } else {
                            sink.writeUtf8("{\"key\":\"file\",\"value\":{\"path\":" + json(path) + ",\"encoding\":\"base64\",\"content\":\"")
                            file.inputStream().use { input ->
                                val buffer = ByteArray(3 * 8192)
                                while (true) {
                                    var used = 0
                                    while (used < buffer.size) { val n = input.read(buffer, used, buffer.size - used); if (n < 0) break; used += n }
                                    if (used == 0) break
                                    sink.writeUtf8(Base64.getEncoder().encodeToString(buffer.copyOf(used)))
                                }
                            }
                            sink.writeUtf8("\"}}\n")
                        }
                    }
                }
            }
            client.newCall(newRequestBuilder("https://huggingface.co/api/datasets/$repo/commit/$encodedBranch").post(body).build()).execute().use { response ->
                if (response.code == 409 || response.code == 412) return@withContext HfUploadResult(false,
                    message = "La branche a changé : conflit détecté. Aucun rebase automatique.", conflict = true)
                check(response.isSuccessful) { "Commit refusé (HTTP ${response.code}). Fichiers locaux conservés." }
                val parsed = parseObject(response.body?.let(::boundedJson) ?: error("Réponse commit absente"))
                val sha = parsed["commitOid"] as? String ?: error("Aucun identifiant de commit vérifiable reçu.")
                require(sha.matches(Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")))
                HfUploadResult(true, sha, "Commit reçu. Vérification des contenus requise avant purge.")
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { HfUploadResult(false, message = e.message ?: "Échec du transfert; données locales conservées.") }
    }

    private fun boundedJson(body:okhttp3.ResponseBody):String {
        val output=java.io.ByteArrayOutputStream()
        body.byteStream().use { DurableFiles.copyBounded(it,output,16L*1024*1024) }
        return output.toString("UTF-8")
    }

    private fun json(value: Any): String = moshi.adapter(Any::class.java).toJson(value)
    @Suppress("UNCHECKED_CAST")
    private fun parseObject(value: String) = moshi.adapter(Map::class.java).fromJson(value) as? Map<String, Any?> ?: error("JSON distant invalide")
    private fun postJson(url: String, payload: Any, mediaType: String = "application/json", headers: Map<String, String> = emptyMap()): Map<String, Any?> {
        client.newCall(newRequestBuilder(url).apply { headers.forEach { (k,v) -> header(k,v) } }.header("Accept", mediaType).post(json(payload).toRequestBody(mediaType.toMediaType())).build()).execute().use { r ->
            check(r.isSuccessful) { "Transfert refusé (HTTP ${r.code})." }
            return parseObject(r.body?.let(::boundedJson)?.ifBlank { "{}" } ?: "{}")
        }
    }
    private fun uploadLfs(repo: String, file: File, hash: String, branch: String) {
        val result = postJson("https://huggingface.co/datasets/$repo.git/info/lfs/objects/batch", mapOf(
            "operation" to "upload", "transfers" to listOf("basic", "multipart"), "hash_algo" to "sha256",
            "ref" to mapOf("name" to "refs/heads/$branch"), "objects" to listOf(mapOf("oid" to hash, "size" to file.length()))), "application/vnd.git-lfs+json")
        val obj = (result["objects"] as? List<*>)?.singleOrNull() as? Map<*, *> ?: error("Réponse LFS incomplète")
        check(obj["oid"] == hash && obj["error"] == null) { "Objet LFS refusé" }
        val actions = obj["actions"] as? Map<*, *> ?: return // content already exists server-side
        val upload = actions["upload"] as? Map<*, *> ?: error("Action LFS absente")
        val href = upload["href"] as? String ?: error("URL LFS absente")
        val headers = upload["header"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val chunkSize = headers["chunk_size"]?.toString()?.toLongOrNull()
        if (chunkSize == null) {
            putPart(href, file, 0, file.length(), safeActionHeaders(headers))
        } else {
            require(chunkSize > 0)
            val urls = headers.entries.mapNotNull { e -> e.key.toString().toIntOrNull()?.let { it to e.value.toString() } }.sortedBy { it.first }
            check(urls.size.toLong() == (file.length() + chunkSize - 1) / chunkSize)
            val parts = urls.mapIndexed { i, (number, url) ->
                check(number == i + 1)
                val etag = putPart(url, file, i * chunkSize, minOf(chunkSize, file.length() - i * chunkSize))
                check(!etag.isNullOrBlank()) { "ETag multipart absent" }
                mapOf("partNumber" to number, "etag" to etag)
            }
            postJson(href, mapOf("oid" to hash, "parts" to parts), "application/vnd.git-lfs+json")
        }
        (actions["verify"] as? Map<*, *>)?.let { verify ->
            postJson(verify["href"] as String, mapOf("oid" to hash, "size" to file.length()), headers = safeActionHeaders(verify["header"] as? Map<*, *> ?: emptyMap<Any, Any>()))
        }
    }
    private fun safeActionHeaders(headers: Map<*, *>): Map<String, String> = headers.entries.mapNotNull { (k,v) ->
        val name = k as? String ?: return@mapNotNull null
        if (name.toIntOrNull() != null || name.lowercase() in setOf("chunk_size", "host", "content-length", "transfer-encoding")) null
        else name to (v as? String ?: error("En-tête LFS invalide"))
    }.toMap()
    private fun putPart(url: String, file: File, start: Long, length: Long, headers: Map<String, String> = emptyMap()): String? {
        require(url.toHttpUrlOrNull()?.isHttps == true)
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: BufferedSink) { RandomAccessFile(file, "r").use { input ->
                input.seek(start); var remaining = length; val buffer = ByteArray(64 * 1024)
                while (remaining > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    check(n > 0) { "Fichier modifié pendant le transfert" }; sink.write(buffer, 0, n); remaining -= n
                }
            } }
        }
        // Signed storage URLs never receive the user's HF token.
        client.newBuilder().followRedirects(false).build().newCall(Request.Builder().url(url).apply { headers.forEach { (k,v) -> header(k,v) } }.put(body).build()).execute().use { r ->
            check(r.isSuccessful) { "Envoi binaire refusé (HTTP ${r.code})" }; return r.header("ETag")
        }
    }
    suspend fun resolveModelRevision(repoId: String, revision: String = "main"): String = withContext(Dispatchers.IO) {
        val clean = repoId.trim().removePrefix("https://huggingface.co/").removePrefix("models/")
        require(clean.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Dépôt modèle invalide" }
        require(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.validRevision(revision))
        val url = "https://huggingface.co/api/models/$clean/revision/".toHttpUrlOrNull()!!.newBuilder().addPathSegment(revision).build().toString()
        client.newCall(newRequestBuilder(url).get().build()).execute().use { r ->
            check(r.isSuccessful) { "Révision modèle inaccessible (HTTP ${r.code})" }
            val sha = parseObject(r.body?.let(::boundedJson) ?: error("Réponse vide"))["sha"] as? String ?: error("SHA modèle absent")
            require(sha.matches(Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")))
            sha
        }
    }

    fun resolveModelUrl(repoId: String, revision: String, path: String): String {
        val clean = repoId.trim().removePrefix("https://huggingface.co/").removePrefix("models/")
        require(clean.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Dépôt modèle invalide" }
        require(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.safeRelativePath(path))
        return "https://huggingface.co/$clean/resolve/".toHttpUrlOrNull()!!.newBuilder().addPathSegment(revision).addPathSegments(path).build().toString()
    }

    /** List a bounded model-repository tree. Used only for the allowlisted community model catalogue. */
    suspend fun listModelTree(repoId: String, revision: String = "main", path: String = "models"): List<HfTreeItem> = withContext(Dispatchers.IO) {
        val clean = repoId.trim().removePrefix("https://huggingface.co/").removePrefix("models/")
        require(clean.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Dépôt modèle invalide" }
        require(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.validRevision(revision))
        require(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.safeRelativePath(path))
        val url = "https://huggingface.co/api/models/$clean/tree/".toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment(revision).addPathSegments(path).addQueryParameter("recursive", "true").addQueryParameter("expand", "false").build().toString()
        client.newCall(newRequestBuilder(url).get().build()).execute().use { r ->
            check(r.isSuccessful) { "Catalogue modèles inaccessible (HTTP ${r.code})" }
            val raw = r.body?.let(::boundedJson) ?: "[]"
            @Suppress("UNCHECKED_CAST")
            val rows = moshi.adapter(List::class.java).fromJson(raw) as? List<Map<String, Any?>> ?: emptyList()
            require(rows.size <= 5000) { "Catalogue distant trop volumineux" }
            rows.mapNotNull { row ->
                val itemPath = row["path"] as? String ?: return@mapNotNull null
                val type = row["type"] as? String ?: "file"
                val size = (row["size"] as? Number)?.toLong() ?: 0L
                HfTreeItem(itemPath, type, size)
            }
        }
    }

    suspend fun downloadModelFile(repoId: String, revision: String, path: String, dest: File, maxBytes: Long): Boolean =
        downloadImage(resolveModelUrl(repoId, revision, path), dest, maxBytes = maxBytes)

    /** Read one small coordination JSON file; 404 means no claim yet. */
    suspend fun readDatasetText(repoId: String, revision: String, path: String, maxBytes: Long = 64L * 1024): String? = withContext(Dispatchers.IO) {
        require(maxBytes in 1..1024L * 1024)
        val request = newRequestBuilder(resolveUrl(repoId, revision, path)).get().build()
        client.newCall(request).execute().use { r ->
            if (r.code == 404) return@withContext null
            check(r.isSuccessful) { "Lecture coordination refusée (HTTP ${r.code})" }
            val body = r.body ?: return@withContext ""
            val out = java.io.ByteArrayOutputStream()
            body.byteStream().use { DurableFiles.copyBounded(it, out, maxBytes) }
            out.toString("UTF-8")
        }
    }

    suspend fun requirePathsAbsent(repoId: String, commit: String, paths: List<String>) = withContext(Dispatchers.IO) {
        for (path in paths) {
            coroutineContext.ensureActive()
            client.newCall(newRequestBuilder(resolveUrl(repoId, commit, path)).head().build()).execute().use { r ->
                check(r.code == 404) { if (r.isSuccessful) "Chemin distant déjà occupé : nouvel emplacement requis"
                    else "Impossible de vérifier l’absence du chemin distant (HTTP ${r.code})" }
            }
        }
    }

    suspend fun verifyRemoteCommit(repoId: String, commitSha: String): Boolean = withContext(Dispatchers.IO) {
        if (!commitSha.matches(Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}"))) return@withContext false
        try {
            val repo = StudioWorkflow.normalizeRepo(repoId, destination = true) ?: error("Dépôt de destination invalide")
            client.newCall(newRequestBuilder("https://huggingface.co/api/datasets/$repo/revision/$commitSha").build()).execute().use { r ->
                r.isSuccessful && parseObject(r.body?.let(::boundedJson)?.ifBlank { "{}" } ?: "{}")["sha"] == commitSha
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }
    /** Hashes and lengths are persisted before upload, so verification survives cache cleanup. */
    suspend fun verifyRemoteFiles(repoId: String, commitSha: String, files: List<Pair<String, File>>): Boolean =
        verifyRemoteDigests(repoId, commitSha, files.map { (path, file) ->
            RemoteFileDigest(path, file.length(), HashUtils.computeSha256(file))
        })

    suspend fun verifyRemoteDigests(repoId: String, commitSha: String, files: List<RemoteFileDigest>): Boolean = withContext(Dispatchers.IO) {
        try {
            require(files.isNotEmpty() && files.size <= 10000 && files.map { it.path }.distinct().size == files.size)
            check(verifyRemoteCommit(repoId, commitSha))
            for (file in files) {
                coroutineContext.ensureActive()
                require(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.safeRelativePath(file.path) && file.size >= 0 && file.sha256.matches(Regex("[a-f0-9]{64}")))
                client.newCall(newRequestBuilder(resolveUrl(repoId, commitSha, file.path)).build()).execute().use { response ->
                    check(response.isSuccessful)
                    val digest = MessageDigest.getInstance("SHA-256"); var count = 0L
                    (response.body ?: error("Contenu absent")).byteStream().use { input ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer); if (n < 0) break
                            check(n.toLong() <= file.size - count) { "Contenu distant trop long" }
                            count += n; digest.update(buffer, 0, n)
                        }
                    }
                    check(count == file.size && digest.digest().joinToString("") { "%02x".format(it) } == file.sha256)
                }
            }
            true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }

}

// Data Transfer Objects for Moshi
@JsonClass(generateAdapter = true)
data class WhoAmIResponse(
    val name: String? = null,
    val username: String? = null,
    val email: String? = null,
    val orgs: List<HfOrg>? = null
)

@JsonClass(generateAdapter = true)
data class HfOrg(val name: String)

data class HfWhoAmIResult(
    val isValid: Boolean,
    val username: String? = null,
    val email: String? = null,
    val orgs: List<String> = emptyList(),
    val error: String? = null
)

data class HfRepoAccessResult(
    val exists: Boolean,
    val isPrivate: Boolean = false,
    val isGated: Boolean = false,
    val isUnauthorized: Boolean = false,
    val isForbidden: Boolean = false,
    val sha: String? = null,
    val message: String = ""
)

data class DatasetSplitItem(
    val config: String,
    val split: String,
    val numRows: Long? = null
)

data class HfSplitsResult(
    val success: Boolean,
    val splits: List<DatasetSplitItem> = emptyList(),
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ViewerSplitsResponse(
    val splits: List<ViewerSplitEntry>? = null
)

@JsonClass(generateAdapter = true)
data class ViewerSplitEntry(
    val dataset: String? = null,
    val config: String,
    val split: String,
    val num_rows: Long? = null
)

@JsonClass(generateAdapter = true)
data class ViewerRowsResponse(
    val rows: List<ViewerRowWrapper>? = null,
    val num_rows_per_page: Int? = null,
    val partial: Boolean = false,
    val features: List<ViewerFeatureWrapper>? = null
)

@JsonClass(generateAdapter = true)
data class ViewerRowWrapper(
    val row_idx: Long,
    val row: Map<String, Any?>,
    val truncated_cells: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class ViewerFeatureWrapper(
    val feature_idx: Int? = null,
    val name: String,
    val type: Any? = null
)

@JsonClass(generateAdapter = true)
data class ViewerFeatureKey(
    val key: String
)

data class ViewerRowData(
    val rowIdx: Long,
    val rowData: Map<String, Any?>,
    val truncatedCells: List<String> = emptyList()
)

data class HfRowsResult(
    val success: Boolean,
    val columns: List<String> = emptyList(),
    val rows: List<ViewerRowData> = emptyList(),
    val numRowsPerDataset: Int = 0,
    val error: String? = null
)

data class HfUploadResult(
    val success: Boolean,
    val commitSha: String? = null,
    val message: String = "",
    val conflict: Boolean = false
)

data class HfTreeItem(val path: String, val type: String, val size: Long = 0L)

@JsonClass(generateAdapter = true)
data class RemoteFileDigest(val path: String, val size: Long, val sha256: String)
@JsonClass(generateAdapter = true)
data class RemoteReceipt(val schema: Int = 1, val files: List<RemoteFileDigest>)
