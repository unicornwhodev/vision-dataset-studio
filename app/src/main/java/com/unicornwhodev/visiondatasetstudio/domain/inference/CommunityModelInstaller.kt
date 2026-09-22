package com.unicornwhodev.visiondatasetstudio.domain.inference

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
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
        f.type!="directory" && f.path.substringAfterLast('/')!="artifact_manifest.json" && (f.path.substringAfterLast('.').lowercase() in setOf("tflite","json","txt","model","vocab","md") || isDocumentation(f.path))
    }
    internal fun verifyRuntimeFiles(directory:File, required:Set<String>) {
        required.forEach { relative ->
            require(ProcessingSettings.safeRelativePath(relative))
            val file=File(directory,relative)
            require(file.canonicalPath.startsWith(directory.canonicalPath+File.separator))
            require(file.isFile && file.canRead() && file.length()>0) {
                tr("Artefact runtime nécessaire absent : $relative", "Required runtime artifact missing: $relative")
            }
        }
    }
    suspend fun install(item:CommunityModelCatalog.Availability,directory:File,hf:HfApiClient,progress:(Int,Int)->Unit):File = withContext(Dispatchers.IO) {
        require(item.installableNow && !directory.exists());directory.mkdirs()
        val items=artifacts(item);val prefix=item.sourcePrefix
        require(items.isNotEmpty() && items.size<=128)
        val hashes=linkedMapOf<String,String>()
        items.forEachIndexed{index,remote->
            val relative=remote.path.removePrefix(prefix)
            require(remote.path.startsWith(prefix) && ProcessingSettings.safeRelativePath(relative))
            require(remote.size in 1..(2L*1024*1024*1024))
            val file=File(directory,relative).apply{parentFile?.mkdirs()}
            require(file.canonicalPath.startsWith(directory.canonicalPath+File.separator))
            check(hf.downloadModelFile(item.sourceRepo,item.repoSha,remote.path,file,remote.size)){tr("Téléchargement interrompu : $relative", "Download interrupted: $relative")}
            require(file.length()==remote.size){tr("Taille différente du catalogue épinglé", "Size differs from the pinned catalog")}
            hashes[relative]=HashUtils.computeSha256(file);progress(index+1,items.size)
        }
        verifyRuntimeFiles(directory,item.entry.expectedFiles.toSet())
        if(item.entry.expectedFiles.size==1)return@withContext File(directory,item.entry.expectedFiles.single())
        val bundle=File(directory,"bundle.json")
        bundle.writeText(StudioJson.moshi.adapter(BundleManifest::class.java).toJson(BundleManifest(kind=item.entry.id,revision=item.repoSha,files=hashes)))
        bundle
    }
}
