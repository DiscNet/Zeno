package com.zeno.studio

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Artboard-responsive layout, borrowing the conceptual Fixed/Fill and row-wrap
 * rules common to editors such as Figcraft. No dependency on Figcraft code.
 *
 * An artboard keeps its own editable geometry snapshot. New breakpoints derive
 * from the smallest existing source so repeated device switches never drift.
 */
object ResponsiveLayout {
    data class Geometry(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val font: Float = 20f
    ) {
        fun toJson() = JSONObject().apply {
            put("x",x.toDouble()); put("y",y.toDouble())
            put("w",w.toDouble()); put("h",h.toDouble())
            put("font",font.toDouble())
        }
        companion object {
            fun fromJson(o:JSONObject) = Geometry(
                o.optDouble("x").toFloat(),o.optDouble("y").toFloat(),
                o.optDouble("w",40.0).toFloat(),o.optDouble("h",40.0).toFloat(),
                o.optDouble("font",20.0).toFloat()
            )
        }
    }

    fun capture(project: DesignProject) {
        project.breakpointLayouts[project.pageWidth] =
            project.elements.associate { it.id to Geometry(
                it.x,it.y,it.width,it.height,it.fontSize) }.toMutableMap()
    }

    private fun computeSingle(elements:List<DesignElement>, originalWidth:Int,
        targetWidth:Int): Map<String,Geometry> {
        if(elements.isEmpty()) return emptyMap()
        if(targetWidth == originalWidth) return elements.associate {
            it.id to Geometry(it.x,it.y,it.width,it.height,it.fontSize)
        }

        val width = targetWidth.toFloat()
        val mobile = targetWidth < 600
        val tablet = targetWidth in 600..1023
        val margin = when {
            mobile -> 22f
            tablet -> 40f
            else -> max(64f, (width-1080f)/2f)
        }
        val usable = width-2f*margin
        val baselineMargin = max(18f, originalWidth*0.065f)
        val baselineUsable = max(100f,originalWidth-2f*baselineMargin)
        val fontScale = if(mobile) 1f else if(tablet) 1.26f else 1.64f
        val gapScale = if(mobile) 1f else if(tablet) 1.22f else 1.55f
        val result = linkedMapOf<String,Geometry>()
        val ordered = elements.sortedWith(compareBy<DesignElement> { it.y }.thenBy { it.x })

        // A hero has heading and call to action above a prominent visual; on
        // wider canvases it becomes two columns instead of a stretched mobile stack.
        val heading = ordered.firstOrNull { it.visible && it.kind == ElementKind.TEXT }
        val media = ordered.firstOrNull {
            it.visible && (it.kind == ElementKind.BOX ||
                it.kind == ElementKind.IMAGE || it.kind == ElementKind.GLASS) &&
                it.width >= baselineUsable*0.50f &&
                (heading == null || it.y > heading.y + 50f)
        }
        val heroText = if(heading != null && media != null && !mobile)
            ordered.filter { it.id != media.id && it.y < media.y &&
                (it.kind == ElementKind.TEXT || it.kind == ElementKind.BUTTON) }
        else emptyList()
        val hero = if(heroText.isNotEmpty() && media != null && !mobile) media else null

        if(hero != null) {
            val gap = if(tablet) 30f else 64f
            val leftWidth = (usable*if(tablet) 0.48f else 0.46f).coerceAtLeast(220f)
            val rightWidth = usable-leftWidth-gap
            val top = if(tablet) 100f else 136f
            var cursor = top
            for(e in heroText) {
                val isText = e.kind == ElementKind.TEXT
                val fs = (e.fontSize*fontScale).coerceIn(12f,88f)
                val w = if(isText) leftWidth else min(leftWidth, max(130f,e.width*fontScale))
                val lineFactor = if(isText && fs>32f) 1.36f else 1f
                val h = if(isText) max(e.height*fontScale*lineFactor,fs*2.15f)
                    else max(e.height,if(tablet) 52f else 59f)
                result[e.id]=Geometry(margin,cursor,w,h,fs)
                cursor+=h+(if(isText) if(tablet) 16f else 23f else 17f)
            }
            val rHeight = if(hero.kind==ElementKind.IMAGE && hero.aspectLocked)
                rightWidth/hero.imageRatio.coerceAtLeast(.05f)
            else max(if(tablet) 310f else 450f,hero.height*1.20f)
            result[hero.id]=Geometry(margin+leftWidth+gap,
                if(tablet) 95f else 123f,rightWidth,rHeight,hero.fontSize)
        }

        // Reflow all non-hero layers into visually scaled rows. Vertical gaps
        // reflect target breakpoints; wide frame-like layers fill their container.
        val others = ordered.filter { it.id !in result }
        val baseLeft = others.minOfOrNull { it.x } ?: baselineMargin
        val baseTop = others.minOfOrNull { it.y } ?: 0f
        val rowCandidates = mutableListOf<MutableList<DesignElement>>()
        for (e in others) {
            val current = rowCandidates.lastOrNull()
            if(current == null || e.y >= current.maxOf { it.y + it.height }-10f)
                rowCandidates.add(mutableListOf(e))
            else current.add(e)
        }
        val heroBottom = result.values.maxOfOrNull { it.y+it.h } ?: 0f
        var nextRowY = if(hero != null) heroBottom+if(tablet) 65f else 88f else
            (if(mobile) 56f else if(tablet) 92f else 124f)
        var previousRowBottom = baseTop
        for(row in rowCandidates) {
            val start = row.minOf { it.y }
            val originalBottom = row.maxOf { it.y+it.height }
            val originalGap = max(0f,start-previousRowBottom)
            nextRowY += originalGap*gapScale
            val sorted = row.sortedBy { it.x }
            val baseSum = sorted.sumOf { it.width.toDouble() }.toFloat()
            val rowHasColumns = sorted.size > 1 && baseSum < usable-20
            val rowSpace = if(rowHasColumns) usable else usable
            val horizontalGap = if(mobile) 12f else 22f
            val rowHeights = mutableListOf<Float>()
            var offsetX = 0f
            var wrapOffset = 0f
            for(e in sorted) {
                val frameLike = e.kind==ElementKind.BOX ||
                    e.kind==ElementKind.IMAGE || e.kind==ElementKind.GLASS
                val isFullWidth = frameLike && e.width >= baselineUsable*.72f
                val ratio = if(frameLike && isFullWidth) (if(mobile) .97f else
                    if(tablet) .92f else .85f) else min(1.4f,max(.80f,
                    usable/baselineUsable))
                val desired = if(isFullWidth) rowSpace*ratio else e.width*ratio
                val w = desired.coerceIn(min(38f,usable),usable)
                if(offsetX>0f && offsetX+w>usable+0.1f) {
                    wrapOffset += (rowHeights.maxOrNull() ?: 50f)+18f
                    rowHeights.clear(); offsetX=0f
                }
                val fs=(e.fontSize*fontScale).coerceIn(9f,88f)
                val h = if(e.kind==ElementKind.IMAGE && e.aspectLocked)
                    w/e.imageRatio.coerceAtLeast(.05f)
                else if(e.kind==ElementKind.TEXT) max(e.height*fontScale,fs*1.65f)
                else if(frameLike && isFullWidth) max(e.height*ratio,if(mobile) 140f
                    else if(tablet) 245f else 355f)
                else max(30f,e.height*min(1.4f,fontScale))
                val x=margin+if(rowHasColumns) offsetX else
                    ((e.x-baseLeft)/baselineUsable*min(usable*.22f,75f))
                result[e.id]=Geometry(x.coerceIn(0f,max(0f,width-w)),
                    nextRowY+wrapOffset,w,h,fs)
                offsetX+=w+horizontalGap
                rowHeights.add(h)
            }
            val rowBottom = row.maxOfOrNull {
                result[it.id]?.let { g->g.y+g.h } ?: nextRowY
            } ?: nextRowY
            nextRowY = rowBottom + if(mobile) 22f else if(tablet) 29f else 36f
            previousRowBottom=originalBottom
        }
        return result
    }

