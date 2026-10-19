package com.zeno.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.util.Base64
import java.io.File
import java.util.LinkedHashMap

object MediaAssets {
    private val bitmaps = object : LinkedHashMap<Int, Bitmap>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean =
            size > 12
    }
    private val typefaces = mutableMapOf<Int, Typeface>()

    fun bitmap(dataUri: String): Bitmap? {
        if (dataUri.isEmpty()) return null
        val key = dataUri.hashCode()
        synchronized(bitmaps) { bitmaps[key]?.let { return it } }
        return runCatching {
            val bytes = Base64.decode(dataUri.substringAfter(',', dataUri), Base64.DEFAULT)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sample = 1
            while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) sample *= 2
            opts.inJustDecodeBounds = false
            opts.inSampleSize = sample
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.also {
                synchronized(bitmaps) { bitmaps[key] = it }
            }
        }.getOrNull()
    }

    fun typeface(context: Context, font: EmbeddedFont?): Typeface? {
        if (font == null || font.data.isEmpty()) return null
        val key = font.data.hashCode()
        synchronized(typefaces) { typefaces[key]?.let { return it } }
        return runCatching {
            val bytes = Base64.decode(font.data, Base64.DEFAULT)
            val file = File(context.cacheDir, "zeno-font-$key.bin")
            if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
            Typeface.createFromFile(file).also { synchronized(typefaces) { typefaces[key] = it } }
        }.getOrNull()
    }

    fun clear() {
        synchronized(bitmaps) { bitmaps.clear() }
        synchronized(typefaces) { typefaces.clear() }
    }
}
