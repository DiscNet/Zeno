package com.zeno.studio

import java.util.Locale

object HtmlExporter {
    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun cssColor(s: String): String =
        if (Regex("^#[0-9a-fA-F]{6}$").matches(s)) s else "#111827"

    private fun pct(value: Float, whole: Int): String =
        String.format(Locale.US, "%.3f%%", value / whole * 100f)

    fun generate(project: DesignProject): String {
        val out = StringBuilder()
        out.append("<!doctype html>\n<html lang=\"en\">\n<head>\n")
        out.append("<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
        out.append("<title>ZENO project</title>\n<style>\n")
        out.append("*{box-sizing:border-box}body{margin:0;min-height:100vh;background:#f2f3f5;font-family:Arial,sans-serif}")
        out.append(".stage{position:relative;width:min(100%,")
        out.append(project.pageWidth)
        out.append("px);aspect-ratio:")
        out.append(project.pageWidth)
        out.append("/")
        out.append(project.pageHeight)
        out.append(";margin:0 auto;background:#fff;overflow:hidden}")
        out.append(".item{position:absolute;margin:0;overflow:hidden}")
        out.append(".text{display:flex;align-items:center;white-space:pre-wrap;word-break:break-word}")
        out.append(".button{display:flex;align-items:center;justify-content:center;border:0;border-radius:10px;cursor:pointer}")
        out.append(".box{border-radius:10px}")
        out.append("</style>\n</head><body><main class=\"stage\">\n")
        for (element in project.elements) {
            val style = StringBuilder()
            style.append("left:").append(pct(element.x, project.pageWidth))
            style.append(";top:").append(pct(element.y, project.pageHeight))
            style.append(";width:").append(pct(element.width, project.pageWidth))
            style.append(";height:").append(pct(element.height, project.pageHeight))
            style.append(";background:").append(cssColor(element.fill))
            style.append(";color:").append(cssColor(element.textColor))
            style.append(";font-size:").append(element.fontSize.toInt()).append("px")
            when (element.kind) {
                ElementKind.TEXT -> {
                    style.append(";background:transparent")
                    out.append("<div class=\"item text\" style=\"").append(style).append("\">")
                    out.append(escape(element.text)).append("</div>\n")
                }
                ElementKind.BOX -> out.append("<div class=\"item box\" style=\"")
                    .append(style).append("\"></div>\n")
                ElementKind.BUTTON -> {
                    out.append("<button type=\"button\" class=\"item button\" style=\"")
                    out.append(style).append("\">").append(escape(element.text))
                        .append("</button>\n")
                }
            }
        }
        out.append("</main></body></html>\n")
        return out.toString()
    }
}
