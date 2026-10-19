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
        out.append(".glass{background:rgba(228,239,255,.16);")
        out.append("backdrop-filter:blur(7px) saturate(1.18);")
        out.append("-webkit-backdrop-filter:blur(7px) saturate(1.18);")
        out.append("border:1.5px solid rgba(255,255,255,.8);")
        out.append("box-shadow:inset 0 1px 1px rgba(255,255,255,.8),")
        out.append("inset 0 -1px 4px rgba(35,50,80,.12),0 8px 25px rgba(0,0,0,.07)}")
        out.append(".glass::after{content:'';position:absolute;inset:0;pointer-events:none;")
        out.append("background:linear-gradient(118deg,rgba(255,255,255,.24),transparent 48%,")
        out.append("rgba(195,219,246,.1));mix-blend-mode:screen}")
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
        // Explicit artboard breakpoints prevent elements from drifting away
        // when a design is viewed on phone, tablet and desktop.
        fun addBreakpoint(width: Int, height: Int, media: String) {
            val geometry = ResponsiveLayout.forViewport(project, width)
            if (media.isNotEmpty()) out.append("@media ").append(media).append("{")
            out.append(".stage{width:min(100%,").append(width)
                .append("px);min-height:max(100vh,").append(height)
                .append("px);aspect-ratio:auto}")
            project.elements.forEachIndexed { index, element ->
                val g = geometry[element.id] ?: return@forEachIndexed
                out.append(".z").append(index).append("{left:")
                    .append(pct(g.x,width)).append(";top:").append(num(g.y))
                    .append("px;width:").append(pct(g.w,width))
                    .append(";height:").append(num(g.h)).append("px;font-size:")
                    .append(num(g.font)).append("px}")
            }
            if (media.isNotEmpty()) out.append("}")
        }
        addBreakpoint(390,844,"")
        addBreakpoint(768,1024,"(min-width:600px)")
        addBreakpoint(1200,800,"(min-width:1024px)")
        out.append("</style></head><body><main class=\"stage\">\n")
        for ((index, e) in project.elements.withIndex()) {
            if (!e.visible) continue
            val style = StringBuilder()
            style.append("border-radius:").append(num(e.radius.coerceAtLeast(0f))).append("px")
                .append(";opacity:").append(num(e.opacity.coerceIn(0f, 1f)))
                .append(";background:").append(if (e.kind == ElementKind.GLASS) "transparent" else cssColor(e.fill))
                .append(";color:").append(cssColor(e.textColor))
                .append(";font-size:").append(num(e.fontSize)).append("px")
            registered[e.fontId]?.let { style.append(";font-family:'").append(it).append("'") }
            when (e.kind) {
                ElementKind.TEXT -> {
                    style.append(";background:transparent")
                    out.append("<div class=\"item text z").append(index).append("\" style=\"").append(style).append("\">")
                    out.append(escape(e.text)).append("</div>\n")
                }
                ElementKind.BOX -> out.append("<div class=\"item box z").append(index).append("\" style=\"")
                    .append(style).append("\"></div>\n")
                ElementKind.BUTTON -> out.append("<button class=\"item button z").append(index).append("\" type=\"button\" style=\"")
                    .append(style).append("\">").append(escape(e.text)).append("</button>\n")
                ElementKind.IMAGE -> {
                    out.append("<img alt=\"\" class=\"item image z").append(index).append("\" src=\"")
                        .append(dataUri(e.imageData)).append("\" style=\"").append(style)
                        .append("\">\n")
                }
                ElementKind.GLASS -> {
                    out.append("<div class=\"item glass z").append(index).append("\" style=\"")
                        .append(style).append(";background:rgba(230,240,255,")
                        .append(num(0.10f + e.glassStrength * 0.12f)).append(")")
                        .append(";backdrop-filter:blur(")
                        .append(num(3f + 8f * e.glassStrength))
                        .append("px) saturate(1.25);")
                        .append("-webkit-backdrop-filter:blur(")
                        .append(num(3f + 8f * e.glassStrength))
                        .append("px) saturate(1.25)\"></div>\n")
                }
            }
        }
        out.append("</main></body></html>\n")
        return out.toString()
    }
}
