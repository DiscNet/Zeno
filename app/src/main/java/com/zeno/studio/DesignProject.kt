package com.zeno.studio

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ElementKind { TEXT, BOX, BUTTON }

data class DesignElement(
    var id: String = UUID.randomUUID().toString(),
    var kind: ElementKind = ElementKind.BOX,
    var x: Float = 24f,
    var y: Float = 24f,
    var width: Float = 180f,
    var height: Float = 64f,
    var text: String = "",
    var fill: String = "#E8EBF2",
    var textColor: String = "#111827",
    var fontSize: Float = 20f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("width", width.toDouble())
        put("height", height.toDouble())
        put("text", text)
        put("fill", fill)
        put("textColor", textColor)
        put("fontSize", fontSize.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject): DesignElement {
            val kind = runCatching { ElementKind.valueOf(obj.optString("kind")) }
                .getOrDefault(ElementKind.BOX)
            return DesignElement(
                id = obj.optString("id").ifEmpty { UUID.randomUUID().toString() },
                kind = kind,
                x = obj.optDouble("x", 24.0).toFloat(),
                y = obj.optDouble("y", 24.0).toFloat(),
                width = obj.optDouble("width", 180.0).toFloat().coerceAtLeast(20f),
                height = obj.optDouble("height", 64.0).toFloat().coerceAtLeast(20f),
                text = obj.optString("text"),
                fill = obj.optString("fill", "#E8EBF2"),
                textColor = obj.optString("textColor", "#111827"),
                fontSize = obj.optDouble("fontSize", 20.0).toFloat().coerceIn(8f, 120f)
            )
        }
    }
}

class DesignProject(
    var pageWidth: Int = 390,
    var pageHeight: Int = 844,
    val elements: MutableList<DesignElement> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("format", "zeno-project")
        put("version", 1)
        put("pageWidth", pageWidth)
        put("pageHeight", pageHeight)
        put("elements", JSONArray().also { array ->
            elements.forEach { array.put(it.toJson()) }
        })
    }

    fun restore(json: JSONObject) {
        pageWidth = json.optInt("pageWidth", 390).coerceIn(240, 1920)
        pageHeight = json.optInt("pageHeight", 844).coerceIn(320, 4096)
        elements.clear()
        val array = json.optJSONArray("elements") ?: JSONArray()
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { elements.add(DesignElement.fromJson(it)) }
        }
    }

    companion object {
        fun starter(): DesignProject = DesignProject().apply {
            elements.add(DesignElement(kind = ElementKind.TEXT, x = 28f, y = 64f,
                width = 328f, height = 58f, text = "Your next big idea.",
                fontSize = 28f, fill = "#FFFFFF"))
            elements.add(DesignElement(kind = ElementKind.TEXT, x = 28f, y = 134f,
                width = 330f, height = 52f, text = "Designed visually with ZENO.",
                fontSize = 17f, textColor = "#6B7280", fill = "#FFFFFF"))
            elements.add(DesignElement(kind = ElementKind.BUTTON, x = 28f, y = 218f,
                width = 214f, height = 54f, text = "Get started", fontSize = 17f,
                fill = "#15171C", textColor = "#FFFFFF"))
            elements.add(DesignElement(kind = ElementKind.BOX, x = 28f, y = 314f,
                width = 334f, height = 225f, fill = "#ECEEF3"))
        }
    }
}
