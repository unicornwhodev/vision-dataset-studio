package com.unicornwhodev.visiondatasetstudio.domain.export

import com.unicornwhodev.visiondatasetstudio.core.geometry.HashUtils
import com.unicornwhodev.visiondatasetstudio.core.geometry.NormalizedRect
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.unicornwhodev.visiondatasetstudio.core.storage.DurableFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class DatasetExportFormat(val key: String, val label: String) {
    CANONICAL_JSONL("CANONICAL_JSON", "JSONL complet"), COCO("COCO", "COCO — boîtes + masques"),
    YOLO("YOLO", "YOLO — boîtes"), VISION_LANGUAGE("VL", "Questions / réponses")
}

/** Canonical annotations are always retained; projections never silently replace unknown labels. */
class DatasetExporters(private val storageManager: StorageManager, private val hfApiClient: HfApiClient) {
    private val moshi = com.unicornwhodev.visiondatasetstudio.data.json.StudioJson.moshi
    private val sampleAdapter = moshi.adapter(CanonicalDatasetSample::class.java)
    private fun json(value: Any) = moshi.adapter(Any::class.java).toJson(value)
    private fun classes(project: ProjectEntity) = project.classesCsv.split(',').map(String::trim).filter(String::isNotBlank).distinct()
    private fun imageName(s: SampleEntity) = "${s.sampleId}.${s.localImagePath?.let(::File)?.extension ?: "jpg"}"
    data class ExportPackageResult(val success: Boolean, val outputDirectory: File, val generatedFiles: List<Pair<String, File>>,
                                  val sampleCount: Int, val zipArchive: File? = null, val error: String? = null)
    data class LocalZipExportResult(val success: Boolean, val zipFile: File?, val sampleCount: Int, val fileSizeBytes: Long, val error: String? = null)

