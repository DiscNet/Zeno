package com.zeno.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Refractive glass. The bitmap contains the page and only layers BEHIND this glass. */
object GlassRenderer {
    fun draw(canvas: Canvas, backdrop: Bitmap, pageWidth: Int, pageHeight: Int,
        rect: RectF, radius: Float, intensity: Float, opacity: Float) {
        val power = intensity.coerceIn(0f, 1f)
        val layerOpacity = opacity.coerceIn(0f, 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val saved = canvas.saveLayer(rect, null)
        val clip = Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        // The extremely reduced backdrop is bilinearly interpolated back up,
        // creating a real (rather than uniformly translucent) blurred sample.
        val tiny = Bitmap.createScaledBitmap(backdrop,
            max(1, backdrop.width/5), max(1, backdrop.height/5), true)
        paint.alpha = (220*layerOpacity).toInt().coerceIn(0,255)
        canvas.drawBitmap(tiny, null, RectF(0f,0f,
            pageWidth.toFloat(), pageHeight.toFloat()), paint)
        tiny.recycle()

        // Horizontal displacement bands refract the content beneath the surface.
        val sx = backdrop.width / pageWidth.toFloat()
        val sy = backdrop.height / pageHeight.toFloat()
        val step = 5f
        var y = rect.top
        paint.alpha = ((70+power*75)*layerOpacity).toInt().coerceIn(0,255)
        while (y < rect.bottom) {
            val stop = min(rect.bottom,y+step)
            val displacement = sin(y/19.0).toFloat()*(1.5f+power*6f)
            val source = Rect(
                ((rect.left+displacement)*sx).toInt().coerceIn(0, backdrop.width-1),
                (y*sy).toInt().coerceIn(0,backdrop.height-1),
                ((rect.right+displacement)*sx).toInt().coerceIn(1,backdrop.width),
                (stop*sy).toInt().coerceIn(1,backdrop.height))
            if (source.right > source.left && source.bottom > source.top)
                canvas.drawBitmap(backdrop,source,RectF(rect.left,y,rect.right,stop),paint)
            y = stop
        }

        paint.shader = LinearGradient(rect.left,rect.top,rect.right,rect.bottom,
            intArrayOf(
                Color.argb((55*layerOpacity).toInt(),255,255,255),
                Color.argb((8*layerOpacity).toInt(),245,250,255),
                Color.argb((75*layerOpacity).toInt(),210,225,255)),
            null, Shader.TileMode.CLAMP)
        paint.alpha = 255
        canvas.drawRoundRect(rect,radius,radius,paint)
        paint.shader = null
        canvas.restoreToCount(saved)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.45f
        paint.color = Color.argb((210*layerOpacity).toInt(),246,249,255)
        canvas.drawRoundRect(rect,radius,radius,paint)
        paint.strokeWidth = 0.75f
        paint.color = Color.argb((125*layerOpacity).toInt(),60,75,98)
        canvas.drawRoundRect(RectF(rect.left+2f,rect.top+2f,rect.right-2f,
            rect.bottom-2f),max(0f,radius-2f),max(0f,radius-2f),paint)
        paint.style=Paint.Style.FILL
        paint.color=Color.argb((95*layerOpacity).toInt(),255,255,255)
        canvas.drawRoundRect(RectF(rect.left+8f,rect.top+3f,
            rect.right-8f,rect.top+4.3f),1.2f,1.2f,paint)
    }
}
