package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CommunityModelInstaller {
    private val documentationNames = setOf("LICENSE", "LICENCE", "COPYING", "NOTICE", "CHANGELOG")
    fun isDocumentation(path:String):Boolean = path.substringAfterLast('/').let { name ->
        name.endsWith(".md", true) || name.substringBeforeLast('.', name).uppercase() in documentationNames
    }
    fun artifacts(item:CommunityModelCatalog.Availability)=item.files.filter{f->
        f.type!="directory" && (f.path.substringAfterLast('.').lowercase() in setOf("tflite","json","txt","model","vocab","md") || isDocumentation(f.path))
    }
    internal fun verifyManifest(directory:File, hashes:Map<String,String>, expected:Map<*,*>, required:Set<String>) {
        required.forEach { relative ->
            require(!isDocumentation(relative)) { "Un fichier documentaire ne peut pas être un artefact runtime requis : $relative" }
            val info=expected[relative] as? Map<*,*> ?: error("Artefact runtime absent du manifeste : $relative")
            val bytes=(info["bytes"] as? Number)?.toLong() ?: error("Taille absente du manifeste : $relative")
            val sha=info["sha256"] as? String ?: error("SHA-256 absent du manifeste : $relative")
            require(relative in hashes && File(directory,relative).isFile) { "Artefact runtime nécessaire absent : $relative" }
            require(hashes[relative]==sha && File(directory,relative).length()==bytes) { "SHA-256 ou taille non conforme : $relative" }
        }
        // Documentation is deliberately best-effort. Other downloaded runtime/config files remain integrity protected.
        hashes.filterKeys { !isDocumentation(it) }.forEach { (relative, actual) ->
            val info=expected[relative] as? Map<*,*> ?: return@forEach
            require(actual==info["sha256"] && File(directory,relative).length()==(info["bytes"] as Number).toLong()) { "SHA-256 ou taille non conforme : $relative" }
        }
    }
    suspend fun install(item:CommunityModelCatalog.Availability,directory:File,hf:HfApiClient,progress:(Int,Int)->Unit):File = withContext(Dispatchers.IO) {
        require(item.installableNow && !directory.exists());directory.mkdirs()
        val items=artifacts(item);val prefix=item.sourcePrefix
        require(items.isNotEmpty() && items.size<=128)
        val hashes=linkedMapOf<String,String>()
        items.sortedBy{if(it.path.endsWith("artifact_manifest.json"))0 else 1}.forEachIndexed{index,remote->
            val relative=remote.path.removePrefix(prefix)
            require(remote.path.startsWith(prefix) && ProcessingSettings.safeRelativePath(relative))
            require(remote.size in 1..(2L*1024*1024*1024))
            val file=File(directory,relative).apply{parentFile?.mkdirs()}
            require(file.canonicalPath.startsWith(directory.canonicalPath+File.separator))
            check(hf.downloadModelFile(item.sourceRepo,item.repoSha,remote.path,file,remote.size)){"Téléchargement interrompu : $relative"}
            require(file.length()==remote.size){"Taille différente du catalogue épinglé"}
            hashes[relative]=HashUtils.computeSha256(file);progress(index+1,items.size)
        }
        val manifest=File(directory,"artifact_manifest.json")
        require(manifest.isFile()){"Manifeste SHA-256 requis pour les modèles du catalogue"}
        val expected=StudioJson.moshi.adapter(Any::class.java).fromJson(manifest.readText()) as? Map<*,*> ?: error("Manifeste invalide")
        verifyManifest(directory,hashes,expected,item.entry.expectedFiles.toSet())
        if(item.entry.expectedFiles.size==1)return@withContext File(directory,item.entry.expectedFiles.single())
        val bundle=File(directory,"bundle.json")
        bundle.writeText(StudioJson.moshi.adapter(BundleManifest::class.java).toJson(BundleManifest(kind=item.entry.id,revision=item.repoSha,files=hashes)))
        bundle
    }
}
