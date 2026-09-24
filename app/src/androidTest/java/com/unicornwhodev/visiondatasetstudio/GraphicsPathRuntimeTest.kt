package com.unicornwhodev.visiondatasetstudio

import android.graphics.Path
import androidx.graphics.path.PathIterator
import androidx.graphics.path.PathSegment
import org.junit.Assert.*
import org.junit.Test

class GraphicsPathRuntimeTest {
    @Test fun nativeConicConversionProducesFiniteQuadratics() {
        val path=Path().apply{addOval(2f,3f,82f,43f,Path.Direction.CW)}
        val raw=PathIterator(path,PathIterator.ConicEvaluation.AsConic).asSequence().toList()
        assertTrue("The ellipse must exercise conic conversion",raw.any{it.type==PathSegment.Type.Conic})
        // AndroidX 1.0.1/1.1.0 calculateSize() leaves converted conics pending
        // in that iterator on API 34+. Keep the counting cursor independent.
        // This test qualifies JNI geometry, not that upstream cursor defect.
        val expectedCount=PathIterator(path,PathIterator.ConicEvaluation.AsQuadratics,.1f).calculateSize()
        val converted=PathIterator(path,PathIterator.ConicEvaluation.AsQuadratics,.1f)
        val segments=converted.asSequence().toList()
        assertEquals(expectedCount,segments.size)
        assertTrue(segments.any{it.type==PathSegment.Type.Quadratic})
        assertFalse(segments.any{it.type==PathSegment.Type.Conic})
        assertTrue(segments.flatMap{it.points.toList()}.all{it.x.isFinite()&&it.y.isFinite()})
        assertEquals(PathSegment.Type.Move,segments.first().type)
        assertEquals(PathSegment.Type.Close,segments.last().type)
        val quadratics=segments.filter{it.type==PathSegment.Type.Quadratic}
        quadratics.zipWithNext().forEach{(left,right)->
            assertEquals(left.points.last().x,right.points.first().x,.001f)
            assertEquals(left.points.last().y,right.points.first().y,.001f)
        }
        // Independent oracle: samples of the converted curve stay on the ellipse.
        quadratics.forEach { segment ->
            for(t in listOf(0f,.25f,.5f,.75f,1f)) {
                val u=1f-t;val (a,b,c)=segment.points
                val x=(u*u*a.x+2*u*t*b.x+t*t*c.x-42f)/40f
                val y=(u*u*a.y+2*u*t*b.y+t*t*c.y-23f)/20f
                assertEquals("Converted curve departed from the ellipse",1f,x*x+y*y,.02f)
            }
        }
    }
}
