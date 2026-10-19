package com.zeno.studio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Responsive reflow from any working artboard. Keeps component sizes readable,
 * centers the content group and stacks horizontal rows that cannot fit.
 * Coordinates are recalculated, not simply scaled by the screen width.
 */
object ResponsiveLayout {
    data class Geometry(val x: Float, val y: Float, val w: Float, val h: Float)
    fun compute(elements: List<DesignElement>, oldWidth: Int, newWidth: Int): Map<String, Geometry> {
        if (elements.isEmpty()) return emptyMap()
        val margin = if (newWidth <= 480) 22f else 32f
        val available = max(100f, newWidth - margin * 2f)
        val left = elements.minOf { it.x }
        val right = elements.maxOf { it.x + it.width }
        val groupW = right - left
        val output = linkedMapOf<String, Geometry>()

        // Components with a shared vertical band form a row. They are stacked
        // when their total natural width cannot be displayed on a small frame.
        val sorted = elements.sortedWith(compareBy<DesignElement> { it.y }.thenBy { it.x })
        val rows = mutableListOf<MutableList<DesignElement>>()
        for (e in sorted) {
            val row = rows.lastOrNull()
            if (row == null || e.y > row.minOf { it.y + it.height } - 8f) {
                rows.add(mutableListOf(e))
            } else row.add(e)
        }
        var extraY = 0f
        val totalWidth = min(groupW, available)
        val newGroupLeft = (newWidth - totalWidth) / 2f
        for (row in rows) {
            val rowStart = row.minOf { it.x }
            val rowEnd = row.maxOf { it.x + it.width }
            val rowW = rowEnd - rowStart
            val shouldStack = row.size > 1 && rowW > available
            val originalTop = row.minOf { it.y }
            var stackedY = originalTop + extraY
            val previousExtra = extraY
            if (shouldStack) {
                val gap = 12f
                val items = row.sortedBy { it.x }
                for (e in items) {
                    val width = min(e.width, available)
                    val height = if (e.kind == ElementKind.IMAGE && e.aspectLocked)
                        width / e.imageRatio.coerceAtLeast(0.05f) else e.height
                    output[e.id] = Geometry(newGroupLeft, stackedY, width, height)
                    stackedY += height + gap
                }
                extraY = previousExtra + max(0f,
                    stackedY - gap - (originalTop + previousExtra) -
                    row.maxOf { it.y + it.height } + originalTop)
            } else {
                for (e in row) {
                    val expanded = e.width >= oldWidth * 0.84f
                    val width = if (expanded) min(available, e.width + (newWidth - oldWidth).coerceAtLeast(0))
                        else min(available, e.width)
                    val h = if (e.kind == ElementKind.IMAGE && e.aspectLocked)
                        width / e.imageRatio.coerceAtLeast(0.05f) else e.height
                    // Avoid pushing left-aligned content to the far edge of a desktop artboard.
                    val horizontal = if (groupW > available)
                        (e.x - left) * available / groupW
                    else e.x - left
                    val x = (newGroupLeft + horizontal).coerceIn(0f, max(0f, newWidth - width))
                    output[e.id] = Geometry(x, e.y + extraY, width, h)
                }
            }
        }
        return output
    }

    fun apply(project: DesignProject, newW: Int, newH: Int) {
        if (project.pageWidth == newW && project.pageHeight == newH) return
        val positions = compute(project.elements, project.pageWidth, newW)
        project.elements.forEach { e ->
            val g = positions[e.id] ?: return@forEach
            e.x = g.x
            e.y = g.y
            e.width = g.w
            e.height = g.h
        }
        project.pageWidth = newW
        project.pageHeight = newH
    }

    fun forViewport(project: DesignProject, width: Int): Map<String, Geometry> =
        compute(project.elements, project.pageWidth, width)
}