    suspend fun packageBatchForHf(project: ProjectEntity, batchNumber: Int,
        validatedSamplesWithAnnotations: List<Pair<SampleEntity, SampleAnnotations>>,
        includeWebDataset: Boolean = true, includeJsonl: Boolean = true, includeCoco: Boolean = true,
        includeYolo: Boolean = true, includeVl: Boolean = true): ExportPackageResult = withContext(Dispatchers.IO) {
        val finalRoot = storageManager.batchExportDir(project.id, batchNumber)
        val root=File(finalRoot.parentFile,finalRoot.name+".building")
        try {
            val pairs = validatedSamplesWithAnnotations
            require(pairs.isNotEmpty()) { "Aucun cas validé à exporter" }
            require(pairs.map { it.first.sampleId }.distinct().size == pairs.size)
            val split = project.targetSplit.ifBlank { "train" }
            require(split.matches(Regex("[a-zA-Z0-9_-]+"))) { "Nom de split invalide" }
            pairs.forEach { (s, _) ->
                require(s.annotationStatus == "VALIDATED") { "Un cas non validé est présent" }
                require(s.imageWidth > 0 && s.imageHeight > 0 && s.localImagePath?.let { File(it).isFile } == true) { "Image locale absente ou invalide" }
                require(HashUtils.computeSha256(File(s.localImagePath!!))==s.sha256) { "Image modifiée après acquisition : ${s.sampleId}" }
            }
            val imageBytes = pairs.sumOf { File(it.first.localImagePath!!).length() }
            // Reserve space for the package, optional TAR and ZIP before writing; never purge originals to make room.
            require(storageManager.hasAvailableBudget(imageBytes * (if (includeWebDataset) 4 else 2) + 8L * 1024 * 1024, project.diskBudgetMb, com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.read(project).reserveFreeMb)) {
                "Espace insuffisant pour images + archive. Augmentez le budget disque ou désactivez WebDataset."
            }
            if (root.exists()) check(root.deleteRecursively()) { "Ancien export inaccessible" }
            check(root.mkdirs())
            val batchId = finalRoot.name
            val prefix = "batches/$batchId"
            val dir = File(root, prefix).apply { mkdirs() }
            val imageDir = File(dir, "images").apply { mkdirs() }
            val exportContext=coroutineContext
            pairs.forEach { (s, _) ->
                exportContext.ensureActive()
                val source=storageManager.ownedImage(s.localImagePath!!)
                DurableFiles.replace(File(imageDir,imageName(s))) { output -> source.inputStream().use { input ->
                    DurableFiles.copyBounded(input,output,source.length(),checkCancelled={exportContext.ensureActive()})
                } }
            }
            // Complete annotation record mandatory, even when the user only requests a lossy training projection.
            File(dir, "annotations.jsonl").bufferedWriter().use { w -> pairs.forEach { (s, a) -> w.appendLine(sampleAdapter.toJson(toCanonical(s, a, project))) } }
            if (includeWebDataset) {
                val tar = File(root, "data/$split/$batchId.tar").apply { parentFile?.mkdirs() }
                WebDatasetTarWriter(tar).use { out -> pairs.forEach { (s, a) ->
                    out.addFile(imageName(s), File(s.localImagePath!!))
                    out.addBytes("${s.sampleId}.json", sampleAdapter.toJson(toCanonical(s, a, project)).toByteArray(Charsets.UTF_8))
                } }
            }
            if (includeCoco) exportCocoDetection(project, pairs, File(dir, "coco.json"))
            if (includeYolo) exportYoloDetection(project, pairs, dir)
            if (includeVl) exportVlDataset(pairs, File(dir, "vqa.jsonl"))
            File(dir, "labels.json").writeText(json(classes(project)))
            File(dir, "captions-tags.jsonl").bufferedWriter().use { out -> pairs.forEach { (s, a) ->
                out.appendLine(json(mapOf("id" to s.sampleId, "image" to "images/${imageName(s)}", "captions" to a.captions.map { mapOf("text" to it.text, "language" to it.language, "detailed" to it.isDetailed) }, "tags" to a.tags.map { it.label })))
            } }
            File(dir, "README.md").writeText("""
                # ${project.name.replace("\n", " ")}: $batchId
                Source: ${project.hfSourceRepo.ifBlank { "local-index:project-${project.id}" }}; config: ${project.sourceConfig}; split: ${project.sourceSplit}.
                Output split: $split. Human-validated cases in this batch: ${pairs.size}.
                Source revision: ${com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.read(project).resolvedSourceRevision ?: "UNRESOLVED (Viewer/local source)"}. External image URLs are not pinned by a Hub manifest revision.
                Images are in `images/`; full annotations are in `annotations.jsonl`.
                JSON coordinates are normalized [0, 1]. COCO uses pixels; YOLO uses normalized center/width/height.
                VQA exports only explicit question/answer pairs; no synthetic response is invented for other tasks.
                Captions, tags, points, grounding, counts and abstentions remain in the canonical record.
                Review upstream licences and redistribution rights before publishing. No licence is granted by this export.
                This file documents one batch, not the complete destination repository. Configure the root dataset card separately.
            """.trimIndent())
            val manifest = mapOf("schema_version" to 2, "batch_number" to batchNumber, "split" to split,
                "source" to project.hfSourceRepo.ifBlank { "local-index:project-${project.id}" }, "source_revision_resolved" to (com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.read(project).resolvedSourceRevision != null), "sample_count" to pairs.size,
                "files" to root.walkTopDown().filter { it.isFile }.map { f -> mapOf("path" to f.relativeTo(root).invariantSeparatorsPath, "size" to f.length(), "sha256" to HashUtils.computeSha256(f)) }.toList())
            File(dir, "manifest.json").writeText(json(manifest))
            check(verifyPreparedPackage(root)) { "Paquet local incomplet ou incohérent" }
            DurableFiles.replaceDirectory(root,finalRoot)
            ExportPackageResult(true, finalRoot, finalRoot.walkTopDown().filter { it.isFile }.map { it.relativeTo(finalRoot).invariantSeparatorsPath to it }.toList(), pairs.size)
        } catch (e: CancellationException) { root.deleteRecursively(); throw e } catch (e: Exception) { root.deleteRecursively(); ExportPackageResult(false, finalRoot, emptyList(), 0, error = e.message ?: "Échec de préparation") }
    }

