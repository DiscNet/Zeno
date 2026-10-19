package com.zeno.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * High-resolution frosted lens. Blurs the backdrop with three separable
 * running-sum box passes, then samples smoothly displaced pixels near edges.
 * This intentionally avoids the old 1/10-resolution upscaling artefacts.
 */
object GlassRenderer {
    private fun clamp(v:Int,a:Int,b:Int)=v.coerceIn(a,b)

    // Two directional passes, repeated, approximate Gaussian frost in O(w*h).
    private fun blur(src:IntArray,w:Int,h:Int,radius:Int):IntArray {
        if(radius<=0)return src
        var data=src
        repeat(2) {
            val horizontal=IntArray(data.size)
            val vertical=IntArray(data.size)
            for(y in 0 until h) {
                var aa=0;var rr=0;var gg=0;var bb=0
                for(k in -radius..radius) {
                    val p=data[y*w+clamp(k,0,w-1)]
                    aa+=Color.alpha(p);rr+=Color.red(p);gg+=Color.green(p);bb+=Color.blue(p)
                }
                val n=radius*2+1
                for(x in 0 until w) {
                    horizontal[y*w+x]=Color.argb(aa/n,rr/n,gg/n,bb/n)
                    val add=data[y*w+clamp(x+radius+1,0,w-1)]
                    val sub=data[y*w+clamp(x-radius,0,w-1)]
                    aa+=Color.alpha(add)-Color.alpha(sub)
                    rr+=Color.red(add)-Color.red(sub)
                    gg+=Color.green(add)-Color.green(sub)
                    bb+=Color.blue(add)-Color.blue(sub)
                }
            }
            for(x in 0 until w) {
                var aa=0;var rr=0;var gg=0;var bb=0
                for(k in -radius..radius) {
                    val p=horizontal[clamp(k,0,h-1)*w+x]
                    aa+=Color.alpha(p);rr+=Color.red(p);gg+=Color.green(p);bb+=Color.blue(p)
                }
                val n=radius*2+1
                for(y in 0 until h) {
                    vertical[y*w+x]=Color.argb(aa/n,rr/n,gg/n,bb/n)
                    val add=horizontal[clamp(y+radius+1,0,h-1)*w+x]
                    val sub=horizontal[clamp(y-radius,0,h-1)*w+x]
                    aa+=Color.alpha(add)-Color.alpha(sub)
                    rr+=Color.red(add)-Color.red(sub)
                    gg+=Color.green(add)-Color.green(sub)
                    bb+=Color.blue(add)-Color.blue(sub)
                }
            }
            data=vertical
        }
        return data
    }

    private fun sample(data:IntArray,w:Int,h:Int,x:Float,y:Float):Int {
        val left=floor(x).toInt().coerceIn(0,w-1)
        val top=floor(y).toInt().coerceIn(0,h-1)
        val right=(left+1).coerceAtMost(w-1)
        val bottom=(top+1).coerceAtMost(h-1)
        val fx=(x-left).coerceIn(0f,1f)
        val fy=(y-top).coerceIn(0f,1f)
        val a=data[top*w+left];val b=data[top*w+right]
        val c=data[bottom*w+left];val d=data[bottom*w+right]
        fun lerp(i:Int,j:Int)=i+(j-i)*fx
        fun channel(f:(Int)->Int):Int {
            val t=lerp(f(a),f(b));val u=lerp(f(c),f(d))
            return (t+(u-t)*fy).toInt().coerceIn(0,255)
        }
        return Color.argb(channel(Color::alpha),channel(Color::red),
            channel(Color::green),channel(Color::blue))
    }

    // Signed distance: positive inside a rounded rectangle.
    private fun distance(x:Float,y:Float,w:Float,h:Float,radius:Float):Float {
        val r=radius.coerceIn(0f,min(w,h)/2f)
        val qx=abs(x-w*.5f)-(w*.5f-r)
        val qy=abs(y-h*.5f)-(h*.5f-r)
        val outer=hypot(max(qx,0f),max(qy,0f))
        return -(outer+min(max(qx,qy),0f)-r)
    }

