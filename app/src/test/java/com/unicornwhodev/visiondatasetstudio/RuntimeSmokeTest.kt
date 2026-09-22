package com.unicornwhodev.visiondatasetstudio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.unicornwhodev.visiondatasetstudio.core.storage.StorageManager
import com.unicornwhodev.visiondatasetstudio.data.hf.HfApiClient
import com.unicornwhodev.visiondatasetstudio.data.json.StudioJson
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExportFormat
import com.unicornwhodev.visiondatasetstudio.domain.export.DatasetExporters
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RuntimeSmokeTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Vision Dataset Studio", appName)
  }

  @Test
  fun `test multi-format export snippet generation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storageManager = StorageManager(context)
    val dummyClient = HfApiClient(tokenProvider = { null })
    val exporters = DatasetExporters(storageManager, dummyClient)

    val project = ProjectEntity(
      classesCsv = "dog,cat,bird",
      hfDestRepo = "user/test-dataset"
    )
    val sample = SampleEntity(
      sampleId = "img_0042",
      projectId = 1L,
      batchNumber = 1,
      assetId = "asset_42",
      sourceRowIndex = 0L,
      sourceFileUrl = null,
      localImagePath = null,
      imageWidth = 800,
      imageHeight = 600,
      acquisitionStatus = "DOWNLOADED",
      annotationStatus = "VALIDATED",
      syncStatus = "LOCAL"
    )
    val annot = SampleAnnotations(
      boxes = listOf(
        BoxTarget("b1", 0.1f, 0.2f, 0.5f, 0.6f, "dog", isHumanVerified = true)
      ),
      captions = listOf(
        CaptionTarget("c1", "A photo of a dog in a park.", language = "en", isHumanVerified = true)
      ),
      vqaList = listOf(
        VqaTarget("v1", "What animal is visible?", "A dog.", isHumanVerified = true)
      ),
      masks = listOf(MaskTarget("m1","dog",4,3,com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec.encode(booleanArrayOf(
        true,true,false,false,true,true,false,false,false,false,false,false)),isHumanVerified=true))
    )

    // 1. Canonical JSON
    val canonicalSnippet = exporters.generatePreviewSnippet(DatasetExportFormat.CANONICAL_JSONL.key, sample, annot, project)
    assertTrue(canonicalSnippet.contains("img_0042"))
    assertTrue(canonicalSnippet.contains("dog"))
    assertTrue(canonicalSnippet.contains("A photo of a dog in a park."))

    // 2. COCO Format
    val cocoSnippet = exporters.generatePreviewSnippet(DatasetExportFormat.COCO.key, sample, annot, project)
    assertTrue(cocoSnippet.contains("bbox_pixels"))
    assertTrue(cocoSnippet.contains("dog"))
    assertTrue(cocoSnippet.contains("\"bbox_pixels\":"))
    assertTrue(cocoSnippet.contains("mask_rle"))

    val cocoFile=java.io.File(context.cacheDir,"coco-export-test.json")
    exporters.exportCocoDetection(project,listOf(sample to annot),cocoFile)
    val coco=StudioJson.moshi.adapter(Map::class.java).fromJson(cocoFile.readText()) as Map<String,Any>
    assertTrue(exporters.validateCocoDocument(coco))
    val rows=coco["annotations"] as List<*>
    assertEquals(2,rows.size)
    assertTrue((rows.last() as Map<*,*>).containsKey("segmentation"))
    val broken=coco.toMutableMap();val brokenRows=rows.map{(it as Map<*,*>).toMutableMap()}.toMutableList()
    val last=brokenRows.last();val segmentation=(last["segmentation"] as Map<*,*>).toMutableMap();segmentation["counts"]=listOf(1,1);last["segmentation"]=segmentation;broken["annotations"]=brokenRows
    assertThrows(IllegalArgumentException::class.java){exporters.validateCocoDocument(broken)}

    val linked=annot.copy(
      boxes=listOf(annot.boxes.single().copy(instanceId="dog-1")),
      masks=listOf(annot.masks.single().copy(instanceId="dog-1")))
    exporters.exportCocoDetection(project,listOf(sample to linked),cocoFile)
    val linkedRows=(StudioJson.moshi.adapter(Map::class.java).fromJson(cocoFile.readText()) as Map<*,*>)["annotations"] as List<*>
    assertEquals(1,linkedRows.size);assertTrue((linkedRows.single() as Map<*,*>).containsKey("segmentation"))
    val distinct=linked.copy(masks=listOf(linked.masks.single().copy(instanceId="dog-2")))
    exporters.exportCocoDetection(project,listOf(sample to distinct),cocoFile)
    assertEquals(2,((StudioJson.moshi.adapter(Map::class.java).fromJson(cocoFile.readText()) as Map<*,*>)["annotations"] as List<*>).size)
    val ambiguous=linked.copy(masks=linked.masks+linked.masks.single().copy(id="m2"))
    assertThrows(IllegalArgumentException::class.java){exporters.exportCocoDetection(project,listOf(sample to ambiguous),cocoFile)}
    cocoFile.delete()

    // 3. YOLO Format
    val yoloSnippet = exporters.generatePreviewSnippet(DatasetExportFormat.YOLO.key, sample, annot, project)
    val fields = yoloSnippet.trim().split(Regex("\\s+"))
    assertEquals("0", fields[0])
    assertEquals(0.3f, fields[1].toFloat(), 0.0001f)
    assertEquals(0.4f, fields[2].toFloat(), 0.0001f)
    assertFalse(yoloSnippet.contains("#"))

    // 4. Vision-Language Format
    val vlSnippet = exporters.generatePreviewSnippet(DatasetExportFormat.VISION_LANGUAGE.key, sample, annot, project)
    assertTrue(vlSnippet.contains("messages"))
    assertTrue(vlSnippet.contains("What animal is visible?"))
    assertTrue(vlSnippet.contains("A dog."))
  }
}