    /** Verify every listed payload and reject unlisted or missing files. A partial directory is never upload-ready. */
    fun verifyPreparedPackage(root:File):Boolean = runCatching {
        require(root.isDirectory)
        val manifests=root.walkTopDown().filter{it.isFile && it.name=="manifest.json"}.toList();require(manifests.size==1)
        val manifest=manifests.single();require(manifest.length()<=16L*1024*1024)
        val parsed=moshi.adapter(Map::class.java).fromJson(manifest.readText()) ?: error("Manifest absent")
        val files=parsed["files"] as? List<*> ?: error("Liste des fichiers absente")
        val names=mutableSetOf<String>()
        for(entry in files) {
            val row=entry as? Map<*,*> ?: error("Entrée invalide");val name=row["path"] as? String ?: error("Chemin absent")
            require(com.unicornwhodev.visiondatasetstudio.core.workflow.ProcessingSettings.safeRelativePath(name) && names.add(name))
            val file=File(root,name);require(file.canonicalPath.startsWith(root.canonicalPath+File.separator) && file.isFile)
            require(file.length()==(row["size"] as Number).toLong() && HashUtils.computeSha256(file)==row["sha256"])
        }
        val actual=root.walkTopDown().filter{it.isFile && it!=manifest}.map{it.relativeTo(root).invariantSeparatorsPath}.toSet()
        require(actual==names && names.isNotEmpty());true
    }.getOrDefault(false)

    suspend fun packageBatchToLocalZip(project: ProjectEntity, batchNumber: Int,
        validatedSamplesWithAnnotations: List<Pair<SampleEntity, SampleAnnotations>>,
        includeWebDataset: Boolean = true, includeJsonl: Boolean = true, includeCoco: Boolean = true,
        includeYolo: Boolean = true, includeVl: Boolean = true): LocalZipExportResult = withContext(Dispatchers.IO) {
        val p = packageBatchForHf(project, batchNumber, validatedSamplesWithAnnotations, includeWebDataset, includeJsonl, includeCoco, includeYolo, includeVl)
        if (!p.success) return@withContext LocalZipExportResult(false, null, 0, 0, p.error)
        val zip = File(storageManager.exportsDir, "${p.outputDirectory.name}.zip")
        
        try {
            check(storageManager.hasAvailableBudget(p.generatedFiles.sumOf { it.second.length() } + 1048576L, project.diskBudgetMb, com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.read(project).reserveFreeMb)) { "Budget insuffisant pour la copie ZIP; paquet préparé conservé" }
            val exportContext=coroutineContext
            DurableFiles.replace(zip) { raw ->
                // finish(), not close(): DurableFiles owns flush/fsync/close of the underlying file.
                val out=ZipOutputStream(raw)
                p.generatedFiles.forEach { (path,f) ->
                    exportContext.ensureActive();out.putNextEntry(ZipEntry(path))
                    f.inputStream().use { DurableFiles.copyBounded(it,out,maxOf(1L,f.length()),checkCancelled={exportContext.ensureActive()}) }
                    out.closeEntry()
                }
                out.finish();out.flush()
            }
            LocalZipExportResult(true, zip, p.sampleCount, zip.length())
        } catch (e: CancellationException) { throw e } catch (e: Exception) { LocalZipExportResult(false, null, 0, 0, e.message) }
    }