    fun compute(elements:List<DesignElement>,originalWidth:Int,targetWidth:Int,
        sourceHeight:Float=844f,targetHeight:Float=844f,screens:Int=1):Map<String,Geometry> {
        val output=linkedMapOf<String,Geometry>()
        for(section in 0 until screens.coerceAtLeast(1)) {
            val sectionStart=section*sourceHeight
            val local=elements.filter { e ->
                val index=(e.y/sourceHeight).toInt().coerceIn(0,screens-1)
                index==section
            }.map { e->e.copy(y=e.y-sectionStart) }
            if(local.isEmpty())continue
            val generated=computeSingle(local,originalWidth,targetWidth)
            for(e in local) {
                val orig=elements.first { it.id==e.id }
                val candidate=generated[e.id] ?: continue
                var x=candidate.x
                var y=candidate.y
                var w=candidate.w
                var h=candidate.h
                when(orig.horizontalAnchor) {
                    HorizontalAnchor.AUTO -> Unit
                    HorizontalAnchor.LEFT -> x=e.x
                    HorizontalAnchor.CENTER -> x=(targetWidth-w)/2f
                    HorizontalAnchor.RIGHT -> x=targetWidth -
                        (originalWidth-(e.x+e.width))-w
                    HorizontalAnchor.STRETCH -> {
                        val left=e.x.coerceAtLeast(0f)
                        val right=(originalWidth-e.x-e.width).coerceAtLeast(0f)
                        x=left
                        w=max(20f,targetWidth-left-right)
                        if(orig.kind==ElementKind.IMAGE && orig.aspectLocked)
                            h=w/orig.imageRatio.coerceAtLeast(.05f)
                    }
                }
                when(orig.verticalAnchor) {
                    VerticalAnchor.AUTO -> Unit
                    VerticalAnchor.TOP -> y=e.y
                    VerticalAnchor.CENTER -> y=(targetHeight-h)/2f
                    VerticalAnchor.BOTTOM -> y=targetHeight-
                        (sourceHeight-e.y-e.height)-h
                }
                output[e.id]=Geometry(x.coerceIn(0f,max(0f,targetWidth-w)),
                    section*targetHeight+y.coerceAtLeast(0f),w,h,candidate.font)
            }
        }
        return output
    }

