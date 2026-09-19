package com.unicornwhodev.visiondatasetstudio.data.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.unicornwhodev.visiondatasetstudio.core.workflow.SourceIdentity
import com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings
import com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings
import com.unicornwhodev.visiondatasetstudio.domain.inference.ProposalMerger
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

interface DatasetSource {
    suspend fun page(project:ProjectEntity,offset:Long,count:Int):List<SourceEntryEntity>
}

class SourceCatalog(private val context:Context,private val db:AppDatabase,private val hf:HfApiClient):DatasetSource {
    private val moshi=com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi
    private val annotationAdapter=moshi.adapter(SampleAnnotations::class.java).failOnUnknown()
    override suspend fun page(project:ProjectEntity,offset:Long,count:Int):List<SourceEntryEntity> {
        val settings=ProjectSettings.read(project)
        require(count in 1..1000)
        if(settings.sourceMode!="HF_VIEWER") {
            check(settings.sourceIndexReady) { "Importez/indexez la source avant de préparer un lot" }
            val result=mutableListOf<SourceEntryEntity>();var chars=0L
            while(result.size<count) {
                val rows=db.sourceEntryDao().page(project.id,offset+result.size,minOf(16,count-result.size))
                if(rows.isEmpty())break
                for(row in rows) {
                    val cost=row.assetId.length.toLong()+row.imageRef.length+(row.annotationJson?.length ?: 0)
                    require(cost<=4L*1024*1024) { "Métadonnées d’un cas trop volumineuses" }
                    if(result.isNotEmpty() && chars+cost>4L*1024*1024)return result
                    result+=row;chars+=cost
                }
                if(rows.size<16)break
            }
            return result
        }
        val result=mutableListOf<SourceEntryEntity>()
        var cursor=offset
        for(size in ProcessingSettings.pageSizes(count)) {
            coroutineContext.ensureActive()
            val response=hf.fetchViewerRows(project.hfSourceRepo,project.sourceConfig,project.sourceSplit,cursor,size,settings.filterExpression,settings.orderBy)
            check(response.success) { response.error ?: "Lecture HF impossible" }
            for((n,row) in response.rows.withIndex()) {
                check(project.imageColumn !in row.truncatedCells) { "Cellule image tronquée : ligne ${row.rowIdx}" }
                val raw=row.rowData[project.imageColumn]
                val ref=when(raw){is Map<*,*>->raw["src"] as? String ?: raw["url"] as? String;is String->raw;else->null}
                check(ref?.startsWith("https://")==true) { "Colonne ${project.imageColumn} sans URL HTTPS exploitable. Utilisez une source manifeste." }
                val id=SourceIdentity.string(row.rowData[project.idColumn]) ?: "${project.sourceConfig}:${project.sourceSplit}:${row.rowIdx}"
                val a=if(settings.importAnnotations) row.rowData["annotations"]?.let{annotationJson(it)} else null
                // ordinal is the position in the selected/filtered stream, not necessarily upstream row_idx.
                result.add(SourceEntryEntity(project.id,cursor+n,id,ref!!,row.rowIdx,SourceIdentity.string(row.rowData["group_id"] ?: row.rowData["group"]),a))
            }
            cursor+=response.rows.size
            if(response.rows.size<size) break
        }
        return result
    }
    private fun annotationJson(value:Any):String {
        val a=if(value is String) annotationAdapter.fromJson(value) else annotationAdapter.fromJsonValue(value)
        return annotationAdapter.toJson(ProposalMerger.draft(a ?: error("Annotations canoniques invalides")))
    }
    private suspend fun beginIndex(project:ProjectEntity) {
        check(db.batchDao().getLatestBatchSync(project.id)==null) { "Source verrouillée : créez un autre projet pour changer de corpus" }
        val config=ProjectSettings.read(project)
        db.projectDao().saveProject(project.copy(settingsJson=ProjectSettings.write(config.copy(sourceIndexReady=false))))
        db.sourceEntryDao().clear(project.id)
    }
    suspend fun importManifest(project:ProjectEntity,uri:Uri,tree:Uri?=null):Long = withContext(Dispatchers.IO) {
        val stream=context.contentResolver.openInputStream(uri) ?: error("Manifeste inaccessible")
        stream.use { input ->
            beginIndex(project)
            val count=readManifest(project,input) { ref ->
                when { ref.startsWith("https://")->ref
                    tree!=null->resolveChild(tree,ref).toString()
                    else->error("Les chemins relatifs exigent un dossier source choisi. Les URL HTTPS peuvent être importées seules.") }
            }
            val settings=ProjectSettings.read(project).copy(sourceMode="LOCAL_INDEX",sourceIndexReady=true,localTreeUri=tree?.toString(),localSourceLabel="Manifeste JSONL local",resolvedSourceRevision=null)
            db.projectDao().saveProject(project.copy(hfSourceRepo="",settingsJson=ProjectSettings.write(settings),lastRowCursor=0))
            count
        }
    }
    suspend fun importHfManifest(project:ProjectEntity):Long = withContext(Dispatchers.IO) {
        val settings=ProjectSettings.read(project)
        beginIndex(project)
        val sha=hf.resolveRevision(project.hfSourceRepo,settings.sourceRevision)
        val uri=hf.resolveUrl(project.hfSourceRepo,sha,settings.manifestPath)
        val tmp=File(context.cacheDir,"source-${project.id}.jsonl.part")
        try {
            val space=com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager(context)
            val limit=minOf(128L*1024*1024,project.diskBudgetMb*1048576-space.getUsedSpaceBytes(),space.getFreeSpaceBytes()-settings.reserveFreeMb*1048576L)
            check(limit>0){"Budget insuffisant pour le manifeste"}
            check(hf.downloadImage(uri,tmp,maxBytes=limit)) { "Manifeste HF inaccessible ou supérieur à 128 Mo" }
            val parent=settings.manifestPath.substringBeforeLast('/',"")
            val count=tmp.inputStream().use { input->readManifest(project,input) { ref->
                if(ref.startsWith("https://")) ref else {
                    require(ProcessingSettings.safeRelativePath(ref)) { "Chemin image invalide" }
                    hf.resolveUrl(project.hfSourceRepo,sha,if(parent.isEmpty()) ref else "$parent/$ref")
                }
            } }
            db.projectDao().saveProject(project.copy(settingsJson=ProjectSettings.write(settings.copy(sourceMode="HF_MANIFEST",resolvedSourceRevision=sha,sourceIndexReady=true)),lastRowCursor=0))
            count
        } finally { tmp.delete() }
    }
    /** Streaming JSONL, bounded records, metadata inserted in chunks; no original images loaded here. */
    private suspend fun readManifest(project:ProjectEntity,input:InputStream,resolve:(String)->String):Long {
        val rows=mutableListOf<SourceEntryEntity>();var ordinal=0L
        val settings=ProjectSettings.read(project)
        val reader=input.bufferedReader()
        var total=0L
        while(true) {
            coroutineContext.ensureActive()
            val line=boundedLine(reader,1024*1024) ?: break
            total+=line.length;check(total<=128L*1024*1024) { "Manifeste trop volumineux" }
            if(line.isBlank()) continue
            val data=moshi.adapter(Map::class.java).fromJson(line) ?: error("JSONL invalide à l’entrée $ordinal")
            val media=data["media"] as? Map<*,*>
            val ref=(data[project.imageColumn] as? String) ?: (data["file_name"] as? String) ?: (data["image_url"] as? String) ?: (media?.get("filename") as? String) ?: error("Image absente à l’entrée $ordinal")
            val id=SourceIdentity.string(data[project.idColumn] ?: data["sample_id"]) ?: ref
            val a=if(settings.importAnnotations) data["annotations"]?.let{annotationJson(it)} else null
            rows.add(SourceEntryEntity(project.id,ordinal,id,resolve(ref),ordinal++,SourceIdentity.string(data["group_id"]),a))
            if(rows.size==256) { db.sourceEntryDao().insert(rows.toList());rows.clear();checkIndexBudget(project) }
        }
        if(rows.isNotEmpty()) db.sourceEntryDao().insert(rows)
        checkIndexBudget(project)
        check(ordinal>0) { "Manifeste vide" };return ordinal
    }
    private fun checkIndexBudget(p:ProjectEntity) {
        check(com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager(context).getUsedSpaceBytes()<p.diskBudgetMb*1024*1024) { "Budget de métadonnées atteint" }
        check(context.filesDir.usableSpace>=ProjectSettings.read(p).reserveFreeMb*1024L*1024) { "Réserve disque atteinte" }
    }
    private fun boundedLine(reader:BufferedReader,max:Int):String? {
        val b=StringBuilder()
        while(true){val c=reader.read();if(c<0)return if(b.isEmpty()) null else b.toString();if(c==10)return b.toString();if(c!=13)b.append(c.toChar());check(b.length<=max){"Ligne JSONL supérieure à 1 Mo"}}
    }
    private data class Child(val id:String,val name:String,val mime:String)
    private fun findChild(tree:Uri,parentId:String,name:String):String {
        val uri=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId)
        var match:String?=null
        context.contentResolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { cursor ->
            while(cursor.moveToNext()) if(cursor.getString(1)==name) {
                check(match==null){"Chemin local ambigu"};match=cursor.getString(0)
            }
        } ?: error("Dossier inaccessible")
        return match ?: error("Fichier local absent: $name")
    }
    private suspend fun streamChildren(tree:Uri,parentId:String,visit:suspend (Child)->Unit) {
        val uri=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId)
        val cursor=context.contentResolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)
            ?: error("Dossier inaccessible")
        cursor.use { while(it.moveToNext()) { coroutineContext.ensureActive();visit(Child(it.getString(0),it.getString(1),it.getString(2))) } }
    }
    private fun resolveChild(tree:Uri,path:String):Uri {
        require(ProcessingSettings.safeRelativePath(path)) { "Chemin local invalide" }
        var parent=DocumentsContract.getTreeDocumentId(tree)
        for(part in path.split('/')) { parent=findChild(tree,parent,part) }
        return DocumentsContract.buildDocumentUriUsingTree(tree,parent)
    }
    suspend fun importFolder(project:ProjectEntity,tree:Uri):Long = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        beginIndex(project)
        var ordinal=0L;val rows=mutableListOf<SourceEntryEntity>();val visited=mutableSetOf<String>()
        suspend fun walk(parent:String,prefix:String,depth:Int) {
            require(depth<=32){"Arborescence trop profonde"};check(visited.add(parent)){"Boucle de dossiers"}
            streamChildren(tree,parent) { child ->
                coroutineContext.ensureActive()
                val path=prefix+child.name
                if(child.mime==DocumentsContract.Document.MIME_TYPE_DIR) walk(child.id,"$path/",depth+1)
                else if(child.mime in setOf("image/jpeg","image/png","image/webp") || child.name.substringAfterLast('.').lowercase() in setOf("jpg","jpeg","png","webp")) {
                    rows.add(SourceEntryEntity(project.id,ordinal++,path,DocumentsContract.buildDocumentUriUsingTree(tree,child.id).toString()))
                    if(rows.size==256){db.sourceEntryDao().insert(rows.toList());rows.clear();checkIndexBudget(project)}
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree),"",0)
        if(rows.isNotEmpty()) db.sourceEntryDao().insert(rows)
        checkIndexBudget(project)
        check(ordinal>0){"Aucune image JPEG/PNG/WebP dans ce dossier"}
        val config=ProjectSettings.read(project).copy(sourceMode="LOCAL_INDEX",sourceIndexReady=true,localTreeUri=tree.toString(),localSourceLabel="Dossier local · $ordinal images",resolvedSourceRevision=null)
        db.projectDao().saveProject(project.copy(hfSourceRepo="",settingsJson=ProjectSettings.write(config),lastRowCursor=0))
        ordinal
    }
    suspend fun copyAsset(ref:String,dest:File,maxBytes:Long):Boolean = withContext(Dispatchers.IO) {
        if(ref.startsWith("https://")) return@withContext hf.downloadImage(ref,dest,maxBytes=maxBytes)
        require(ref.startsWith("content://")){"Source non autorisée"}
        val copyContext=coroutineContext
        context.contentResolver.openInputStream(Uri.parse(ref))?.use { input ->
            com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles.replace(dest) { output ->
                check(com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles.copyBounded(input,output,maxBytes,checkCancelled={copyContext.ensureActive()})>0) { "Image vide" }
            }
        } ?: error("Autorisation SAF perdue; choisissez à nouveau le dossier source")
        true
    }
}
