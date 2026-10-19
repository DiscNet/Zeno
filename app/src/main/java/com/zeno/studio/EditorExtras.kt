package com.zeno.studio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.util.UUID

class EditorExtras(
    private val activity: Activity,
    private val project: DesignProject,
    private val canvas: EditorCanvas,
    private val changed: () -> Unit
) {
    private var pendingImageId: String? = null
    private var fontTargetId: String? = null
    private val density get() = activity.resources.displayMetrics.density
    private fun dp(v: Int) = (v*density+0.5f).toInt()
    private fun notice(value: String) = Toast.makeText(activity, value, Toast.LENGTH_SHORT).show()

    fun pickImage(isBackground: Boolean = false, replaceId: String? = null) {
        pendingImageId = replaceId
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
        }
        activity.startActivityForResult(intent, if (isBackground) BACKGROUND_IMAGE else LAYER_IMAGE)
    }

    fun showLayers() {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(8))
        }
        val scroll = ScrollView(activity)
        scroll.addView(root)
        val dialog = AlertDialog.Builder(activity).setTitle("ORDEM DAS CAMADAS")
            .setView(scroll).setNegativeButton("Fechar", null).create()
        fun button(title: String, action: () -> Unit): TextView =
            TextView(activity).apply {
                text = title; textSize = 13f; gravity = Gravity.CENTER
                setPadding(dp(9), dp(7), dp(9), dp(7))
                setOnClickListener { action() }
            }
        fun rebuild() {
            root.removeAllViews()
            for (element in project.elements.asReversed().toList()) {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val shortName = when (element.kind) {
                    ElementKind.TEXT -> "T"
                    ElementKind.BOX -> "▢"
                    ElementKind.BUTTON -> "▣"
                    ElementKind.IMAGE -> "▧"
                } + "  " + (element.text.ifBlank {
                    if (element.kind == ElementKind.IMAGE) "Imagem" else "Forma"
                }).take(12)
                row.addView(button(shortName) {
                    canvas.select(element.id); dialog.dismiss()
                }, LinearLayout.LayoutParams(0, dp(45), 1f))
                row.addView(button("↑") { canvas.moveLayer(element.id, 1); rebuild() })
                row.addView(button("↓") { canvas.moveLayer(element.id, -1); rebuild() })
                row.addView(button(if (element.visible) "◉" else "○") {
                    element.visible = !element.visible; changed(); rebuild()
                })
                row.addView(button(if (element.locked) "🔒" else "◇") {
                    element.locked = !element.locked; changed(); rebuild()
                })
                root.addView(row)
                root.addView(View(activity).apply { minimumHeight = dp(1) })
            }
            if (project.elements.isEmpty())
                root.addView(TextView(activity).apply { text = "Nenhuma camada" })
        }
        rebuild()
        dialog.show()
        dialog.window?.setLayout(-1, -2)
    }

    fun showBackground() {
        val items = arrayOf("Cor sólida", "Grid", "Degradê", "Imagem")
        AlertDialog.Builder(activity).setTitle("FUNDO DA PÁGINA")
            .setSingleChoiceItems(items, project.backgroundMode.ordinal) { dialog, option ->
                dialog.dismiss()
                when (option) {
                    0 -> {
                        project.backgroundMode = BackgroundMode.SOLID; changed()
                        ColorPicker.show(activity, project.backgroundColor) { color, _ ->
                            project.backgroundColor = color; changed()
                        }
                    }
                    1 -> {
                        project.backgroundMode = BackgroundMode.GRID; changed()
                        AlertDialog.Builder(activity).setMessage("Grid de 20 px")
                            .setPositiveButton("Cor de fundo") { _, _ ->
                                ColorPicker.show(activity, project.backgroundColor) { color, _ ->
                                    project.backgroundColor = color; changed()
                                }
                            }.setNegativeButton("Fechar", null).show()
                    }
                    2 -> {
                        project.backgroundMode = BackgroundMode.GRADIENT
                        changed(); gradientDialog()
                    }
                    3 -> {
                        project.backgroundMode = BackgroundMode.IMAGE
                        changed(); pickImage(true)
                    }
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun gradientDialog() {
        AlertDialog.Builder(activity).setTitle("CORES DO DEGRADÊ")
            .setItems(arrayOf("Cor inicial", "Cor final")) { _, index ->
                val color = if (index == 0) project.gradientStart else project.gradientEnd
                ColorPicker.show(activity, color) { selected, _ ->
                    if (index == 0) project.gradientStart = selected
                    else project.gradientEnd = selected
                    changed()
                    gradientDialog()
                }
            }.setNegativeButton("Concluir", null).show()
    }

    fun showFontMenu(target: DesignElement) {
        val entries = project.fonts.keys.toList()
        val labels = arrayOf("Fonte padrão") +
            entries.map { project.fonts[it]?.name ?: "Fonte" } +
            listOf("＋ Importar TTF/OTF")
        AlertDialog.Builder(activity).setTitle("FONTES")
            .setItems(labels) { _, index ->
                when {
                    index == 0 -> { target.fontId = ""; changed() }
                    index == labels.size - 1 -> pickFont(target.id)
                    else -> {
                        target.fontId = entries[index-1]
                        changed()
                    }
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    fun pickFont(target: String?) {
        fontTargetId = target
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
        }
        activity.startActivityForResult(intent, FONT)
    }

    fun consumeActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode !in listOf(LAYER_IMAGE, BACKGROUND_IMAGE, FONT)) return false
        if (resultCode != Activity.RESULT_OK) return true
        val uri = data?.data ?: return true
        try {
            when (requestCode) {
                LAYER_IMAGE, BACKGROUND_IMAGE -> {
                    val image = encodeImage(uri)
                    if (requestCode == BACKGROUND_IMAGE) {
                        project.backgroundImage = image
                        project.backgroundMode = BackgroundMode.IMAGE
                        changed()
                    } else {
                        val target = project.elements.firstOrNull { it.id == pendingImageId }
                        if (target == null) canvas.add(ElementKind.IMAGE, image)
                        else { target.imageData = image; changed() }
                    }
                    pendingImageId = null
                    notice("Imagem importada")
                }
                FONT -> importFont(uri)
            }
        } catch (error: Exception) {
            notice("Falha na importação: " + (error.message ?: "arquivo inválido"))
        }
        return true
    }

    private fun readBytes(uri: Uri, maximum: Int): ByteArray {
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null,
            null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0))
                require(cursor.getLong(0) <= maximum.toLong()) { "Arquivo muito grande" }
        }
        return activity.contentResolver.openInputStream(uri)?.use { input ->
            val stream = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                require(stream.size() + count <= maximum) { "Arquivo muito grande" }
                stream.write(buffer, 0, count)
            }
            stream.toByteArray()
        } ?: error("Acesso negado ao arquivo")
    }

    private fun encodeImage(uri: Uri): String {
        val input = readBytes(uri, 12_000_000)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Imagem inválida" }
        var sample = 1
        while (options.outWidth/sample > 2400 || options.outHeight/sample > 2400)
            sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        val bitmap = BitmapFactory.decodeByteArray(input, 0, input.size, options)
            ?: error("Imagem inválida")
        val ratio = minOf(1f, 1600f/maxOf(bitmap.width, bitmap.height))
        val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap,
            (bitmap.width*ratio).toInt().coerceAtLeast(1),
            (bitmap.height*ratio).toInt().coerceAtLeast(1), true) else bitmap
        val output = ByteArrayOutputStream()
        val png = scaled.hasAlpha()
        require(scaled.compress(if (png) Bitmap.CompressFormat.PNG
            else Bitmap.CompressFormat.JPEG, if (png) 100 else 82, output)) {
            "Não foi possível converter a imagem"
        }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        require(output.size() <= 5_500_000) { "Imagem grande demais após otimização" }
        return "data:image/" + (if (png) "png" else "jpeg") +
            ";base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun importFont(uri: Uri) {
        val filename = activity.contentResolver.query(uri,
            arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "fonte.ttf"
        val lower = filename.lowercase()
        require(lower.endsWith(".ttf") || lower.endsWith(".otf")) {
            "Selecione um arquivo .ttf ou .otf"
        }
        val font = EmbeddedFont(filename, if (lower.endsWith(".otf")) "font/otf"
            else "font/ttf", Base64.encodeToString(
                readBytes(uri, 2_000_000), Base64.NO_WRAP))
        require(MediaAssets.typeface(activity, font) != null) { "Fonte não suportada" }
        val id = UUID.randomUUID().toString()
        project.fonts[id] = font
        project.elements.firstOrNull { it.id == fontTargetId }?.fontId = id
        changed()
        notice("Fonte importada: " + filename)
    }

    companion object {
        const val LAYER_IMAGE = 203
        const val BACKGROUND_IMAGE = 204
        const val FONT = 205
    }
}
