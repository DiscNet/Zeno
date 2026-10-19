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
    @Test fun centerAnchorStaysCenteredAcrossBreakpoints() {
        val p=DesignProject()
        val shape=DesignElement(x=50f,y=60f,width=120f,height=90f,
            horizontalAnchor=HorizontalAnchor.CENTER)
        p.elements.add(shape)
        ResponsiveLayout.apply(p,1200,800)
        assertEquals((1200f-shape.width)/2f,shape.x,.01f)
        ResponsiveLayout.apply(p,768,1024)
        assertEquals((768f-shape.width)/2f,shape.x,.01f)
    }

    @Test fun appendedScreenSurvivesResizeAndScroll() {
        val p=DesignProject.starter()
        p.appendScreen()
        assertEquals(2,p.screens)
        assertEquals(1688,p.pageHeight)
        val second=DesignElement(kind=ElementKind.TEXT,x=28f,y=960f,
            width=250f,height=64f,text="Segunda tela")
        p.elements.add(second)
        ResponsiveLayout.apply(p,1200,800)
        assertEquals(1600,p.pageHeight)
        assertTrue("second section content stays below first screen",
            second.y>=800f)
        ResponsiveLayout.apply(p,390,844)
        assertEquals(1688,p.pageHeight)
        assertEquals(960f,second.y,.01f)
    }

    @Test fun rightAnchorPreservesRightMargin() {
        val e=DesignElement(x=250f,y=40f,width=100f,height=80f,
            horizontalAnchor=HorizontalAnchor.RIGHT)
        val p=DesignProject(elements=mutableListOf(e))
        ResponsiveLayout.apply(p,768,1024)
        assertEquals(768f-40f-e.width,e.x,.01f)
    }

}