    fun draw(canvas:Canvas,backdrop:Bitmap,pageWidth:Int,pageHeight:Int,
        rect:RectF,radius:Float,intensity:Float,opacity:Float,
        frost:Float=0.14f,depth:Float=0.75f,dispersion:Float=0.15f,
        light:Float=0.36f,originX:Float=0f,originY:Float=0f,
        renderScale:Float=backdrop.width/pageWidth.toFloat()) {
        if(rect.width()<=1f||rect.height()<=1f)return
        val strength=intensity.coerceIn(0f,1f)
        val alpha=opacity.coerceIn(0f,1f)
        if(alpha<=0f)return

        val scale=renderScale
        // Crop only the glass footprint plus blur/refraction reach.
        val reach=ceil((14f+strength*14f)*scale).toInt().coerceAtLeast(6)
        val x0=floor((rect.left-originX)*scale).toInt().coerceIn(0,backdrop.width-1)
        val y0=floor((rect.top-originY)*scale).toInt().coerceIn(0,backdrop.height-1)
        val x1=ceil((rect.right-originX)*scale).toInt().coerceIn(x0+1,backdrop.width)
        val y1=ceil((rect.bottom-originY)*scale).toInt().coerceIn(y0+1,backdrop.height)
        val bx0=(x0-reach).coerceAtLeast(0)
        val by0=(y0-reach).coerceAtLeast(0)
        val bx1=(x1+reach).coerceAtMost(backdrop.width)
        val by1=(y1+reach).coerceAtMost(backdrop.height)
        val bw=bx1-bx0
        val bh=by1-by0
        // Bound memory for malformed artboards.
        if(bw<=0||bh<=0||bw.toLong()*bh>4_000_000L)return
        val source=IntArray(bw*bh)
        backdrop.getPixels(source,0,bw,bx0,by0,bw,bh)
        val blurRadius=if(frost<0.03f) 0 else (1f+frost*5f).toInt().coerceIn(1,6)
        val frosted=blur(source,bw,bh,blurRadius)
        val outW=x1-x0;val outH=y1-y0
        val out=IntArray(outW*outH)
        val w=rect.width();val h=rect.height()
        val edgeDepth=(7f+depth*19f).coerceAtMost(min(w,h)*.42f)
        val bend=(2f+depth*13f)*strength

        for(y in 0 until outH) {
            val localY=(y+y0+.5f)/scale+originY-rect.top
            for(x in 0 until outW) {
                val localX=(x+x0+.5f)/scale+originX-rect.left
                val dist=distance(localX,localY,w,h,radius)
                if(dist<=-.8f)continue
                val coverage=(dist+.8f).coerceIn(0f,1f)
                val edge=(1f-dist/edgeDepth).coerceIn(0f,1f)
                val nx=if(localX<w*.5f) 1f else -1f
                val ny=if(localY<h*.5f) 1f else -1f
                val nearX=min(localX,w-localX)
                val nearY=min(localY,h-localY)
                val sideX=if(nearX<nearY)nx else nx*.24f
                val sideY=if(nearY<nearX)ny else ny*.24f
                val bnd=edge*edge*bend*scale
                val u=(x+x0-bx0+sideX*bnd).coerceIn(0f,(bw-1).toFloat())
                val v=(y+y0-by0+sideY*bnd).coerceIn(0f,(bh-1).toFloat())
                val crisp=sample(source,bw,bh,u,v)
                val soft=sample(frosted,bw,bh,u,v)
                // Retain texture, while softening it like frosted glass.
                val frostMix=(frost*0.82f).coerceIn(0f,0.82f)
                fun mix(a:Int,b:Int,t:Float)=(a*(1-t)+b*t).toInt()
                val hilite=((edge.pow(4)*.17f +
                    .016f*(1f-localY/h))*light).coerceIn(0f,.15f)
                val light=(hilite*255).toInt()
                val fringe=dispersion*edge*2.2f*scale
                val fringeRed=if(fringe>.15f) sample(source,bw,bh,
                    (u-fringe).coerceIn(0f,(bw-1).toFloat()),v) else crisp
                val fringeBlue=if(fringe>.15f) sample(source,bw,bh,
                    (u+fringe).coerceIn(0f,(bw-1).toFloat()),v) else crisp
                val rr=(mix(Color.red(fringeRed),Color.red(soft),frostMix)+light)
                    .coerceIn(0,255)
                val gg=(mix(Color.green(crisp),Color.green(soft),frostMix)+light)
                    .coerceIn(0,255)
                val bb=(mix(Color.blue(fringeBlue),Color.blue(soft),frostMix)+light)
                    .coerceIn(0,255)
                out[y*outW+x]=Color.argb((255*coverage).toInt().coerceIn(0,255),
                    rr,gg,bb)
            }
        }
        val image=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888)
        image.setPixels(out,0,outW,0,0,outW,outH)
        val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val saved=canvas.saveLayerAlpha(rect,(alpha*255).toInt().coerceIn(0,255))
        canvas.drawBitmap(image,null,rect,p)
        image.recycle()

        val rim=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style=Paint.Style.STROKE
            strokeWidth=1.6f
            color=Color.argb((110f+120f*light).toInt(),240,248,255)
        }
        canvas.drawRoundRect(rect,radius,radius,rim)
        rim.strokeWidth=1f
        rim.color=Color.argb((70f+60f*depth).toInt(),30,48,68)
        val inner=RectF(rect.left+2.5f,rect.top+2.5f,
            rect.right-2.5f,rect.bottom-2.5f)
        canvas.drawRoundRect(inner,max(0f,radius-2.5f),
            max(0f,radius-2.5f),rim)
        val line=RectF(rect.left+radius+6f,rect.top+3.2f,
            rect.right-radius-6f,rect.top+4.2f)
        if(line.right>line.left) {
            rim.shader=LinearGradient(line.left,line.top,line.right,line.top,
                intArrayOf(Color.TRANSPARENT,Color.argb((170f*light).toInt(),255,255,255),
                    Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
            rim.style=Paint.Style.FILL
            canvas.drawRoundRect(line,1f,1f,rim)
        }
        canvas.restoreToCount(saved)
    }
}
