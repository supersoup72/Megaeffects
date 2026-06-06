package com.megaeffects

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var project: Project? = null
    var currentTime: Float = 0f
    var onSeek:        ((Float) -> Unit)? = null
    var onSelectLayer: ((Int) -> Unit)? = null

    private val trackH   = dp(32f)
    private val headerW  = dp(70f)
    private val secW     = dp(60f)
    private val rulerH   = dp(20f)

    private val layerColors = listOf(
        Color.rgb(51, 128, 204),
        Color.rgb(230, 102, 51),
        Color.rgb(51, 179, 102),
        Color.rgb(179, 51, 179),
        Color.rgb(179, 179, 51),
        Color.rgb(51, 179, 179),
    )

    private val paintBg     = Paint().apply { color = Color.rgb(18, 18, 18) }
    private val paintRuler  = Paint().apply { color = Color.rgb(40, 40, 40) }
    private val paintTick   = Paint().apply { color = Color.rgb(80, 80, 80); strokeWidth = 1f }
    private val paintHead   = Paint().apply { color = Color.rgb(255, 220, 50); strokeWidth = dp(2f) }
    private val paintText   = Paint().apply {
        color = Color.WHITE; textSize = dp(10f)
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        val p = project ?: return
        val dur = p.duration

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)

        // Ruler
        canvas.drawRect(0f, 0f, width.toFloat(), rulerH, paintRuler)
        for (sec in 0..dur.toInt()) {
            val x = headerW + sec * secW
            canvas.drawLine(x, 0f, x, rulerH, paintTick)
            canvas.drawText("${sec}s", x + 2, rulerH - 4, paintText)
        }

        // Layers
        p.layers.forEachIndexed { i, layer ->
            val y = rulerH + i * trackH
            val color = layerColors[i % layerColors.size]

            // Header
            paintBg.color = adjustColor(color, 0.5f)
            canvas.drawRect(0f, y, headerW, y + trackH - 2, paintBg)
            paintText.textSize = dp(9f)
            canvas.drawText(layer.name.take(8), 4f, y + trackH * 0.65f, paintText)

            // Clip bar
            val bx = headerW + layer.start * secW
            val bw = (layer.end - layer.start) * secW
            paintBg.color = if (layer.visible) color else adjustColor(color, 0.3f)
            canvas.drawRect(bx, y + 2, bx + bw, y + trackH - 2, paintBg)
        }
        paintBg.color = Color.rgb(18, 18, 18) // reset

        // Playhead
        val px = headerW + currentTime * secW
        canvas.drawLine(px, 0f, px, height.toFloat(), paintHead)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val p = project ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (event.y < rulerH) {
                    // Seek
                    val t = ((event.x - headerW) / secW).coerceIn(0f, p.duration)
                    currentTime = t
                    onSeek?.invoke(t)
                    invalidate()
                } else {
                    // Select layer
                    val layerIdx = ((event.y - rulerH) / trackH).toInt()
                    if (layerIdx in p.layers.indices) {
                        onSelectLayer?.invoke(layerIdx)
                    }
                    // Also seek on drag
                    if (event.action == MotionEvent.ACTION_MOVE) {
                        val t = ((event.x - headerW) / secW).coerceIn(0f, p.duration)
                        currentTime = t
                        onSeek?.invoke(t)
                        invalidate()
                    }
                }
                return true
            }
        }
        return false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val p = project
        val layers = p?.layers?.size ?: 0
        val h = (rulerH + layers * trackH + trackH).toInt().coerceAtLeast(dp(80f).toInt())
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    private fun adjustColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
