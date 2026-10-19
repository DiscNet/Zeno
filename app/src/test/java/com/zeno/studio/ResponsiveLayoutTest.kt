package com.zeno.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {
    @Test fun heroBecomesTwoColumnsOnDesktop() {
        val p=DesignProject.starter()
        val title=p.elements[0]
        val art=p.elements.last()
        val initialFont=title.fontSize
        val initialArtWidth=art.width
        ResponsiveLayout.apply(p,1200,800)
        assertEquals(1200,p.pageWidth)
        assertTrue("desktop media must appear to the right of heading",
            art.x>title.x+title.width-1f)
        assertTrue("desktop media grows",art.width>initialArtWidth)
        assertTrue("heading typography grows",title.fontSize>initialFont)
    }

    @Test fun switchingBackPreservesMobileGeometry() {
        val p=DesignProject.starter()
        val original=p.elements.map { it.x to it.y }
        ResponsiveLayout.apply(p,1200,800)
        ResponsiveLayout.apply(p,768,1024)
        ResponsiveLayout.apply(p,390,844)
        p.elements.zip(original).forEach { (e,point) ->
            assertEquals(point.first,e.x,.01f)
            assertEquals(point.second,e.y,.01f)
        }
    }

    @Test fun editsAreKeptForTheirBreakpoint() {
        val p=DesignProject.starter()
        ResponsiveLayout.apply(p,1200,800)
        p.elements[0].x=219f
        ResponsiveLayout.apply(p,390,844)
        ResponsiveLayout.apply(p,1200,800)
        assertEquals(219f,p.elements[0].x,.01f)
    }

    @Test fun lockedImagePreservesIntrinsicRatio() {
        val p=DesignProject()
        val e=DesignElement(kind=ElementKind.IMAGE,x=25f,y=120f,
            width=240f,height=120f,imageRatio=2f,aspectLocked=true)
        p.elements.add(e)
        ResponsiveLayout.apply(p,768,1024)
        assertEquals(2f,e.width/e.height,.01f)
    }
}
