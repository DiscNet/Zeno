package com.zeno.studio

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val ink = Color.rgb(245, 246, 250)
    private val muted = Color.rgb(170, 175, 188)
    private val background = Color.rgb(17, 18, 23)
    private val surface = Color.rgb(26, 28, 35)
    private val border = Color.rgb(55, 58, 69)
    private val accent = Color.rgb(112, 130, 255)
    private val palette = listOf("#15171C", "#FFFFFF", "#606AFF", "#DDE2F0",
        "#F4A261", "#2A9D8F", "#E76F83", "#1F2937")

    private lateinit var project: DesignProject
    private lateinit var canvasView: EditorCanvas
    private lateinit var inspector: LinearLayout
    private val history = mutableListOf<String>()
    private var historyIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        project = loadDraft()
        history.add(project.toJson().toString())
        historyIndex = 0
        buildInterface()
    }

    private fun loadDraft(): DesignProject {
        val raw = getSharedPreferences("zeno", MODE_PRIVATE).getString("autosave", null)
        if (raw.isNullOrBlank()) return DesignProject.starter()
        return runCatching {
            DesignProject().apply { restore(JSONObject(raw)) }
        }.getOrElse { DesignProject.starter() }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(fill: Int, radius: Int = 10, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            stroke?.let { setStroke(dp(1), it) }
        }

    private fun label(text: String, size: Float = 13f, color: Int = ink): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun action(text: String, primary: Boolean = false, onClick: () -> Unit): TextView =
        label(text, 13f, if (primary) Color.WHITE else ink).apply {
            gravity = Gravity.CENTER
            setPadding(dp(13), 0, dp(13), 0)
            background = rounded(if (primary) accent else surface, 9,
                if (primary) null else border)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)
            ).apply { marginEnd = dp(7) }
            setOnClickListener { onClick() }
            isClickable = true
        }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
        }
        setContentView(root)

        val topScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setFillViewport(true)
            setPadding(dp(10), dp(7), 0, dp(7))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(label("ZENO.", 21f).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(7), 0, dp(20), 0)
        })
        top.addView(action("↶") { undo() })
        top.addView(action("↷") { redo() })
        top.addView(action("Abrir") { openProject() })
        top.addView(action("Prévia") { showPreview() })
        top.addView(action("Salvar") { saveProject() })
        top.addView(action("Exportar", true) { exportWebsite() })
        topScroll.addView(top)
        root.addView(topScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        val toolScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(10), dp(5), 0, dp(5))
        }
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tools.addView(action("+ Texto") { canvasView.add(ElementKind.TEXT) })
        tools.addView(action("+ Forma") { canvasView.add(ElementKind.BOX) })
        tools.addView(action("+ Botão") { canvasView.add(ElementKind.BUTTON) })
        tools.addView(action("Camadas") { showLayers() })
        tools.addView(action("Tela") { chooseCanvasSize() })
        tools.addView(action("Ajustar") { canvasView.fitToScreen() })
        toolScroll.addView(tools)
        root.addView(toolScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(51)))

        canvasView = EditorCanvas(this, project, { showInspector(it) }, {
            rememberChange()
            showInspector(canvasView.selected())
        })
        root.addView(canvasView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        inspector = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
            setPadding(dp(12), dp(12), dp(12), dp(13))
        }
        root.addView(inspector, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        showInspector(null)
    }

    private fun scrollRow(): Pair<HorizontalScrollView, LinearLayout> {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(inner)
        return scroll to inner
    }

    private fun showInspector(element: DesignElement?) {
        if (!::inspector.isInitialized) return
        inspector.removeAllViews()

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = label(
            if (element == null) "Nenhum elemento selecionado" else
                when (element.kind) {
                    ElementKind.TEXT -> "Texto"
                    ElementKind.BOX -> "Forma"
                    ElementKind.BUTTON -> "Botão"
                },
            14f
        ).apply { setTypeface(typeface, Typeface.BOLD) }
        heading.addView(title, LinearLayout.LayoutParams(0, dp(35), 1f))
        if (element != null) {
            heading.addView(action("Frente") { canvasView.bringToFront() })
            heading.addView(action("Excluir") { canvasView.deleteSelected() })
        }
        inspector.addView(heading)

        if (element == null) {
            inspector.addView(label(
                "Adicione um elemento ou toque no canvas para editar.",
                12f, muted
            ).apply { setPadding(0, dp(3), 0, dp(7)) })
            return
        }

        if (element.kind != ElementKind.BOX) {
            val textInput = field(element.text, false) { next ->
                if (element.text != next) {
                    element.text = next
                    propertyChanged()
                }
            }
            textInput.hint = "Texto do elemento"
            inspector.addView(textInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
            ).apply { bottomMargin = dp(8) })
        }

        val (numbersScroll, numbers) = scrollRow()
        numbers.addView(numberField("X", element.x) {
            element.x = it.coerceIn(0f, (project.pageWidth - element.width).coerceAtLeast(0f))
            propertyChanged()
        })
        numbers.addView(numberField("Y", element.y) {
            element.y = it.coerceIn(0f, (project.pageHeight - element.height).coerceAtLeast(0f))
            propertyChanged()
        })
        numbers.addView(numberField("W", element.width) {
            element.width = it.coerceIn(20f, project.pageWidth.toFloat())
            propertyChanged()
        })
        numbers.addView(numberField("H", element.height) {
            element.height = it.coerceIn(20f, project.pageHeight.toFloat())
            propertyChanged()
        })
        if (element.kind != ElementKind.BOX) {
            numbers.addView(numberField("Fonte", element.fontSize) {
                element.fontSize = it.coerceIn(8f, 120f)
                propertyChanged()
            })
        }
        inspector.addView(numbersScroll)

        val (colorsScroll, colors) = scrollRow()
        colors.addView(label(
            if (element.kind == ElementKind.TEXT) "Cor do texto  "
            else "Preenchimento  ", 12f, muted
        ))
        palette.forEach { hex ->
            val sample = View(this).apply {
                background = rounded(Color.parseColor(hex), 7, border)
                contentDescription = "Cor " + hex
                setOnClickListener {
                    if (element.kind == ElementKind.TEXT) element.textColor = hex
                    else element.fill = hex
                    propertyChanged()
                }
            }
            colors.addView(sample, LinearLayout.LayoutParams(dp(27), dp(27)).apply {
                marginEnd = dp(9)
            })
        }
        inspector.addView(colorsScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
    }

    private fun field(initial: String, numeric: Boolean,
        onValue: (String) -> Unit): EditText =
        EditText(this).apply {
            setSingleLine(true)
            setText(initial)
            textSize = 13f
            setTextColor(ink)
            setHintTextColor(muted)
            background = rounded(background, 8, border)
            setPadding(dp(10), 0, dp(10), 0)
            if (numeric) {
                inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int,
                    count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int,
                    before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    onValue(s?.toString() ?: "")
                }
            })
        }

    private fun numberField(title: String, initial: Float,
        changed: (Float) -> Unit): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(8), 0)
        }
        column.addView(label(title, 11f, muted))
        val input = field(initial.roundToInt().toString(), true) { raw ->
            raw.replace(',', '.').toFloatOrNull()?.let { changed(it) }
        }
        column.addView(input, LinearLayout.LayoutParams(dp(69), dp(40)))
        return column
    }

    private fun propertyChanged() {
        canvasView.invalidate()
        rememberChange()
    }

    private fun rememberChange() {
        val next = project.toJson().toString()
        if (history.getOrNull(historyIndex) == next) return
        while (history.size > historyIndex + 1) history.removeAt(history.lastIndex)
        history.add(next)
        if (history.size > 70) history.removeAt(0)
        historyIndex = history.lastIndex
        autosave()
    }

    private fun autosave() {
        getSharedPreferences("zeno", MODE_PRIVATE).edit()
            .putString("autosave", project.toJson().toString()).apply()
    }

    private fun restoreHistory(index: Int) {
        if (index !in history.indices) return
        historyIndex = index
        project.restore(JSONObject(history[index]))
        canvasView.select(null)
        canvasView.invalidate()
        autosave()
    }

    private fun undo() = restoreHistory(historyIndex - 1)
    private fun redo() = restoreHistory(historyIndex + 1)

    private fun showLayers() {
        if (project.elements.isEmpty()) {
            notify("Nenhuma camada no projeto")
            return
        }
        val reversed = project.elements.asReversed()
        val names = reversed.map {
            val kind = when (it.kind) {
                ElementKind.TEXT -> "Texto"
                ElementKind.BOX -> "Forma"
                ElementKind.BUTTON -> "Botão"
            }
            kind + if (it.text.isEmpty()) "" else ": " + it.text.take(24)
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Camadas (superior primeiro)")
            .setItems(names) { _, which ->
                canvasView.select(reversed[which].id)
            }.setNegativeButton("Fechar", null).show()
    }

    private fun chooseCanvasSize() {
        val names = arrayOf("Celular · 390 × 844", "Tablet · 768 × 1024",
            "Desktop · 1200 × 800")
        val sizes = arrayOf(390 to 844, 768 to 1024, 1200 to 800)
        AlertDialog.Builder(this).setTitle("Formato do canvas")
            .setItems(names) { _, index ->
                project.pageWidth = sizes[index].first
                project.pageHeight = sizes[index].second
                canvasView.fitToScreen()
                rememberChange()
                showInspector(canvasView.selected())
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun showPreview() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(surface)
        }
        toolbar.addView(label("Pré-visualização", 16f).apply {
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        toolbar.addView(action("Fechar") { dialog.dismiss() })
        root.addView(toolbar)
        val browser = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            setBackgroundColor(Color.WHITE)
            loadDataWithBaseURL(null, HtmlExporter.generate(project),
                "text/html", "UTF-8", null)
        }
        root.addView(browser, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        dialog.setContentView(root)
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT)
        dialog.setOnDismissListener { browser.destroy() }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT)
    }

    private fun saveProject() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "ZENO-project.json")
        }
        startActivityForResult(intent, REQUEST_SAVE)
    }

    private fun exportWebsite() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "ZENO-website.zip")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun openProject() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, REQUEST_OPEN)
    }

    @Deprecated("Android document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                REQUEST_OPEN -> {
                    val jsonText = contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: error("Arquivo vazio")
                    val obj = JSONObject(jsonText)
                    require(obj.optString("format") == "zeno-project") {
                        "Formato não reconhecido"
                    }
                    project.restore(obj)
                    canvasView.select(null)
                    canvasView.fitToScreen()
                    history.clear()
                    history.add(project.toJson().toString())
                    historyIndex = 0
                    autosave()
                    notify("Projeto aberto")
                }
                REQUEST_SAVE -> {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(project.toJson().toString(2).toByteArray(Charsets.UTF_8))
                    } ?: error("Não foi possível salvar")
                    notify("Projeto JSON salvo")
                }
                REQUEST_EXPORT -> {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        ZipOutputStream(stream).use { zip ->
                            zip.putNextEntry(ZipEntry("index.html"))
                            zip.write(HtmlExporter.generate(project)
                                .toByteArray(Charsets.UTF_8))
                            zip.closeEntry()
                            zip.putNextEntry(ZipEntry("project.zeno.json"))
                            zip.write(project.toJson().toString(2)
                                .toByteArray(Charsets.UTF_8))
                            zip.closeEntry()
                        }
                    } ?: error("Não foi possível exportar")
                    notify("Site exportado em ZIP")
                }
            }
        } catch (error: Exception) {
            notify("Erro: " + (error.message ?: "operação não concluída"))
        }
    }

    private fun notify(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQUEST_SAVE = 200
        private const val REQUEST_EXPORT = 201
        private const val REQUEST_OPEN = 202
    }
}
