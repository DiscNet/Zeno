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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val ink = Color.rgb(245, 246, 250)
    private val muted = Color.rgb(170, 175, 188)
    private val editorBg = Color.rgb(17, 18, 23)
    private val surface = Color.rgb(26, 28, 35)
    private val border = Color.rgb(55, 58, 69)
    private val accent = Color.rgb(112, 130, 255)
    private val palette = listOf("#15171C", "#FFFFFF", "#606AFF", "#DDE2F0",
        "#F4A261", "#2A9D8F", "#E76F83", "#1F2937")

    private lateinit var project: DesignProject
    private lateinit var canvasView: EditorCanvas
    private lateinit var inspector: LinearLayout
    private lateinit var inspectorScroller: ScrollView
    private lateinit var extras: EditorExtras
    private val history = mutableListOf<String>()
    private var historyIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = editorBg
        window.navigationBarColor = editorBg
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

    private fun rounded(fill: Int, radius: Int = 5, stroke: Int? = null): GradientDrawable =
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
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(if (primary) accent else surface, 4,
                if (primary) null else border)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)
            ).apply { marginEnd = dp(7) }
            setOnClickListener { onClick() }
            isClickable = true
        }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(editorBg)
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
        tools.addView(action("+ Imagem") { extras.pickImage() })
        tools.addView(action("+ Vidro") { canvasView.add(ElementKind.GLASS) })
        tools.addView(action("Ordenar") { extras.showLayers() })
        tools.addView(action("Fundo") { extras.showBackground() })
        tools.addView(action("Guias") {
            canvasView.snapToGuides = !canvasView.snapToGuides
            notify(if (canvasView.snapToGuides) "Guias ativas" else "Guias desligadas")
        })
        tools.addView(action("Tela") { chooseCanvasSize() })
        tools.addView(action("+ Seção ↓") { canvasView.appendScreen() })
        tools.addView(action("Seções") { showSections() })
        tools.addView(action("Ajustar") { canvasView.fitToScreen() })
        toolScroll.addView(tools)
        root.addView(toolScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(51)))

        canvasView = EditorCanvas(this, project, { showInspector(it) }, {
            rememberChange()
            showInspector(canvasView.selected())
        })
        extras = EditorExtras(this, project, canvasView) {
            propertyChanged()
            showInspector(canvasView.selected())
        }
        root.addView(canvasView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        inspector = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        inspectorScroller = ScrollView(this).apply {
            setBackgroundColor(surface)
            isVerticalScrollBarEnabled = false
            addView(inspector)
        }
        root.addView(inspectorScroller, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))
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
        val layout = inspectorScroller.layoutParams
        layout.height = dp(if (element == null) 78 else 211)
        inspectorScroller.layoutParams = layout
        inspectorScroller.scrollTo(0, 0)

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
                ElementKind.IMAGE -> "Imagem"
                    ElementKind.GLASS -> "Vidro"
                },
            14f
        ).apply { setTypeface(typeface, Typeface.BOLD) }
        heading.addView(title, LinearLayout.LayoutParams(0, dp(35), 1f))
        if (element != null) {
            heading.addView(action("Ordenar") { extras.showLayers() })
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

        if (element.kind == ElementKind.TEXT || element.kind == ElementKind.BUTTON) {
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
            if (element.kind == ElementKind.IMAGE && element.aspectLocked)
                element.height = element.width / element.imageRatio.coerceAtLeast(0.05f)
            propertyChanged()
        })
        numbers.addView(numberField("H", element.height) {
            element.height = it.coerceIn(20f, project.pageHeight.toFloat())
            if (element.kind == ElementKind.IMAGE && element.aspectLocked)
                element.width = element.height * element.imageRatio.coerceAtLeast(0.05f)
            propertyChanged()
        })
        if (element.kind == ElementKind.TEXT || element.kind == ElementKind.BUTTON) {
            numbers.addView(numberField("Fonte", element.fontSize) {
                element.fontSize = it.coerceIn(8f, 120f)
                propertyChanged()
            })
        }
        if (element.kind != ElementKind.TEXT) {
            numbers.addView(numberField("Raio", element.radius) {
                element.radius = it.coerceIn(0f, 300f)
                propertyChanged()
            })
        }
        if (element.kind == ElementKind.GLASS) {
            numbers.addView(numberField("Força", element.glassStrength * 100f) {
                element.glassStrength = (it / 100f).coerceIn(0f, 1f)
                propertyChanged()
            })
            numbers.addView(numberField("Desfoque", element.glassFrost * 100f) {
                element.glassFrost = (it / 100f).coerceIn(0f,1f)
                propertyChanged()
            })
            numbers.addView(numberField("Lente", element.glassDepth * 100f) {
                element.glassDepth = (it / 100f).coerceIn(0f,1f)
                propertyChanged()
            })
            numbers.addView(numberField("RGB", element.glassDispersion * 100f) {
                element.glassDispersion = (it / 100f).coerceIn(0f,1f)
                propertyChanged()
            })
            numbers.addView(numberField("Luz", element.glassLight * 100f) {
                element.glassLight = (it / 100f).coerceIn(0f,1f)
                propertyChanged()
            })
        }
        inspector.addView(numbersScroll)

        val (toolsScroll, toolsRow) = scrollRow()
        toolsRow.addView(action("Âncoras") { showAnchorPicker(element) })
        if (element.kind == ElementKind.TEXT || element.kind == ElementKind.BUTTON) {
            toolsRow.addView(action("Cor do texto") {
                ColorPicker.show(this, element.textColor, element.opacity) { hex, alpha ->
                    element.textColor = hex
                    element.opacity = alpha
                    propertyChanged()
                }
            })
            toolsRow.addView(action("Fonte") { extras.showFontMenu(element) })
        }
        if (element.kind == ElementKind.BOX || element.kind == ElementKind.BUTTON) {
            toolsRow.addView(action("Preenchimento") {
                ColorPicker.show(this, element.fill, element.opacity) { hex, alpha ->
                    element.fill = hex
                    element.opacity = alpha
                    propertyChanged()
                }
            })
        }
        if (element.kind == ElementKind.IMAGE) {
            toolsRow.addView(action("Trocar imagem") {
                extras.pickImage(false, element.id)
            })
            toolsRow.addView(action(
                if (element.aspectLocked) "Proporção 🔒" else "Proporção 🔓") {
                element.aspectLocked = !element.aspectLocked
                if (element.aspectLocked)
                    element.imageRatio = (element.width /
                        element.height.coerceAtLeast(1f)).coerceIn(0.05f,30f)
                propertyChanged()
                showInspector(element)
            })
        }
        if (element.kind == ElementKind.GLASS) {
            toolsRow.addView(action("Transparência") {
                ColorPicker.show(this, "#E6F0FF", element.opacity) { _, alpha ->
                    element.opacity = alpha
                    propertyChanged()
                }
            })
        }
        toolsRow.addView(action(if (element.visible) "Ocultar" else "Mostrar") {
            element.visible = !element.visible
            propertyChanged()
            showInspector(element)
        })
        toolsRow.addView(action(if (element.locked) "Desbloquear" else "Bloquear") {
            element.locked = !element.locked
            propertyChanged()
            showInspector(element)
        })
        inspector.addView(toolsScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        inspector.addView(label("Use as alças de seleção para redimensionar.", 11f, muted))
    }

    private fun field(initial: String, numeric: Boolean,
        onValue: (String) -> Unit): EditText =
        EditText(this).apply {
            setSingleLine(true)
            setText(initial)
            textSize = 13f
            setTextColor(ink)
            setHintTextColor(muted)
            background = rounded(editorBg, 8, border)
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
        canvasView.invalidateGlassCache()
        rememberChange()
    }

    private fun rememberChange() {
        val next = project.toJson().toString()
        if (history.getOrNull(historyIndex) == next) return
        while (history.size > historyIndex + 1) history.removeAt(history.lastIndex)
        history.add(next)
        if (history.size > 20) history.removeAt(0)
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
        canvasView.invalidateGlassCache()
        autosave()
    }

    private fun undo() = restoreHistory(historyIndex - 1)
    private fun redo() = restoreHistory(historyIndex + 1)

    private fun showAnchorPicker(element:DesignElement) {
        val horizontal=arrayOf("Automático","Esquerda","Centro","Direita","Preencher largura")
        AlertDialog.Builder(this).setTitle("ÂNCORA HORIZONTAL")
            .setSingleChoiceItems(horizontal,element.horizontalAnchor.ordinal) { dlg,index ->
                element.horizontalAnchor=HorizontalAnchor.entries[index]
                if(element.horizontalAnchor==HorizontalAnchor.CENTER)
                    element.x=(project.pageWidth-element.width)/2f
                propertyChanged()
                dlg.dismiss()
                val vertical=arrayOf("Automático","Topo","Centro da seção","Base")
                AlertDialog.Builder(this).setTitle("ÂNCORA VERTICAL")
                    .setSingleChoiceItems(vertical,element.verticalAnchor.ordinal) { second,choice ->
                        element.verticalAnchor=VerticalAnchor.entries[choice]
                        if(element.verticalAnchor==VerticalAnchor.CENTER) {
                            val sh=project.sectionHeight()
                            val section=(element.y/sh).toInt().coerceIn(0,project.screens-1)
                            element.y=section*sh+(sh-element.height)/2f
                        }
                        propertyChanged()
                        second.dismiss()
                        showInspector(element)
                    }.setNegativeButton("Manter",null).show()
            }.setNegativeButton("Cancelar",null).show()
    }

    private fun showSections() {
        val choices=Array(project.screens) { i -> "Seção " + (i+1) }
        AlertDialog.Builder(this).setTitle("NAVEGAÇÃO VERTICAL")
            .setItems(choices) { _,index -> canvasView.scrollToSection(index) }
            .setPositiveButton("Adicionar abaixo") { _,_ -> canvasView.appendScreen() }
            .setNegativeButton("Fechar",null).show()
    }

    private fun chooseCanvasSize() {
        val names = arrayOf("Celular · 390 × 844", "Tablet · 768 × 1024",
            "Desktop · 1200 × 800")
        val sizes = arrayOf(390 to 844, 768 to 1024, 1200 to 800)
        AlertDialog.Builder(this).setTitle("Formato do canvas")
            .setItems(names) { _, index ->
                ResponsiveLayout.apply(project, sizes[index].first, sizes[index].second)
                canvasView.invalidateGlassCache()
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
        if (extras.consumeActivityResult(requestCode, resultCode, data)) return
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
                    MediaAssets.clear()
                    canvasView.invalidateGlassCache()
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
