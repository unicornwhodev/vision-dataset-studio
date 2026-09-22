package com.unicornwhodev.visiondatasetstudio.data.source

import com.unicornwhodev.visiondatasetstudio.core.i18n.tr
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
            check(settings.sourceIndexReady) { tr("Importez/indexez la source avant de préparer un lot", "Import/index the source before preparing a batch") }
            val result=mutableListOf<SourceEntryEntity>();var chars=0L
            while(result.size<count) {
                val rows=db.sourceEntryDao().page(project.id,offset+result.size,minOf(16,count-result.size))
                if(rows.isEmpty())break
                for(row in rows) {
                    val cost=row.assetId.length.toLong()+row.imageRef.length+(row.annotationJson?.length ?: 0)
                    require(cost<=4L*1024*1024) { tr("Métadonnées d’un cas trop volumineuses", "Sample metadata too large") }
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
            check(response.success) { response.error ?: tr("Lecture HF impossible", "Cannot read HF source") }
            for((n,row) in response.rows.withIndex()) {
                check(project.imageColumn !in row.truncatedCells) { tr("Cellule image tronquée : ligne ${row.rowIdx}", "Truncated image cell: row ${row.rowIdx}") }
                val raw=row.rowData[project.imageColumn]
                val ref=when(raw){is Map<*,*>->raw["src"] as? String ?: raw["url"] as? String;is String->raw;else->null}
                check(ref?.startsWith("https://")==true) { tr("Colonne ${project.imageColumn} sans URL HTTPS exploitable. Utilisez une source manifeste.", "Column ${project.imageColumn} has no usable HTTPS URL. Use a manifest source.") }
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
        return annotationAdapter.toJson(ProposalMerger.draft(a ?: error(tr("Annotations canoniques invalides", "Invalid canonical annotations"))))
    }
    private suspend fun beginIndex(project:ProjectEntity) {
        check(db.batchDao().getLatestBatchSync(project.id)==null) { tr("Source verrouillée : créez un autre projet pour changer de corpus", "Source locked: create another project to change the corpus") }
        val config=ProjectSettings.read(project)
        db.projectDao().saveProject(project.copy(settingsJson=ProjectSettings.write(config.copy(sourceIndexReady=false))))
        db.sourceEntryDao().clear(project.id)
    }
    suspend fun importManifest(project:ProjectEntity,uri:Uri,tree:Uri?=null):Long = withContext(Dispatchers.IO) {
        val stream=context.contentResolver.openInputStream(uri) ?: error(tr("Manifeste inaccessible", "Manifest inaccessible"))
        stream.use { input ->
            beginIndex(project)
            val count=readManifest(project,input) { ref ->
                when { ref.startsWith("https://")->ref
                    tree!=null->resolveChild(tree,ref).toString()
                    else->error(tr("Les chemins relatifs exigent un dossier source choisi. Les URL HTTPS peuvent être importées seules.", "Relative paths require a selected source folder. HTTPS URLs can be imported independently.")) }
            }
            val settings=ProjectSettings.read(project).copy(sourceMode="LOCAL_INDEX",sourceIndexReady=true,localTreeUri=tree?.toString(),localSourceLabel=tr("Manifeste JSONL local", "Local JSONL manifest"),resolvedSourceRevision=null)
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
            check(limit>0){tr("Budget insuffisant pour le manifeste", "Insufficient budget for the manifest")}
            check(hf.downloadImage(uri,tmp,maxBytes=limit)) { tr("Manifeste HF inaccessible ou supérieur à 128 Mo", "HF manifest inaccessible or larger than 128 MB") }
            val parent=settings.manifestPath.substringBeforeLast('/',"")
            val count=tmp.inputStream().use { input->readManifest(project,input) { ref->
                if(ref.startsWith("https://")) ref else {
                    require(ProcessingSettings.safeRelativePath(ref)) { tr("Chemin image invalide", "Invalid image path") }
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
            total+=line.length;check(total<=128L*1024*1024) { tr("Manifeste trop volumineux", "Manifest too large") }
            if(line.isBlank()) continue
            val data=moshi.adapter(Map::class.java).fromJson(line) ?: error(tr("JSONL invalide à l’entrée $ordinal", "Invalid JSONL at entry $ordinal"))
            val media=data["media"] as? Map<*,*>
            val ref=(data[project.imageColumn] as? String) ?: (data["file_name"] as? String) ?: (data["image_url"] as? String) ?: (media?.get("filename") as? String) ?: error(tr("Image absente à l’entrée $ordinal", "Image missing at entry $ordinal"))
            val id=SourceIdentity.string(data[project.idColumn] ?: data["sample_id"]) ?: ref
            val a=if(settings.importAnnotations) data["annotations"]?.let{annotationJson(it)} else null
            rows.add(SourceEntryEntity(project.id,ordinal,id,resolve(ref),ordinal++,SourceIdentity.string(data["group_id"]),a))
            if(rows.size==256) { db.sourceEntryDao().insert(rows.toList());rows.clear();checkIndexBudget(project) }
        }
        if(rows.isNotEmpty()) db.sourceEntryDao().insert(rows)
        checkIndexBudget(project)
        check(ordinal>0) { tr("Manifeste vide", "Empty manifest") };return ordinal
    }
    private fun checkIndexBudget(p:ProjectEntity) {
        check(com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager(context).getUsedSpaceBytes()<p.diskBudgetMb*1024*1024) { tr("Budget de métadonnées atteint", "Metadata budget reached") }
        check(context.filesDir.usableSpace>=ProjectSettings.read(p).reserveFreeMb*1024L*1024) { tr("Réserve disque atteinte", "Storage reserve reached") }
    }
    private fun boundedLine(reader:BufferedReader,max:Int):String? {
        val b=StringBuilder()
        while(true){val c=reader.read();if(c<0)return if(b.isEmpty()) null else b.toString();if(c==10)return b.toString();if(c!=13)b.append(c.toChar());check(b.length<=max){tr("Ligne JSONL supérieure à 1 Mo", "JSONL line exceeds 1 MB")}}
    }
    private data class Child(val id:String,val name:String,val mime:String)
    private fun findChild(tree:Uri,parentId:String,name:String):String {
        val uri=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId)
        var match:String?=null
        context.contentResolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { cursor ->
            while(cursor.moveToNext()) if(cursor.getString(1)==name) {
                check(match==null){tr("Chemin local ambigu", "Ambiguous local path")};match=cursor.getString(0)
            }
        } ?: error(tr("Dossier inaccessible", "Folder inaccessible"))
        return match ?: error(tr("Fichier local absent: $name", "Local file missing: $name"))
    }
    private suspend fun streamChildren(tree:Uri,parentId:String,visit:suspend (Child)->Unit) {
        val uri=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId)
        val cursor=context.contentResolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)
            ?: error(tr("Dossier inaccessible", "Folder inaccessible"))
        cursor.use { while(it.moveToNext()) { coroutineContext.ensureActive();visit(Child(it.getString(0),it.getString(1),it.getString(2))) } }
    }
    private fun resolveChild(tree:Uri,path:String):Uri {
        require(ProcessingSettings.safeRelativePath(path)) { tr("Chemin local invalide", "Invalid local path") }
        var parent=DocumentsContract.getTreeDocumentId(tree)
        for(part in path.split('/')) { parent=findChild(tree,parent,part) }
        return DocumentsContract.buildDocumentUriUsingTree(tree,parent)
    }
    suspend fun importFolder(project:ProjectEntity,tree:Uri):Long = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        beginIndex(project)
        var ordinal=0L;val rows=mutableListOf<SourceEntryEntity>();val visited=mutableSetOf<String>()
        suspend fun walk(parent:String,prefix:String,depth:Int) {
            require(depth<=32){tr("Arborescence trop profonde", "Folder tree too deep")};check(visited.add(parent)){tr("Boucle de dossiers", "Folder cycle")}
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
        check(ordinal>0){tr("Aucune image JPEG/PNG/WebP dans ce dossier", "No JPEG/PNG/WebP images in this folder")}
        val config=ProjectSettings.read(project).copy(sourceMode="LOCAL_INDEX",sourceIndexReady=true,localTreeUri=tree.toString(),localSourceLabel=tr("Dossier local · $ordinal images", "Local folder · $ordinal images"),resolvedSourceRevision=null)
        db.projectDao().saveProject(project.copy(hfSourceRepo="",settingsJson=ProjectSettings.write(config),lastRowCursor=0))
        ordinal
    }
    suspend fun copyAsset(ref:String,dest:File,maxBytes:Long):Boolean = withContext(Dispatchers.IO) {
        if(ref.startsWith("https://")) return@withContext hf.downloadImage(ref,dest,maxBytes=maxBytes)
        require(ref.startsWith("content://")){tr("Source non autorisée", "Source not authorized")}
        val copyContext=coroutineContext
        context.contentResolver.openInputStream(Uri.parse(ref))?.use { input ->
            com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles.replace(dest) { output ->
                check(com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles.copyBounded(input,output,maxBytes,checkCancelled={copyContext.ensureActive()})>0) { tr("Image vide", "Empty image") }
            }
        } ?: error(tr("Autorisation SAF perdue; choisissez à nouveau le dossier source", "SAF permission lost; select the source folder again"))
        true
    }
}
