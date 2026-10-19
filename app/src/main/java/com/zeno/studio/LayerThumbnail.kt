package com.zeno.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** Lightweight, file-independent layer previews for the ordering sheet. */
class LayerThumbnail(context: Context, private val element: DesignElement): View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private fun color(hex: String, fallback: Int) =
        runCatching { Color.parseColor(hex) }.getOrDefault(fallback)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val box = RectF(2f,2f,width-2f,height-2f)
        paint.color = Color.rgb(43,46,54)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(box,5f,5f,paint)
        val content = RectF(5f,5f,width-5f,height-5f)
        when (element.kind) {
            ElementKind.IMAGE -> {
                val bitmap=MediaAssets.bitmap(element.imageData)
                if (bitmap!=null) {
                    val scale=maxOf(content.width()/bitmap.width,content.height()/bitmap.height)
                    val cw=content.width()/scale
                    val ch=content.height()/scale
                    canvas.save()
                    canvas.clipRect(content)
                    canvas.drawBitmap(bitmap,android.graphics.Rect(
                        ((bitmap.width-cw)/2).toInt(),((bitmap.height-ch)/2).toInt(),
                        ((bitmap.width+cw)/2).toInt(),((bitmap.height+ch)/2).toInt()
                    ),content,paint)
                    canvas.restore()
                }
            }
            ElementKind.GLASS -> {
                paint.color=Color.rgb(155,176,204)
                paint.alpha=85
                canvas.drawRoundRect(content,5f,5f,paint)
                paint.alpha=255
                paint.color=Color.WHITE
                paint.style=Paint.Style.STROKE
                paint.strokeWidth=2f
                canvas.drawRoundRect(content,5f,5f,paint)
                paint.style=Paint.Style.FILL
            }
            ElementKind.BOX -> {
                paint.color=color(element.fill,Color.GRAY)
                canvas.drawRoundRect(content,element.radius.coerceAtMost(12f),
                    element.radius.coerceAtMost(12f),paint)
            }
            ElementKind.TEXT, ElementKind.BUTTON -> {
                if (element.kind == ElementKind.BUTTON) {
                    paint.color=color(element.fill,Color.DKGRAY)
                    canvas.drawRoundRect(content,5f,5f,paint)
                }
                paint.color=color(element.textColor,Color.WHITE)
                paint.typeface=Typeface.DEFAULT_BOLD
                paint.textSize=16f*resources.displayMetrics.density
                val label=element.text.take(1).ifEmpty { "T" }
                canvas.drawText(label,content.centerX()-paint.measureText(label)/2,
                    content.centerY()-(paint.ascent()+paint.descent())/2,paint)
            }
        }
        if (!element.visible) {
            paint.color=Color.argb(175,29,30,37)
            canvas.drawRoundRect(box,5f,5f,paint)
        }
    }
}
