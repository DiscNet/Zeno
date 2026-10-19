package com.zeno.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.min

class EditorCanvas(
    context: Context,
    private val project: DesignProject,
    private val onSelection: (DesignElement?) -> Unit,
    private val onChanged: () -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val background = Color.rgb(25, 27, 33)
    private val accent = Color.rgb(103, 126, 255)
    private var zoom = 1f
    private var originX = 0f
    private var originY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragElement = false
    private var moved = false

    var selectedId: String? = null
        private set

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val worldX = (detector.focusX - originX) / zoom
                val worldY = (detector.focusY - originY) / zoom
                val next = (zoom * detector.scaleFactor).coerceIn(0.12f, 4f)
                originX = detector.focusX - worldX * next
                originY = detector.focusY - worldY * next
                zoom = next
                invalidate()
                return true
            }
        })

    fun selected(): DesignElement? = project.elements.firstOrNull { it.id == selectedId }

    fun select(id: String?) {
        selectedId = id
        onSelection(selected())
        invalidate()
    }

    fun fitToScreen() {
        if (width <= 0 || height <= 0) return
        val padding = dp(20f)
        zoom = min(
            (width - 2f * padding) / project.pageWidth,
            (height - 2f * padding) / project.pageHeight
        ).coerceIn(0.12f, 3f)
        originX = (width - project.pageWidth * zoom) / 2f
        originY = (height - project.pageHeight * zoom) / 2f
        invalidate()
    }

    fun add(kind: ElementKind) {
        val offset = (project.elements.size % 6) * 16f
        val element = when (kind) {
            ElementKind.TEXT -> DesignElement(
                kind = kind, x = 28f + offset, y = 92f + offset,
                width = 270f, height = 55f, text = "Novo texto",
                fill = "#FFFFFF", textColor = "#111827", fontSize = 22f
            )
            ElementKind.BUTTON -> DesignElement(
                kind = kind, x = 28f + offset, y = 92f + offset,
                width = 192f, height = 52f, text = "Botão",
                fill = "#15171C", textColor = "#FFFFFF", fontSize = 16f
            )
            ElementKind.BOX -> DesignElement(
                kind = kind, x = 28f + offset, y = 92f + offset,
                width = 240f, height = 140f, fill = "#DDE2F0"
            )
        }
        project.elements.add(element)
        select(element.id)
        onChanged()
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        if (project.elements.removeAll { it.id == id }) {
            select(null)
            onChanged()
        }
    }

    fun bringSelectedToFront() {
        val element = selected() ?: return
        project.elements.remove(element)
        project.elements.add(element)
        invalidate()
        onChanged()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 || oldh == 0) fitToScreen()
    }

    private fun parseColor(hex: String, fallback: Int): Int =
        runCatching { Color.parseColor(hex) }.getOrDefault(fallback)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(background)
        canvas.save()
        canvas.translate(originX, originY)
        canvas.scale(zoom, zoom)

        val w = project.pageWidth.toFloat()
        val h = project.pageHeight.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, w, h, paint)
        canvas.save()
        canvas.clipRect(0f, 0f, w, h)

        paint.strokeWidth = 0.6f
        paint.color = Color.rgb(244, 246, 249)
        for (x in 0..project.pageWidth step 20) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), h, paint)
        }
        for (y in 0..project.pageHeight step 20) {
            canvas.drawLine(0f, y.toFloat(), w, y.toFloat(), paint)
        }

        project.elements.forEach { element ->
            val rect = RectF(element.x, element.y,
                element.x + element.width, element.y + element.height)
            paint.style = Paint.Style.FILL
            paint.color = parseColor(element.fill, Color.LTGRAY)

            if (element.kind != ElementKind.TEXT) {
                canvas.drawRoundRect(rect, 10f, 10f, paint)
            }

            if (element.kind != ElementKind.BOX) {
                textPaint.color = parseColor(element.textColor, Color.BLACK)
                textPaint.typeface = Typeface.create(
                    "sans-serif",
                    if (element.kind == ElementKind.BUTTON) Typeface.BOLD else Typeface.NORMAL
                )
                textPaint.textSize = element.fontSize
                val baseline = rect.centerY() -
                    (textPaint.ascent() + textPaint.descent()) / 2f
                canvas.save()
                canvas.clipRect(rect)
                val label = element.text.replace('\n', ' ')
                if (element.kind == ElementKind.BUTTON) {
                    canvas.drawText(label, rect.centerX() - textPaint.measureText(label) / 2f,
                        baseline, textPaint)
                } else {
                    canvas.drawText(label, rect.left + 2f, baseline, textPaint)
                }
                canvas.restore()
            }

            if (selectedId == element.id) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f / zoom
                paint.color = accent
                canvas.drawRect(rect, paint)
                paint.style = Paint.Style.FILL
                val r = 4.5f / zoom
                for (point in arrayOf(
                    floatArrayOf(rect.left, rect.top),
                    floatArrayOf(rect.right, rect.top),
                    floatArrayOf(rect.left, rect.bottom),
                    floatArrayOf(rect.right, rect.bottom)
                )) {
                    canvas.drawCircle(point[0], point[1], r, paint)
                }
            }
        }
        canvas.restore()
        canvas.restore()
    }

    private fun hitTest(screenX: Float, screenY: Float): DesignElement? {
        val x = (screenX - originX) / zoom
        val y = (screenY - originY) / zoom
        return project.elements.asReversed().firstOrNull {
            x >= it.x && x <= it.x + it.width &&
                y >= it.y && y <= it.y + it.height
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                moved = false
                val hit = hitTest(event.x, event.y)
                dragElement = hit != null
                select(hit?.id)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                dragElement = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (dx != 0f || dy != 0f) {
                        if (dragElement) {
                            selected()?.let {
                                it.x = (it.x + dx / zoom).coerceIn(
                                    0f, (project.pageWidth - it.width).coerceAtLeast(0f))
                                it.y = (it.y + dy / zoom).coerceIn(
                                    0f, (project.pageHeight - it.height).coerceAtLeast(0f))
                                moved = true
                            }
                        } else {
                            originX += dx
                            originY += dy
                        }
                        invalidate()
                    }
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                lastX = event.getX(0)
                lastY = event.getY(0)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (moved) onChanged()
                else performClick()
                dragElement = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (moved) onChanged()
                dragElement = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