    fun exportCocoDetection(project: ProjectEntity, samples: List<Pair<SampleEntity, SampleAnnotations>>, outputFile: File) {
        require(samples.map{it.first.sampleId}.distinct().size==samples.size){"Identifiants d’images COCO dupliqués"}
        val labels = classes(project); var next = 0
        val annotations = samples.flatMapIndexed { index, (_, a) -> a.boxes.map { b ->
            val s = samples[index].first; val c = labels.indexOf(b.label); require(c >= 0) { "Classe COCO inconnue: ${b.label}" }
            val bbox = NormalizedRect(b.xmin, b.ymin, b.xmax, b.ymax).toCocoPx(s.imageWidth, s.imageHeight)
            mapOf("id" to ++next, "image_id" to index + 1, "category_id" to c + 1, "bbox" to bbox.toList(), "area" to bbox[2] * bbox[3], "iscrowd" to 0)
        } + a.masks.mapNotNull { m ->
            val sample=samples[index].first;val category=labels.indexOf(m.label);require(category>=0) { "Classe COCO inconnue: ${m.label}" }
            val projection=com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec.projectCoco(m,sample.imageWidth,sample.imageHeight)
            if(projection.area==0) null else mapOf("id" to ++next,"image_id" to index+1,"category_id" to category+1,"bbox" to projection.bbox,"area" to projection.area,"iscrowd" to 0,
                "segmentation" to projection.segmentation)
        } }
        val document=mapOf<String,Any>("images" to samples.mapIndexed { i, (s, _) -> mapOf("id" to i + 1, "file_name" to "images/${imageName(s)}", "width" to s.imageWidth, "height" to s.imageHeight) },
            "categories" to labels.mapIndexed { i, label -> mapOf("id" to i + 1, "name" to label) }, "annotations" to annotations)
        validateCocoDocument(document)
        DurableFiles.replace(outputFile){out->out.write(json(document).toByteArray(Charsets.UTF_8))}
    }
    /** Independent structural validation before an export can enter a package manifest. */
    internal fun validateCocoDocument(document:Map<String,Any>):Boolean {
        val images=(document["images"] as? List<*>)?.map{it as? Map<*,*> ?: error("Image COCO invalide")} ?: error("Images COCO absentes")
        val categories=(document["categories"] as? List<*>)?.map{it as? Map<*,*> ?: error("Catégorie COCO invalide")} ?: error("Catégories COCO absentes")
        val annotations=(document["annotations"] as? List<*>)?.map{it as? Map<*,*> ?: error("Annotation COCO invalide")} ?: error("Annotations COCO absentes")
        fun uniqueIds(rows:List<Map<*,*>>,kind:String)=rows.map{(it["id"] as? Number)?.toInt() ?: error("ID $kind absent")}.also{require(it.size==it.distinct().size){"ID $kind dupliqué"}}
        val imageIds=uniqueIds(images,"image").toSet();val categoryIds=uniqueIds(categories,"catégorie").toSet();uniqueIds(annotations,"annotation")
        val dimensions=images.associate{row->
            val id=(row["id"] as Number).toInt();val width=(row["width"] as? Number)?.toInt() ?: 0;val height=(row["height"] as? Number)?.toInt() ?: 0
            require(width>0&&height>0&&(row["file_name"] as? String).orEmpty().isNotBlank());id to (width to height)
        }
        categories.forEach{require((it["name"] as? String).orEmpty().isNotBlank())}
        annotations.forEach{row->
            val imageId=(row["image_id"] as? Number)?.toInt() ?: error("image_id absent");require(imageId in imageIds)
            val categoryId=(row["category_id"] as? Number)?.toInt() ?: error("category_id absent");require(categoryId in categoryIds)
            val isCrowd=(row["iscrowd"] as? Number)?.toInt() ?: error("iscrowd absent");require(isCrowd in 0..1){"iscrowd COCO invalide"}
            val (width,height)=dimensions.getValue(imageId);val bbox=(row["bbox"] as? List<*>)?.map{(it as Number).toDouble()} ?: error("bbox absente")
            require(bbox.size==4&&bbox.all{it.isFinite()}&&bbox[0]>=0&&bbox[1]>=0&&bbox[2]>0&&bbox[3]>0&&bbox[0]+bbox[2]<=width+1e-6&&bbox[1]+bbox[3]<=height+1e-6)
            val area=(row["area"] as? Number)?.toDouble() ?: 0.0;require(area>0&&area<=width.toDouble()*height)
            row["segmentation"]?.let{raw->
                val segmentation=raw as? Map<*,*> ?: error("Segmentation COCO invalide")
                require((segmentation["size"] as? List<*>)?.map{(it as Number).toInt()}==listOf(height,width))
                val counts=(segmentation["counts"] as? List<*>)?.map{(it as Number).toLong()} ?: error("RLE COCO absente")
                require(counts.isNotEmpty()&&counts.all{it>=0}&&counts.sum()==width.toLong()*height)
                require(counts.withIndex().filter{it.index%2==1}.sumOf{it.value}==area.toLong())
            }
        }
        return true
    }
    fun exportYoloDetection(project: ProjectEntity, samples: List<Pair<SampleEntity, SampleAnnotations>>, outputDir: File) {
        val labels = classes(project); val dir = File(outputDir, "labels").apply { mkdirs() }
        samples.forEach { (s, a) -> File(dir, "${s.sampleId}.txt").writeText(yolo(a, labels)) }
        // Paths are relative to dataset.yaml. This is one split, not an invented train/val partition.
        File(outputDir, "dataset.yaml").writeText("# Set path to this extracted batch directory in your trainer.\n${project.targetSplit.ifBlank { "train" }}: images\nnames: ${json(labels)}\n")
    }
    private fun yolo(a: SampleAnnotations, labels: List<String>) = a.boxes.joinToString("\n") { b ->
        val index = labels.indexOf(b.label); require(index >= 0) { "Classe YOLO inconnue: ${b.label}" }
        val xywh = NormalizedRect(b.xmin, b.ymin, b.xmax, b.ymax).toYolo()
        String.format(Locale.US, "%d %.8f %.8f %.8f %.8f", index, xywh[0], xywh[1], xywh[2], xywh[3])
    }
    fun exportVlDataset(samples: List<Pair<SampleEntity, SampleAnnotations>>, outputFile: File) {
        outputFile.bufferedWriter().use { out -> samples.forEach { (s, a) -> a.vqaList.forEach { q -> out.appendLine(json(vqaRow(s, q))) } } }
    }
    private fun vqaRow(s: SampleEntity, q: VqaTarget) = mapOf("id" to "${s.sampleId}:${q.id}", "image" to "images/${imageName(s)}",
        "question" to q.question, "answer" to if (q.isAbstained) null else q.answer, "abstained" to q.isAbstained, "target_ids" to q.targetIds,
        "messages" to if (q.isAbstained) emptyList() else listOf(mapOf("role" to "user", "content" to q.question), mapOf("role" to "assistant", "content" to q.answer)))

