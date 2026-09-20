package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CommunityModelInstaller {
    fun artifacts(item:CommunityModelCatalog.Availability)=item.files.filter{f->
        f.type!="directory" && f.path.substringAfterLast('.').lowercase() in setOf("tflite","json","txt","md")
    }
    suspend fun install(item:CommunityModelCatalog.Availability,directory:File,hf:HfApiClient,progress:(Int,Int)->Unit):File = withContext(Dispatchers.IO) {
        require(item.installableNow && !directory.exists());directory.mkdirs()
        val items=artifacts(item);val prefix="models/${item.entry.id}/"
        require(items.isNotEmpty() && items.size<=128)
        val hashes=linkedMapOf<String,String>()
        items.sortedBy{if(it.path.endsWith("artifact_manifest.json"))0 else 1}.forEachIndexed{index,remote->
            val relative=remote.path.removePrefix(prefix)
            require(remote.path.startsWith(prefix) && ProcessingSettings.safeRelativePath(relative))
            require(remote.size in 1..(2L*1024*1024*1024))
            val file=File(directory,relative).apply{parentFile?.mkdirs()}
            require(file.canonicalPath.startsWith(directory.canonicalPath+File.separator))
            check(hf.downloadModelFile(CommunityModelCatalog.repoId,item.repoSha,remote.path,file,remote.size)){"Téléchargement interrompu : $relative"}
            require(file.length()==remote.size){"Taille différente du catalogue épinglé"}
            hashes[relative]=HashUtils.computeSha256(file);progress(index+1,items.size)
        }
        val manifest=File(directory,"artifact_manifest.json")
        require(manifest.isFile()){"Manifeste SHA-256 requis pour les modèles du catalogue"}
        val expected=StudioJson.moshi.adapter(Any::class.java).fromJson(manifest.readText()) as? Map<*,*> ?: error("Manifeste invalide")
        expected.forEach{(name,value)->
            val info=value as? Map<*,*> ?: error("Entrée de manifeste invalide")
            val relative=name.toString()
            if(relative in hashes) {
                require(hashes[relative]==info["sha256"] && File(directory,relative).length()==(info["bytes"] as Number).toLong()){"SHA-256 ou taille non conforme : $relative"}
            }
        }
        item.entry.expectedFiles.forEach{require(it in hashes && it in expected){"Poids non couverts par le manifeste"}}
        if(item.entry.expectedFiles.size==1)return@withContext File(directory,item.entry.expectedFiles.single())
        val bundle=File(directory,"bundle.json")
        bundle.writeText(StudioJson.moshi.adapter(BundleManifest::class.java).toJson(BundleManifest(kind=item.entry.id,revision=item.repoSha,files=hashes)))
        bundle
    }
}
