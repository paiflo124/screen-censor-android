package com.screencensor.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.screencensor.model.DetectionBox

class CensorOverlayView(context: Context) : View(context) {

    private val censorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private var activeDetections: List<DetectionBox> = emptyList()
    private val drawRect = RectF()

    fun updateDetections(detections: List<DetectionBox>) {
        this.activeDetections = detections
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (activeDetections.isEmpty()) return

        val screenW = width.toFloat()
        val screenH = height.toFloat()

        for (detection in activeDetections) {
            val r = detection.rect

            // Expand box slightly (padding) so censorship is 100% solid
            val padX = (r.right - r.left) * 0.05f
            val padY = (r.bottom - r.top) * 0.05f

            val left = (r.left - padX).coerceAtLeast(0f) * screenW
            val top = (r.top - padY).coerceAtLeast(0f) * screenH
            val right = (r.right + padX).coerceAtMost(1f) * screenW
            val bottom = (r.bottom + padY).coerceAtMost(1f) * screenH

            drawRect.set(left, top, right, bottom)

            // Draw solid censor rectangle with smooth rounded corners
            canvas.drawRoundRect(drawRect, 16f, 16f, censorPaint)
            canvas.drawRoundRect(drawRect, 16f, 16f, borderPaint)
        }
    }
}