    fun generatePreviewSnippet(format: String, sample: SampleEntity, annot: SampleAnnotations, project: ProjectEntity): String = try {
        when (format) {
            "YOLO" -> yolo(annot, classes(project)).ifBlank { "# Pas de boîte : vérifier l’absence explicitement avant export." }
            "COCO" -> json(mapOf("image" to imageName(sample), "bbox_pixels" to annot.boxes.map { b -> mapOf("label" to b.label, "bbox" to NormalizedRect(b.xmin, b.ymin, b.xmax, b.ymax).toCocoPx(sample.imageWidth, sample.imageHeight).toList()) },
                "mask_rle" to annot.masks.mapNotNull{m->MaskCodec.projectCoco(m,sample.imageWidth,sample.imageHeight).takeIf{it.area>0}?.let{mapOf("label" to m.label,"bbox" to it.bbox,"area" to it.area,"segmentation" to it.segmentation)}}))
            "VL" -> annot.vqaList.joinToString("\n") { json(vqaRow(sample, it)) }.ifBlank { "Aucune question / réponse. Les autres annotations restent dans le JSONL complet." }
            else -> sampleAdapter.indent("  ").toJson(toCanonical(sample, annot, project))
        }
    } catch (e: Exception) { "Aperçu non disponible : ${e.message}" }

    private fun toCanonical(s: SampleEntity, a: SampleAnnotations, p: ProjectEntity) = CanonicalDatasetSample(
        sample_id = s.sampleId, asset_id = s.assetId, dataset_source = p.hfSourceRepo.ifBlank { "local-index:project-${p.id}" }, source_revision = com.unicornwhodev.visiondatasetstudio.data.preferences.ProjectSettings.read(p).resolvedSourceRevision ?: "UNRESOLVED",
        source_config = p.sourceConfig, source_split = p.sourceSplit, source_row_index = s.sourceRowIndex, group_id = s.groupId, split = s.split,
        media = CanonicalMediaInfo(imageName(s), s.imageWidth, s.imageHeight, when (File(s.localImagePath ?: "image.jpg").extension.lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" },
            s.sha256 ?: "", s.phash?.toString(16), null, s.sourceSha256, s.imageTransform), // Never publish a temporary signed/private URL.
        annotations = a, review_status = s.annotationStatus,
        audit = CanonicalAuditInfo(s.createdAt, s.updatedAt, "local_curator", a.masks.any { it.sourceProvenance != "human" } || a.boxes.any { it.sourceProvenance != "human" } || a.points.any { it.sourceProvenance != "human" } || a.tags.any { it.sourceProvenance != "human" }))
}
