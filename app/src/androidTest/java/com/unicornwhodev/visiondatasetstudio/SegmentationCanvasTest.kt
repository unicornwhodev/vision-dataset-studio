package com.unicornwhodev.visiondatasetstudio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.unicornwhodev.visiondatasetstudio.core.geometry.ViewPoint
import com.unicornwhodev.visiondatasetstudio.data.model.*
import com.unicornwhodev.visiondatasetstudio.domain.inference.MaskCodec
import com.unicornwhodev.visiondatasetstudio.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SegmentationCanvasTest {
    @get:Rule val rule=createComposeRule()
    @Test fun separateMasksCanBePaintedAndSamPromptDoesNotBecomeAnAnnotation() {
        var annotations by mutableStateOf(SampleAnnotations())
        var selected by mutableStateOf<String?>(null)
        var tool by mutableStateOf(EditorTool.MASK)
        var prompt by mutableStateOf<ViewPoint?>(null)
        val sample=SampleEntity("canvas-qa",batchNumber=1,assetId="canvas",sourceRowIndex=0,sourceFileUrl=null,
            localImagePath=null,imageWidth=256,imageHeight=256,acquisitionStatus="AVAILABLE",annotationStatus="IN_PROGRESS",syncStatus="NOT_EXPORTED")
        rule.setContent {
            MaterialTheme { Box(Modifier.size(256.dp)) {
                InteractiveAnnotationCanvas(sample,annotations,tool,"object",selected,1f,Offset.Zero,{_,_->},
                    {selected=it},{annotations=it},promptPoint=prompt,onPromptSelected={prompt=it})
            } }
        }
        fun tap(x:Float,y:Float) {
            val node=rule.onNodeWithContentDescription(com.unicornwhodev.visiondatasetstudio.core.i18n.tr("Image à annoter.","Image to annotate."),substring=true)
            val size=node.fetchSemanticsNode().boundsInRoot.size
            node.performTouchInput { click(Offset(size.width*x,size.height*y)) }
            rule.waitForIdle()
        }
        tap(.25f,.25f)
        rule.waitUntil(5_000) { annotations.masks.isNotEmpty() }
        rule.runOnIdle { assertEquals(1,annotations.masks.size);selected=null }
        tap(.75f,.75f)
        rule.waitUntil(5_000) { annotations.masks.size==2 }
        lateinit var before:SampleAnnotations
        rule.runOnIdle {
            assertEquals(2,annotations.masks.size)
            assertTrue(annotations.masks.all { it.isHumanVerified && MaskCodec.decode(it).any { p->p } })
            before=annotations;tool=EditorTool.SAM_POINT
        }
        tap(.5f,.5f)
        rule.waitUntil(5_000) { prompt!=null }
        rule.runOnIdle {
            assertNotNull(prompt);assertEquals(.5f,prompt!!.x,.01f);assertEquals(.5f,prompt!!.y,.01f)
            assertEquals("SAM hint must never enter exported human annotations",before,annotations)
        }
    }
}
