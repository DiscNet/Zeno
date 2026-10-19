package com.zeno.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class EditorCanvas(
    context: Context,
    private val project: DesignProject,
    private val onSelection: (DesignElement?) -> Unit,
    private val onChanged: () -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val canvasBackground = Color.rgb(23, 24, 29)
    private val accent = Color.rgb(118, 141, 255)
    private var zoom = 1f
    private var originX = 0f
    private var originY = 0f
    private var downX = 0f
    private var downY = 0f
    private var originalRect = RectF()
    private var gesture = Gesture.PAN
    private var changed = false
    private var scaling = false
    private val glassCache = mutableMapOf<String,Bitmap>()
    var activeScreen = 0
        private set

    fun invalidateGlassCache() {
        glassCache.values.forEach { if(!it.isRecycled)it.recycle() }
        glassCache.clear()
        invalidate()
    }

    private fun sceneChanged() {
        invalidateGlassCache()
        onChanged()
    }

    fun appendScreen() {
        if(project.screens >= 12)return
        project.appendScreen()
        activeScreen=project.screens-1
        select(null)
        invalidateGlassCache()
        scrollToSection(activeScreen)
        sceneChanged()
    }

    fun scrollToSection(index:Int) {
        activeScreen=index.coerceIn(0,project.screens-1)
        val top=project.sectionHeight()*activeScreen
        originY=resources.displayMetrics.density*18f-top*zoom
        invalidate()
    }

    override fun onDetachedFromWindow() {
        invalidateGlassCache()
        super.onDetachedFromWindow()
    }
    var snapToGuides = true
    var selectedId: String? = null
        private set

    private enum class Gesture { PAN, DRAG, RESIZE_SE, RESIZE_SW, RESIZE_NE, RESIZE_NW }

    private val detector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaling = true
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val x = (detector.focusX - originX) / zoom
                val y = (detector.focusY - originY) / zoom
                zoom = (zoom * detector.scaleFactor).coerceIn(0.12f, 5f)
                originX = detector.focusX - x * zoom
                originY = detector.focusY - y * zoom
                invalidate()
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                scaling = false
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
        val padding = dp(18f)
        zoom = min(
            (width - 2f * padding) / project.pageWidth,
            (height - 2f * padding) / project.sectionHeight()
        ).coerceIn(0.12f, 3f)
        originX = (width - project.pageWidth * zoom) / 2f
        originY = (height - project.sectionHeight() * zoom) / 2f
        invalidate()
    }

    fun add(kind: ElementKind, image: String = "", imageRatio: Float = 1f) {
        val offset = (project.elements.size % 6) * 15f
        val x = 22f + offset
        val y = project.sectionHeight()*activeScreen + 80f + offset
        val element = when (kind) {
            ElementKind.TEXT -> DesignElement(kind = kind, x = x, y = y,
                width = 255f, height = 60f, text = "Novo texto", fontSize = 22f)
            ElementKind.BUTTON -> DesignElement(kind = kind, x = x, y = y,
                width = 178f, height = 50f, text = "Botão", fontSize = 16f,
                fill = "#181A20", textColor = "#FFFFFF", radius = 5f)
            ElementKind.BOX -> DesignElement(kind = kind, x = x, y = y,
                width = 232f, height = 140f, fill = "#DFE5F0", radius = 4f)
            ElementKind.IMAGE -> {
                val ratio = imageRatio.coerceIn(0.05f, 30f)
                val w = 250f
                DesignElement(kind = kind, x = x, y = y,
                    width = w, height = w / ratio, imageData = image,
                    imageRatio = ratio, aspectLocked = true, radius = 4f)
            }
            ElementKind.GLASS -> DesignElement(kind = kind, x = x, y = y,
                width = 235f, height = 150f, radius = 12f,
                opacity = 1f, glassStrength = 0.6f)
        }
        project.elements.add(element)
        select(element.id)
        sceneChanged()
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        if (project.elements.removeAll { it.id == id }) {
            select(null)
            sceneChanged()
        }
    }

    fun moveLayer(id: String, delta: Int) {
        val old = project.elements.indexOfFirst { it.id == id }
        if (old < 0) return
        val next = (old + delta).coerceIn(0, project.elements.lastIndex)
        if (old == next) return
        val value = project.elements.removeAt(old)
        project.elements.add(next, value)
        sceneChanged()
    }

    fun bringSelectedToFront() {
        selectedId?.let { moveLayer(it, project.elements.size) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 || oldh == 0) fitToScreen()
    }

    private fun parseColor(s: String, fallback: Int): Int =
        runCatching { Color.parseColor(s) }.getOrDefault(fallback)

    private fun drawCover(canvas: Canvas, bitmap: Bitmap, box: RectF) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val scale = max(box.width() / w, box.height() / h)
        val cropW = box.width() / scale
        val cropH = box.height() / scale
        val src = Rect(
            ((w - cropW) / 2f).toInt(),
            ((h - cropH) / 2f).toInt(),
            ((w + cropW) / 2f).toInt(),
            ((h + cropH) / 2f).toInt())
        canvas.drawBitmap(bitmap, src, box, paint)
    }

    private fun drawPageBackground(canvas: Canvas, page: RectF) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.style = Paint.Style.FILL
        paint.color = parseColor(project.backgroundColor, Color.WHITE)
        when (project.backgroundMode) {
            BackgroundMode.GRADIENT -> {
                paint.shader = LinearGradient(0f, 0f, page.width(), page.height(),
                    parseColor(project.gradientStart, Color.WHITE),
                    parseColor(project.gradientEnd, Color.LTGRAY), Shader.TileMode.CLAMP)
                canvas.drawRect(page, paint)
                paint.shader = null
            }
            BackgroundMode.IMAGE -> {
                canvas.drawRect(page, paint)
                MediaAssets.bitmap(project.backgroundImage)?.let { drawCover(canvas, it, page) }
            }
            else -> canvas.drawRect(page, paint)
        }
        if (project.backgroundMode == BackgroundMode.GRID) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.6f
            paint.color = Color.argb(65, 105, 115, 136)
            var x = 0
            while (x <= project.pageWidth) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), page.bottom, paint)
                x += 20
            }
            var y = 0
            while (y <= project.pageHeight) {
                canvas.drawLine(0f, y.toFloat(), page.right, y.toFloat(), paint)
                y += 20
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(canvasBackground)
        canvas.save()
        canvas.translate(originX, originY)
        canvas.scale(zoom, zoom)
        val page = RectF(0f, 0f, project.pageWidth.toFloat(), project.pageHeight.toFloat())
        canvas.save()
        canvas.clipRect(page)
        drawPageBackground(canvas, page)

        for ((index,element) in project.elements.withIndex()) {
            if (!element.visible) continue
            if(element.kind == ElementKind.GLASS) {
                val bounds=RectF(element.x,element.y,element.x+element.width,
                    element.y+element.height)
                val glass=glassCache[element.id] ?: buildGlassBitmap(index,element)?.also {
                    glassCache[element.id]=it
                }
                if(glass!=null && !glass.isRecycled) {
                    paint.reset()
                    paint.isAntiAlias=true
                    paint.isFilterBitmap=true
                    canvas.drawBitmap(glass,null,bounds,paint)
                }
            } else drawStandardElement(canvas,element)
        }
        drawSectionGuides(canvas)
        canvas.restore()

        selected()?.takeIf { it.visible }?.let { selected ->
            val r = RectF(selected.x, selected.y, selected.x + selected.width,
                selected.y + selected.height)
            paint.reset()
            paint.isAntiAlias = true
            paint.color = accent
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f / zoom
            canvas.drawRect(r, paint)
            paint.style = Paint.Style.FILL
            val size = 5.5f / zoom
            val points = arrayOf(r.left to r.top, r.right to r.top,
                r.left to r.bottom, r.right to r.bottom)
            for ((x, y) in points) {
                paint.color = Color.WHITE
                canvas.drawRect(x-size, y-size, x+size, y+size, paint)
                paint.color = accent
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.7f / zoom
                canvas.drawRect(x-size, y-size, x+size, y+size, paint)
                paint.style = Paint.Style.FILL
            }
        }
        canvas.restore()
    }

    private fun drawSectionGuides(canvas:Canvas) {
        if(project.screens<=1)return
        val h=project.sectionHeight()
        val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style=Paint.Style.STROKE;strokeWidth=1.3f/zoom
            color=Color.argb(190,117,145,255)
        }
        for(i in 1 until project.screens) {
            val y=i*h
            canvas.drawLine(0f,y,project.pageWidth.toFloat(),y,line)
        }
    }

    private fun buildGlassBitmap(index:Int,e:DesignElement):Bitmap? {
        val rect=RectF(e.x,e.y,e.x+e.width,e.y+e.height)
        if(rect.width()<2f || rect.height()<2f)return null
        val margin=36f
        val x0=(rect.left-margin).coerceAtLeast(0f)
        val y0=(rect.top-margin).coerceAtLeast(0f)
        val x1=(rect.right+margin).coerceAtMost(project.pageWidth.toFloat())
        val y1=(rect.bottom+margin).coerceAtMost(project.pageHeight.toFloat())
        val cropW=x1-x0;val cropH=y1-y0
        if(cropW<2f||cropH<2f)return null
        val density=min(1.35f,1200f/max(cropW,cropH)).coerceIn(.55f,1.35f)
        val bw=max(1,(cropW*density).toInt())
        val bh=max(1,(cropH*density).toInt())
        val ow=max(1,(rect.width()*density).toInt())
        val oh=max(1,(rect.height()*density).toInt())
        if(bw.toLong()*bh>2_400_000L || ow.toLong()*oh>1_800_000L)return null
        return runCatching {
            val snapshot=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888)
            try {
                val backCanvas=Canvas(snapshot)
                backCanvas.scale(density,density)
                backCanvas.translate(-x0,-y0)
                drawPageBackground(backCanvas,RectF(0f,0f,
                    project.pageWidth.toFloat(),project.pageHeight.toFloat()))
                val cropped=RectF(x0,y0,x1,y1)
                val drawPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                for(i in 0 until index) {
                    val layer=project.elements[i]
                    if(!layer.visible)continue
                    val box=RectF(layer.x,layer.y,layer.x+layer.width,
                        layer.y+layer.height)
                    if(!RectF.intersects(box,cropped))continue
                    if(layer.kind==ElementKind.GLASS) {
                        val old=glassCache[layer.id]
                        if(old!=null && !old.isRecycled)
                            backCanvas.drawBitmap(old,null,box,drawPaint)
                    } else drawStandardElement(backCanvas,layer)
                }
                val result=Bitmap.createBitmap(ow,oh,Bitmap.Config.ARGB_8888)
                val paneCanvas=Canvas(result)
                paneCanvas.scale(density,density)
                paneCanvas.translate(-rect.left,-rect.top)
                GlassRenderer.draw(paneCanvas,snapshot,project.pageWidth,project.pageHeight,
                    rect,e.radius,e.glassStrength,e.opacity,e.glassFrost,e.glassDepth,
                    e.glassDispersion,e.glassLight,x0,y0,density)
                result
            } finally { snapshot.recycle() }
        }.getOrNull()
    }

    private fun drawStandardElement(canvas: Canvas, element: DesignElement) {
            val rect = RectF(element.x, element.y,
                element.x + element.width, element.y + element.height)
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.alpha = (element.opacity * 255).toInt().coerceIn(0, 255)
            val r = element.radius.coerceAtMost(min(element.width, element.height) / 2)
            if (element.kind == ElementKind.IMAGE) {
                canvas.save()
                canvas.clipPath(android.graphics.Path().apply { addRoundRect(rect, r, r,
                    android.graphics.Path.Direction.CW) })
                val bitmap = MediaAssets.bitmap(element.imageData)
                if (bitmap != null) drawCover(canvas, bitmap, rect)
                else {
                    paint.color = Color.LTGRAY
                    canvas.drawRect(rect, paint)
                }
                canvas.restore()
            } else if (element.kind == ElementKind.BOX || element.kind == ElementKind.BUTTON) {
                paint.color = parseColor(element.fill, Color.LTGRAY)
                paint.alpha = (element.opacity * 255).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(rect, r, r, paint)
            }

            if (element.kind == ElementKind.TEXT || element.kind == ElementKind.BUTTON) {
                textPaint.reset()
                textPaint.isAntiAlias = true
                textPaint.color = parseColor(element.textColor, Color.BLACK)
                textPaint.alpha = (element.opacity * 255).toInt().coerceIn(0, 255)
                textPaint.typeface = MediaAssets.typeface(context,
                    project.fonts[element.fontId]) ?: Typeface.create(
                        "sans-serif", if (element.kind == ElementKind.BUTTON)
                            Typeface.BOLD else Typeface.NORMAL)
                textPaint.textSize = element.fontSize
                val innerWidth = (rect.width() - 8f).toInt().coerceAtLeast(1)
                val layout = StaticLayout.Builder.obtain(element.text, 0,
                    element.text.length, textPaint, innerWidth)
                    .setAlignment(if (element.kind == ElementKind.BUTTON)
                        Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false).build()
                canvas.save()
                canvas.clipRect(rect)
                canvas.translate(rect.left + 4f,
                    rect.top + max(0f, (rect.height() - layout.height) / 2f))
                layout.draw(canvas)
                canvas.restore()
            }
    }

    private fun worldX(screenX: Float): Float = (screenX - originX) / zoom
    private fun worldY(screenY: Float): Float = (screenY - originY) / zoom

    private fun handleAt(x: Float, y: Float): Gesture? {
        val e = selected() ?: return null
        if (!e.visible || e.locked) return null
        val pad = max(13f, dp(18f) / zoom)
        val points = arrayOf(
            Triple(e.x, e.y, Gesture.RESIZE_NW),
            Triple(e.x+e.width, e.y, Gesture.RESIZE_NE),
            Triple(e.x, e.y+e.height, Gesture.RESIZE_SW),
            Triple(e.x+e.width, e.y+e.height, Gesture.RESIZE_SE))
        return points.firstOrNull { abs(x-it.first) < pad && abs(y-it.second) < pad }?.third
    }

    private fun hitTest(x: Float, y: Float): DesignElement? =
        project.elements.asReversed().firstOrNull {
            it.visible && !it.locked && x >= it.x && x <= it.x + it.width &&
                y >= it.y && y <= it.y + it.height
        }

    private fun snapped(value: Float, axis: Char, except: String): Float {
        if (!snapToGuides) return value
        val section=activeScreen
        val sh=project.sectionHeight()
        val stops = mutableListOf(if (axis == 'x')
            project.pageWidth / 2f else sh*(section+0.5f))
        stops.add(if (axis=='x') 0f else sh*section)
        stops.add(if (axis == 'x') project.pageWidth.toFloat()
            else sh*(section+1))
        for (e in project.elements) {
            if (e.id == except || !e.visible) continue
            if (axis == 'x') {
                stops.add(e.x); stops.add(e.x + e.width)
                stops.add(e.x + e.width/2)
            } else {
                stops.add(e.y); stops.add(e.y + e.height)
                stops.add(e.y + e.height/2)
            }
        }
        return stops.firstOrNull { abs(value - it) <= 5f / zoom } ?: value
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                changed = false
                val x = worldX(event.x)
                val y = worldY(event.y)
                activeScreen=(y/project.sectionHeight()).toInt().coerceIn(0,project.screens-1)
                val resize = handleAt(x, y)
                val hit = if (resize == null) hitTest(x, y) else selected()
                if (resize != null) gesture = resize
                else {
                    select(hit?.id)
                    gesture = if (hit != null) Gesture.DRAG else Gesture.PAN
                }
                selected()?.let { originalRect = RectF(it.x, it.y,
                    it.x + it.width, it.y + it.height) }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                gesture = Gesture.PAN
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !detector.isInProgress && !scaling) {
                    val dx = (event.x - downX) / zoom
                    val dy = (event.y - downY) / zoom
                    val element = selected()
                    if (gesture == Gesture.PAN || element == null || element.locked) {
                        originX += event.x - downX
                        originY += event.y - downY
                        downX = event.x; downY = event.y
                    } else if (gesture == Gesture.DRAG) {
                        val x = snapped(originalRect.left + dx, 'x', element.id)
                        val y = snapped(originalRect.top + dy, 'y', element.id)
                        element.x = x.coerceIn(0f, max(0f, project.pageWidth - element.width))
                        element.y = y.coerceIn(0f, max(0f, project.pageHeight - element.height))
                        changed = true
                    } else {
                        var l = originalRect.left
                        var t = originalRect.top
                        var r = originalRect.right
                        var b = originalRect.bottom
                        if (gesture == Gesture.RESIZE_NW || gesture == Gesture.RESIZE_SW) l += dx
                        else r += dx
                        if (gesture == Gesture.RESIZE_NW || gesture == Gesture.RESIZE_NE) t += dy
                        else b += dy
                        l = l.coerceIn(0f, r - 20f)
                        t = t.coerceIn(0f, b - 20f)
                        r = r.coerceIn(l + 20f, project.pageWidth.toFloat())
                        b = b.coerceIn(t + 20f, project.pageHeight.toFloat())
                        if (element.kind == ElementKind.IMAGE && element.aspectLocked) {
                            val ratio = element.imageRatio.coerceAtLeast(0.05f)
                            // Resizing from any corner keeps the opposite anchor stable.
                            val targetW = max(20f, r-l)
                            val targetH = max(20f, b-t)
                            val w = min(targetW, targetH*ratio)
                            val h = w/ratio
                            when (gesture) {
                                Gesture.RESIZE_NW -> { l = r-w; t = b-h }
                                Gesture.RESIZE_NE -> { r = l+w; t = b-h }
                                Gesture.RESIZE_SW -> { l = r-w; b = t+h }
                                Gesture.RESIZE_SE -> { r = l+w; b = t+h }
                                else -> Unit
                            }
                        }
                        element.x = l; element.y = t
                        element.width = r - l; element.height = b - t
                        changed = true
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (changed) sceneChanged()
                else performClick()
                gesture = Gesture.PAN
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
