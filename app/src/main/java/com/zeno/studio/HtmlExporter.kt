package com.zeno.studio

import java.util.Locale

object HtmlExporter {
    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    private fun cssColor(s: String): String =
        if (Regex("^#[0-9a-fA-F]{6}$").matches(s)) s else "#111827"

    private fun dataUri(s: String): String =
        if (s.startsWith("data:image/") && s.length < 12_000_000 &&
            s.substringBefore(',').matches(Regex("^data:image/(png|jpeg|webp);base64$")) &&
            s.substringAfter(',', "").matches(Regex("^[A-Za-z0-9+/=\\r\\n]+$"))) s else ""

    private fun pct(value: Float, whole: Int): String =
        String.format(Locale.US, "%.3f%%", value / whole * 100f)

    private fun num(value: Float): String = String.format(Locale.US, "%.2f", value)

    fun generate(project: DesignProject): String {
        val out = StringBuilder()
        out.append("<!doctype html>\n<html lang=\"pt-BR\"><head>\n")
        out.append("<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
        out.append("<title>ZENO Project</title>\n<style>\n")
        out.append("*{box-sizing:border-box}body{margin:0;min-height:100vh;background:#17181d;font-family:Arial,sans-serif}")
        out.append(".stage{position:relative;width:min(100%,").append(project.pageWidth)
        out.append("px);aspect-ratio:").append(project.pageWidth).append("/")
        out.append(project.pageHeight).append(";margin:0 auto;overflow:hidden;background:")
        out.append(cssColor(project.backgroundColor)).append("}")
        out.append(".item{position:absolute;margin:0;overflow:hidden}")
        out.append(".text{display:flex;align-items:center;white-space:pre-wrap;overflow-wrap:anywhere}")
        out.append(".button{display:flex;align-items:center;justify-content:center;border:0;cursor:pointer}")
        out.append(".image{display:block;object-fit:cover}")
        val registered = mutableMapOf<String, String>()
        var fontCounter = 0
        project.fonts.forEach { (key, font) ->
            val mime = when (font.mime) {
                "font/otf" -> "font/otf"
                "font/woff" -> "font/woff"
                "font/woff2" -> "font/woff2"
                else -> "font/ttf"
            }
            if (font.data.length in 8..3_000_000 &&
                font.data.matches(Regex("^[A-Za-z0-9+/=\\r\\n]+$"))) {
                val face = "ZenoFont" + fontCounter++
                registered[key] = face
                out.append("@font-face{font-family:'").append(face)
                out.append("';src:url('data:").append(mime)
                out.append(";base64,").append(font.data)
                out.append("') format('")
                out.append(when(mime) { "font/otf" -> "opentype"; "font/woff" -> "woff";
                    "font/woff2" -> "woff2"; else -> "truetype" })
                out.append("');font-display:swap}")
            }
        }
        when (project.backgroundMode) {
            BackgroundMode.SOLID -> Unit
            BackgroundMode.GRID -> {
                out.append(".stage{background-image:linear-gradient(to right,rgba(100,110,130,.23) 1px,transparent 1px),")
                out.append("linear-gradient(to bottom,rgba(100,110,130,.23) 1px,transparent 1px);")
                out.append("background-size:20px 20px}")
            }
            BackgroundMode.GRADIENT -> {
                out.append(".stage{background:linear-gradient(135deg,")
                out.append(cssColor(project.gradientStart)).append(",")
                out.append(cssColor(project.gradientEnd)).append(")}")
            }
            BackgroundMode.IMAGE -> {
                val uri = dataUri(project.backgroundImage)
                if (uri.isNotEmpty()) {
                    out.append(".stage{background-image:url('").append(uri)
                    out.append("');background-size:cover;background-position:center}")
                }
            }
        }
        out.append("</style></head><body><main class=\"stage\">\n")
        for (e in project.elements) {
            if (!e.visible) continue
            val style = StringBuilder()
            style.append("left:").append(pct(e.x, project.pageWidth))
                .append(";top:").append(pct(e.y, project.pageHeight))
                .append(";width:").append(pct(e.width, project.pageWidth))
                .append(";height:").append(pct(e.height, project.pageHeight))
                .append(";border-radius:").append(num(e.radius.coerceAtLeast(0f))).append("px")
                .append(";opacity:").append(num(e.opacity.coerceIn(0f, 1f)))
                .append(";background:").append(cssColor(e.fill))
                .append(";color:").append(cssColor(e.textColor))
                .append(";font-size:").append(num(e.fontSize)).append("px")
            registered[e.fontId]?.let { style.append(";font-family:'").append(it).append("'") }
            when (e.kind) {
                ElementKind.TEXT -> {
                    style.append(";background:transparent")
                    out.append("<div class=\"item text\" style=\"").append(style).append("\">")
                    out.append(escape(e.text)).append("</div>\n")
                }
                ElementKind.BOX -> out.append("<div class=\"item box\" style=\"")
                    .append(style).append("\"></div>\n")
                ElementKind.BUTTON -> out.append("<button class=\"item button\" type=\"button\" style=\"")
                    .append(style).append("\">").append(escape(e.text)).append("</button>\n")
                ElementKind.IMAGE -> {
                    out.append("<img alt=\"\" class=\"item image\" src=\"")
                        .append(dataUri(e.imageData)).append("\" style=\"").append(style)
                        .append("\">\n")
                }
            }
        }
        out.append("</main></body></html>\n")
        return out.toString()
    }
}