    fun apply(project:DesignProject, newW:Int,newH:Int) {
        if(project.pageWidth==newW && project.pageHeight==newH) return
        capture(project)
        val existing=project.breakpointLayouts[newW]
        val map=if(existing!=null) existing else {
            val originalWidth=project.breakpointLayouts.keys.minOrNull() ?: project.pageWidth
            val original=project.breakpointLayouts[originalWidth] ?: emptyMap()
            val reference=project.elements.map { e ->
                original[e.id]?.let { g ->
                    e.copy(x=g.x,y=g.y,width=g.w,height=g.h,fontSize=g.font)
                } ?: e
            }
            compute(reference,originalWidth,newW,
                project.sectionHeight(),newH.toFloat(),project.screens).toMutableMap().also {
                project.breakpointLayouts[newW]=it
            }
        }
        project.pageWidth=newW
        project.pageHeight=newH*project.screens
        project.elements.forEach { e ->
            val g=map[e.id] ?: return@forEach
            e.x=g.x;e.y=g.y;e.width=g.w;e.height=g.h;e.fontSize=g.font
        }
    }

    fun forViewport(project:DesignProject,width:Int):Map<String,Geometry> {
        if(width==project.pageWidth) return project.elements.associate {
            it.id to Geometry(it.x,it.y,it.width,it.height,it.fontSize)
        }
        project.breakpointLayouts[width]?.let { snapshot ->
            // Existing breakpoints keep user corrections for each artboard.
            val targetHeight=when { width<600 -> 844f
                width<1024 -> 1024f else -> 800f }
            val defaults=compute(project.elements,project.pageWidth,width,
                project.sectionHeight(),targetHeight,project.screens)
            return defaults+(snapshot.filterKeys { id ->
                project.elements.any { it.id==id }
            })
        }
        val baseWidth=project.breakpointLayouts.keys.minOrNull() ?: project.pageWidth
        val known=project.breakpointLayouts[baseWidth]
        val base=if(known!=null) project.elements.map {e->
            known[e.id]?.let {g->e.copy(x=g.x,y=g.y,
                width=g.w,height=g.h,fontSize=g.font)} ?: e
        } else project.elements
        val targetHeight=when {
            width<600 -> 844f
            width<1024 -> 1024f
            else -> 800f
        }
        return compute(base,baseWidth,width,project.sectionHeight(),
            targetHeight,project.screens)
    }
}
