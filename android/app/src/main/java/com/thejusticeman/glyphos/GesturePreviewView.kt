package com.thejusticeman.glyphos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class GesturePreviewView(context: Context) : View(context) {
  var points: List<Point> = emptyList()
    set(value) {
      field = value
      invalidate()
    }

  private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.themeColor(android.R.attr.colorAccent, Color.WHITE)
    style = Paint.Style.STROKE
    strokeWidth = 2f
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }

  private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.themeColor(android.R.attr.textColorHint, Color.LTGRAY)
    style = Paint.Style.STROKE
    strokeWidth = 1f
  }

  private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.themeColor(android.R.attr.colorBackgroundFloating, Color.WHITE)
    style = Paint.Style.FILL
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val size = resolveSize(dp(56), widthMeasureSpec)
    setMeasuredDimension(size, size)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val radius = dp(8).toFloat()
    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, backgroundPaint)
    canvas.drawRoundRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, radius, radius, borderPaint)

    if (points.size < 2) return
    val previewPath = buildPreviewPath(points, width.toFloat(), height.toFloat(), dp(6).toFloat())
    canvas.drawPath(previewPath, strokePaint)
  }

  private fun buildPreviewPath(points: List<Point>, boxWidth: Float, boxHeight: Float, padding: Float): Path {
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY

    points.forEach { point ->
      if (point.x < minX) minX = point.x
      if (point.y < minY) minY = point.y
      if (point.x > maxX) maxX = point.x
      if (point.y > maxY) maxY = point.y
    }

    val sourceWidth = (maxX - minX).takeIf { it != 0.0 } ?: 1.0
    val sourceHeight = (maxY - minY).takeIf { it != 0.0 } ?: 1.0
    val available = minOf(boxWidth, boxHeight) - padding * 2
    val scale = available / maxOf(sourceWidth, sourceHeight).toFloat()
    val offsetX = padding + (available - sourceWidth.toFloat() * scale) / 2
    val offsetY = padding + (available - sourceHeight.toFloat() * scale) / 2

    val path = Path()
    points.forEachIndexed { index, point ->
      val x = ((point.x - minX).toFloat() * scale) + offsetX
      val y = ((point.y - minY).toFloat() * scale) + offsetY
      if (index == 0) {
        path.moveTo(x, y)
      } else {
        path.lineTo(x, y)
      }
    }
    return path
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
