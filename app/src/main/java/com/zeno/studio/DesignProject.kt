package com.zeno.studio

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ElementKind { TEXT, BOX, BUTTON, IMAGE }
enum class BackgroundMode { SOLID, GRID, GRADIENT, IMAGE }

data class EmbeddedFont(
    val name: String,
    val mime: String,
    val data: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("mime", mime); put("data", data)
    }
    companion object {
        fun fromJson(obj: JSONObject): EmbeddedFont = EmbeddedFont(
            obj.optString("name", "Fonte"),
            obj.optString("mime", "font/ttf"),
            obj.optString("data", "")
        )
    }
}

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
    var fontSize: Float = 20f,
    var radius: Float = 4f,
    var opacity: Float = 1f,
    var imageData: String = "",
    var fontId: String = "",
    var visible: Boolean = true,
    var locked: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("kind", kind.name); put("x", x.toDouble()); put("y", y.toDouble())
        put("width", width.toDouble()); put("height", height.toDouble()); put("text", text)
        put("fill", fill); put("textColor", textColor); put("fontSize", fontSize.toDouble())
        put("radius", radius.toDouble()); put("opacity", opacity.toDouble())
        put("imageData", imageData); put("fontId", fontId)
        put("visible", visible); put("locked", locked)
    }

    companion object {
        fun fromJson(obj: JSONObject): DesignElement {
            val kind = runCatching { ElementKind.valueOf(obj.optString("kind")) }
                .getOrDefault(ElementKind.BOX)
            return DesignElement(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                kind = kind,
                x = obj.optDouble("x", 24.0).toFloat(),
                y = obj.optDouble("y", 24.0).toFloat(),
                width = obj.optDouble("width", 180.0).toFloat().coerceAtLeast(20f),
                height = obj.optDouble("height", 64.0).toFloat().coerceAtLeast(20f),
                text = obj.optString("text"),
                fill = obj.optString("fill", "#E8EBF2"),
                textColor = obj.optString("textColor", "#111827"),
                fontSize = obj.optDouble("fontSize", 20.0).toFloat().coerceIn(8f, 160f),
                radius = obj.optDouble("radius", if (kind == ElementKind.BUTTON) 8.0 else 4.0)
                    .toFloat().coerceIn(0f, 300f),
                opacity = obj.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                imageData = obj.optString("imageData"),
                fontId = obj.optString("fontId"),
                visible = obj.optBoolean("visible", true),
                locked = obj.optBoolean("locked", false)
            )
        }
    }
}

class DesignProject(
    var pageWidth: Int = 390,
    var pageHeight: Int = 844,
    val elements: MutableList<DesignElement> = mutableListOf(),
    var backgroundMode: BackgroundMode = BackgroundMode.SOLID,
    var backgroundColor: String = "#FFFFFF",
    var gradientStart: String = "#EDF1FF",
    var gradientEnd: String = "#FFFFFF",
    var backgroundImage: String = "",
    val fonts: MutableMap<String, EmbeddedFont> = mutableMapOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("format", "zeno-project"); put("version", 2)
        put("pageWidth", pageWidth); put("pageHeight", pageHeight)
        put("backgroundMode", backgroundMode.name); put("backgroundColor", backgroundColor)
        put("gradientStart", gradientStart); put("gradientEnd", gradientEnd)
        put("backgroundImage", backgroundImage)
        put("fonts", JSONObject().apply {
            fonts.forEach { (key, value) -> put(key, value.toJson()) }
        })
        put("elements", JSONArray().also { array -> elements.forEach { array.put(it.toJson()) } })
    }

    fun restore(json: JSONObject) {
        pageWidth = json.optInt("pageWidth", 390).coerceIn(240, 1920)
        pageHeight = json.optInt("pageHeight", 844).coerceIn(320, 4096)
        backgroundMode = runCatching {
            BackgroundMode.valueOf(json.optString("backgroundMode", "SOLID"))
        }.getOrDefault(BackgroundMode.SOLID)
        backgroundColor = json.optString("backgroundColor", "#FFFFFF")
        gradientStart = json.optString("gradientStart", "#EDF1FF")
        gradientEnd = json.optString("gradientEnd", "#FFFFFF")
        backgroundImage = json.optString("backgroundImage", "")
        fonts.clear()
        json.optJSONObject("fonts")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                obj.optJSONObject(key)?.let { fonts[key] = EmbeddedFont.fromJson(it) }
            }
        }
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
                fill = "#15171C", textColor = "#FFFFFF", radius = 6f))
            elements.add(DesignElement(kind = ElementKind.BOX, x = 28f, y = 314f,
                width = 334f, height = 225f, fill = "#ECEEF3", radius = 8f))
        }
    }
}
