package com.screencensor.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.screencensor.model.CensorConfig
import com.screencensor.model.CensorStyle
import com.screencensor.model.DetailedCategory
import com.screencensor.model.DetectionBox
import kotlin.math.sin

class CensorOverlayView(context: Context) : View(context) {

    private var activeDetections: List<DetectionBox> = emptyList()
    var config: CensorConfig = CensorConfig()

    // Base Paints
    private val solidBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCB71C1C") // Crimson Red Badge
        style = Paint.Style.FILL
    }

    // Glitch Paints
    private val glitchBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F00A0E17") // Deep Cyber Blue
        style = Paint.Style.FILL
    }
    private val glitchCyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val glitchMagentaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF0055")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    // Caution Tape Paints
    private val tapeYellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        style = Paint.Style.FILL
    }
    private val tapeBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111111")
        style = Paint.Style.FILL
    }
    private val tapePath = Path()

    // Mosaic Paint
    private val mosaicTilePaint = Paint().apply {
        style = Paint.Style.FILL
    }

    // Frosted Blur Paint
    private val blurGlassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6181F28")
        style = Paint.Style.FILL
    }
    private val blurBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4488AACC")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Solid Neon Glow Paint
    private val neonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val drawRect = RectF()
    private val textPillRect = RectF()

    fun updateDetections(detections: List<DetectionBox>) {
        this.activeDetections = detections
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (activeDetections.isEmpty()) return

        val screenW = width.toFloat()
        val screenH = height.toFloat()
        val timeMs = System.currentTimeMillis()

        for (detection in activeDetections) {
            val r = detection.rect

            // Box Expansion / Padding
            val padPercent = config.boxPaddingPercent
            val padX = (r.right - r.left) * padPercent
            val padY = (r.bottom - r.top) * padPercent

            val left = (r.left - padX).coerceAtLeast(0f) * screenW
            val top = (r.top - padY).coerceAtLeast(0f) * screenH
            val right = (r.right + padX).coerceAtMost(1f) * screenW
            val bottom = (r.bottom + padY).coerceAtMost(1f) * screenH

            drawRect.set(left, top, right, bottom)
            val boxW = drawRect.width()
            val boxH = drawRect.height()
            if (boxW <= 0f || boxH <= 0f) continue

            // 1. Specialized Eye Bar (Classic Anime Eye Censor)
            if (detection.category == DetailedCategory.EYE) {
                drawEyeBar(canvas, drawRect)
                continue
            }

            // 2. Render chosen Visual Style
            when (config.style) {
                CensorStyle.GLITCH -> drawGlitch(canvas, drawRect, timeMs)
                CensorStyle.CAUTION_TAPE -> drawCautionTape(canvas, drawRect)
                CensorStyle.MOSAIC -> drawMosaic(canvas, drawRect)
                CensorStyle.BLUR -> drawBlur(canvas, drawRect)
                CensorStyle.SOLID_GLOW -> drawSolidGlow(canvas, drawRect, timeMs)
            }

            // 3. Render Text Stamp ("CENSORED", "BLOCKED", etc.)
            if (config.showText && boxW > 60f && boxH > 35f) {
                drawTextStamp(canvas, drawRect, detection.phrase)
            }
        }
    }

    private fun drawEyeBar(canvas: Canvas, rect: RectF) {
        // Sleek black eye bar
        canvas.drawRoundRect(rect, 8f, 8f, solidBlackPaint)
        // Red neon accent line across center
        val midY = rect.centerY()
        glitchMagentaPaint.strokeWidth = 2f
        canvas.drawLine(rect.left, midY, rect.right, midY, glitchMagentaPaint)
    }

    private fun drawGlitch(canvas: Canvas, rect: RectF, timeMs: Long) {
        // Base dark cyber fill
        canvas.drawRoundRect(rect, 10f, 10f, glitchBasePaint)

        // RGB Shift glitch lines
        val pulse = ((sin(timeMs / 100.0) + 1.0) / 2.0).toFloat()
        val numLines = (rect.height() / 14f).toInt().coerceIn(3, 12)
        val step = rect.height() / numLines

        for (i in 0 until numLines) {
            val y = rect.top + i * step + (timeMs % 10)
            if (y < rect.bottom) {
                val paint = if (i % 2 == 0) glitchCyanPaint else glitchMagentaPaint
                paint.strokeWidth = if (i % 3 == 0) 3f else 1.5f
                val offset = ((sin(i + timeMs / 50.0) * 6f) * pulse).toFloat()
                canvas.drawLine(rect.left + offset, y, rect.right + offset, y, paint)
            }
        }

        // Animated neon border
        glitchCyanPaint.strokeWidth = 3f
        canvas.drawRoundRect(rect, 10f, 10f, glitchCyanPaint)
    }

    private fun drawCautionTape(canvas: Canvas, rect: RectF) {
        canvas.save()
        canvas.clipRect(rect)

        // Fill background with warning yellow
        canvas.drawRect(rect, tapeYellowPaint)

        // Draw 45-degree diagonal hazard stripes
        val stripeWidth = 24f
        val totalSpan = rect.width() + rect.height()
        var currentX = rect.left - rect.height()

        while (currentX < rect.right + rect.height()) {
            tapePath.reset()
            tapePath.moveTo(currentX, rect.bottom)
            tapePath.lineTo(currentX + stripeWidth, rect.bottom)
            tapePath.lineTo(currentX + stripeWidth + rect.height(), rect.top)
            tapePath.lineTo(currentX + rect.height(), rect.top)
            tapePath.close()

            canvas.drawPath(tapePath, tapeBlackPaint)
            currentX += stripeWidth * 2f
        }

        // Heavy dark border
        tapeBlackPaint.style = Paint.Style.STROKE
        tapeBlackPaint.strokeWidth = 4f
        canvas.drawRect(rect, tapeBlackPaint)
        tapeBlackPaint.style = Paint.Style.FILL

        canvas.restore()
    }

    private fun drawMosaic(canvas: Canvas, rect: RectF) {
        canvas.save()
        canvas.clipRect(rect)

        val tileSize = 16f
        val cols = (rect.width() / tileSize).toInt().coerceAtLeast(2)
        val rows = (rect.height() / tileSize).toInt().coerceAtLeast(2)
        val cellW = rect.width() / cols
        val cellH = rect.height() / rows

        // Deterministic pseudo-random pattern for realistic mosaic look
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val hash = ((r * 31 + c * 17) % 5)
                val colorVal = when (hash) {
                    0 -> Color.parseColor("#333333")
                    1 -> Color.parseColor("#4F4F4F")
                    2 -> Color.parseColor("#666666")
                    3 -> Color.parseColor("#262626")
                    else -> Color.parseColor("#3D3D3D")
                }
                mosaicTilePaint.color = colorVal
                canvas.drawRect(
                    rect.left + c * cellW,
                    rect.top + r * cellH,
                    rect.left + (c + 1) * cellW,
                    rect.top + (r + 1) * cellH,
                    mosaicTilePaint
                )
            }
        }
        canvas.restore()
    }

    private fun drawBlur(canvas: Canvas, rect: RectF) {
        // Layered translucent rounded frosted glass
        canvas.drawRoundRect(rect, 14f, 14f, blurGlassPaint)
        canvas.drawRoundRect(rect, 14f, 14f, blurBorderPaint)
    }

    private fun drawSolidGlow(canvas: Canvas, rect: RectF, timeMs: Long) {
        // Deep pure black core
        canvas.drawRoundRect(rect, 12f, 12f, solidBlackPaint)

        // Pulsing Neon Glow border (Red / Cyan cycle)
        val alpha = (140 + (sin(timeMs / 180.0) * 100).toInt()).coerceIn(40, 255)
        neonBorderPaint.color = Color.argb(alpha, 255, 0, 64)
        canvas.drawRoundRect(rect, 12f, 12f, neonBorderPaint)
    }

    private fun drawTextStamp(canvas: Canvas, rect: RectF, text: String) {
        val textSize = (rect.height() * 0.35f).coerceIn(16f, 32f)
        textPaint.textSize = textSize

        val textWidth = textPaint.measureText(text)
        val paddingX = 14f
        val paddingY = 8f

        val pillLeft = (rect.centerX() - textWidth / 2f - paddingX).coerceAtLeast(rect.left + 4f)
        val pillRight = (rect.centerX() + textWidth / 2f + paddingX).coerceAtMost(rect.right - 4f)
        val pillTop = rect.centerY() - textSize / 2f - paddingY
        val pillBottom = rect.centerY() + textSize / 2f + paddingY

        textPillRect.set(pillLeft, pillTop, pillRight, pillBottom)

        // Badge pill
        canvas.drawRoundRect(textPillRect, 6f, 6f, textBgPaint)

        // Centered bold text
        val textY = rect.centerY() + textSize * 0.35f
        canvas.drawText(text, rect.centerX(), textY, textPaint)
    }
}
