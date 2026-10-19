package com.zeno.studio

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale

object ColorPicker {
    private fun dp(a: Activity, n: Int): Int = (n*a.resources.displayMetrics.density+0.5f).toInt()
    private fun hex(c: Int) = String.format(Locale.US, "#%02X%02X%02X",
        Color.red(c), Color.green(c), Color.blue(c))

    fun show(a: Activity, initial: String, alpha: Float = 1f,
        apply: (String, Float) -> Unit) {
        val initialColor = runCatching { Color.parseColor(initial) }.getOrDefault(Color.WHITE)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 18), dp(a, 15), dp(a, 18), dp(a, 5))
            setBackgroundColor(Color.rgb(29, 30, 37))
        }
        root.addView(TextView(a).apply {
            text = "SELETOR DE CORES"
            textSize = 13f; setTextColor(Color.WHITE)
            letterSpacing = 0.07f
        }, LinearLayout.LayoutParams(-1, dp(a, 37)))
        val preview = View(a)
        fun updatePreview(c: Int) {
            preview.background = GradientDrawable().apply {
                setColor(c); cornerRadius = dp(a, 4).toFloat()
            }
        }
        updatePreview(initialColor)
        val hexInput = EditText(a).apply {
            setSingleLine(true); textSize = 15f
            setTextColor(Color.WHITE); setSelectAllOnFocus(true)
            setText(hex(initialColor))
            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 19, 25))
                cornerRadius = dp(a, 4).toFloat()
            }
            setPadding(dp(a, 10), 0, dp(a, 10), 0)
        }
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        var syncing = false
        val selector = PickerView(a, hsv) { c ->
            updatePreview(c)
            if (!syncing) {
                syncing = true
                hexInput.setText(hex(c))
                hexInput.setSelection(hexInput.length())
                syncing = false
            }
        }
        root.addView(selector, LinearLayout.LayoutParams(-1, dp(a, 250)))
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(preview, LinearLayout.LayoutParams(dp(a, 38), dp(a, 38)).apply {
            marginEnd = dp(a, 10)
        })
        row.addView(hexInput, LinearLayout.LayoutParams(0, dp(a, 40), 1f))
        root.addView(row, LinearLayout.LayoutParams(-1, dp(a, 55)))
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int,
                after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int,
                count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (syncing) return
                val v = s?.toString()?.trim() ?: return
                if (!v.matches(Regex("#?[0-9a-fA-F]{6}"))) return
                val c = runCatching { Color.parseColor(if (v[0]=='#') v else "#"+v) }
                    .getOrNull() ?: return
                syncing = true
                selector.setColor(c)
                updatePreview(c)
                syncing = false
            }
        })
        val alphaLabel = TextView(a).apply {
            textSize = 12f
            setTextColor(Color.LTGRAY)
            text = "OPACIDADE · "+(alpha.coerceIn(0f,1f)*100).toInt()+"%"
        }
        root.addView(alphaLabel)
        val alphaBar = SeekBar(a).apply {
            max = 100; progress = (alpha.coerceIn(0f,1f)*100).toInt()
        }
        alphaBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int,
                fromUser: Boolean) { alphaLabel.text = "OPACIDADE · "+value+"%" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(alphaBar, LinearLayout.LayoutParams(-1, dp(a, 45)))
        AlertDialog.Builder(a).setView(root).setNegativeButton("Cancelar",null)
            .setPositiveButton("Aplicar") { _, _ ->
                apply(hex(selector.currentColor()), alphaBar.progress/100f)
            }.show()
    }

    private class PickerView(
        a: Activity,
        private val hsv: FloatArray,
        private val update: (Int) -> Unit
    ) : View(a) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val box = RectF()
        private val density = a.resources.displayMetrics.density
        private var hueTop = 0f
        private var squareBottom = 0f
        private val padding = 2f*density
        fun currentColor(): Int = Color.HSVToColor(hsv)
        fun setColor(color: Int) {
            Color.colorToHSV(color, hsv); invalidate()
        }
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat(); val h = height.toFloat()
            squareBottom = h*0.77f
            hueTop = squareBottom+15f*density
            box.set(padding, padding, w-padding, squareBottom)
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(box.left,0f,box.right,0f,
                Color.WHITE,Color.HSVToColor(floatArrayOf(hsv[0],1f,1f)),Shader.TileMode.CLAMP)
            c.drawRoundRect(box,3f,3f,p)
            p.shader = LinearGradient(0f,box.top,0f,box.bottom,
                Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP)
            c.drawRoundRect(box,3f,3f,p)
            p.shader = null
            p.color = Color.WHITE; p.style = Paint.Style.STROKE; p.strokeWidth=3f
            c.drawCircle(box.left+box.width()*hsv[1],
                box.top+box.height()*(1f-hsv[2]),8f*density,p)
            p.style=Paint.Style.FILL
            p.shader=LinearGradient(padding,0f,w-padding,0f,
                intArrayOf(Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,
                    Color.BLUE,Color.MAGENTA,Color.RED),
                floatArrayOf(0f,1f/6f,2f/6f,3f/6f,4f/6f,5f/6f,1f),
                Shader.TileMode.CLAMP)
            val slider = RectF(padding,hueTop,w-padding,hueTop+15f*density)
            c.drawRoundRect(slider,3f,3f,p); p.shader=null
            val marker = padding+(w-2f*padding)*hsv[0]/360f
            p.color=Color.WHITE
            c.drawCircle(marker,slider.centerY(),9f*density,p)
            p.color=currentColor()
            c.drawCircle(marker,slider.centerY(),6f*density,p)
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val x=e.x.coerceIn(padding,width-padding)
                    if(e.y>=hueTop-9f*density)
                        hsv[0]=((x-padding)/(width-2f*padding)*360f).coerceIn(0f,359.99f)
                    else {
                        hsv[1]=((x-padding)/(width-2f*padding)).coerceIn(0f,1f)
                        hsv[2]=(1f-(e.y-padding)/(squareBottom-padding)).coerceIn(0f,1f)
                    }
                    update(currentColor()); invalidate(); return true
                }
                MotionEvent.ACTION_UP -> { performClick(); return true }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }
}
