package com.thejusticeman.glyphos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor
import kotlin.math.hypot

private const val LONG_PRESS_MS = 800L
private const val LONG_PRESS_CANCEL_PX = 10.0
private const val CLEAR_CANVAS_MS = 400L
private const val TRAIL_SEGMENTS = 5
private const val TRAIL_MIN_OPACITY = 0.15
private const val TRAIL_OPACITY_RANGE = 0.85
private const val TRAIL_MAX_WIDTH = 7.5
private const val TRAIL_WIDTH_RANGE = 5.0
private const val TAP_MAX_MOVEMENT_DP = 12
private const val LABEL_MIN_ICON_DP = 58

class GestureCanvasView(context: Context) : View(context) {
  var onGestureComplete: ((List<Point>) -> Unit)? = null
  var onLongPressOpenManagement: (() -> Unit)? = null
  var onIconTapped: ((AppDetail) -> Unit)? = null
  var onCanvasSizeChanged: (() -> Unit)? = null
  var launcherIcons: List<LauncherIconNode> = emptyList()
    set(value) {
      field = value
      invalidate()
    }
  var trailEffect: Boolean = false
    set(value) {
      field = value
      invalidate()
    }

  private val rawPoints = mutableListOf<Point>()
  private val path = Path()
  private val iconBounds = Rect()
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.themeColor(android.R.attr.colorAccent, Color.WHITE)
    style = Paint.Style.STROKE
    strokeWidth = 3f
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textAlign = Paint.Align.CENTER
    textSize = dp(11).toFloat()
    setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
  }
  private val fallbackIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.themeColor(android.R.attr.colorAccent, Color.WHITE)
    alpha = 180
  }

  private var downX = 0f
  private var downY = 0f
  private var longPressTriggered = false

  private val longPressRunnable = Runnable {
    longPressTriggered = true
    clearNow()
    onLongPressOpenManagement?.invoke()
  }

  init {
    isFocusable = true
    isClickable = true
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        removeCallbacks(longPressRunnable)
        longPressTriggered = false
        downX = event.x
        downY = event.y
        rawPoints.clear()
        path.reset()
        rawPoints += Point(event.x.toDouble(), event.y.toDouble())
        path.moveTo(event.x, event.y)
        postDelayed(longPressRunnable, LONG_PRESS_MS)
        invalidate()
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        if (longPressTriggered) return true

        val totalMovement = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())
        if (totalMovement > LONG_PRESS_CANCEL_PX) {
          removeCallbacks(longPressRunnable)
        }

        rawPoints += Point(event.x.toDouble(), event.y.toDouble())
        path.lineTo(event.x, event.y)
        invalidate()
        return true
      }

      MotionEvent.ACTION_UP -> {
        removeCallbacks(longPressRunnable)
        if (longPressTriggered) {
          longPressTriggered = false
          clearNow()
          return true
        }

        if (isTap(event.x, event.y)) {
          hitTestIcon(event.x, event.y)?.let { icon ->
            clearNow()
            onIconTapped?.invoke(icon.app)
            return true
          }
        }

        normalizeTo40Points(rawPoints)?.let { onGestureComplete?.invoke(it) }
        postDelayed({ clearNow() }, CLEAR_CANVAS_MS)
        return true
      }

      MotionEvent.ACTION_CANCEL -> {
        removeCallbacks(longPressRunnable)
        longPressTriggered = false
        clearNow()
        return true
      }
    }
    return true
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    drawLauncherIcons(canvas)
    if (trailEffect) {
      drawTrail(canvas)
    } else {
      canvas.drawPath(path, paint)
    }
  }

  override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
    super.onSizeChanged(width, height, oldWidth, oldHeight)
    if (width != oldWidth || height != oldHeight) {
      onCanvasSizeChanged?.invoke()
    }
  }

  private fun clearNow() {
    rawPoints.clear()
    path.reset()
    invalidate()
  }

  private fun isTap(x: Float, y: Float): Boolean {
    val movement = hypot((x - downX).toDouble(), (y - downY).toDouble())
    return movement <= dp(TAP_MAX_MOVEMENT_DP)
  }

  private fun hitTestIcon(x: Float, y: Float): LauncherIconNode? {
    for (icon in launcherIcons.asReversed()) {
      val dx = x - icon.x.toFloat()
      val dy = y - icon.y.toFloat()
      if (hypot(dx.toDouble(), dy.toDouble()) <= icon.radiusPx) {
        return icon
      }
    }
    return null
  }

  private fun drawLauncherIcons(canvas: Canvas) {
    for (icon in launcherIcons) {
      val halfSize = (icon.sizePx / 2.0).toInt()
      val left = icon.x.toInt() - halfSize
      val top = icon.y.toInt() - halfSize
      val right = icon.x.toInt() + halfSize
      val bottom = icon.y.toInt() + halfSize
      iconBounds.set(left, top, right, bottom)

      val drawable = icon.app.icon
      if (drawable == null) {
        canvas.drawCircle(icon.x.toFloat(), icon.y.toFloat(), icon.radiusPx.toFloat(), fallbackIconPaint)
      } else {
        drawable.setBounds(iconBounds)
        drawable.draw(canvas)
      }

      if (icon.sizePx >= dp(LABEL_MIN_ICON_DP)) {
        drawIconLabel(canvas, icon)
      }
    }
  }

  private fun drawIconLabel(canvas: Canvas, icon: LauncherIconNode) {
    val maxWidth = (icon.sizePx * 1.25).toFloat()
    val label = icon.app.label
    val measuredCount = labelPaint.breakText(label, true, maxWidth, null)
    val visibleLabel = if (measuredCount < label.length && measuredCount > 1) {
      label.take(measuredCount - 1) + "..."
    } else {
      label
    }
    val baseline = (icon.y + icon.radiusPx + dp(14)).toFloat().coerceAtMost(height - dp(6).toFloat())
    canvas.drawText(visibleLabel, icon.x.toFloat(), baseline, labelPaint)
  }

  private fun drawTrail(canvas: Canvas) {
    if (rawPoints.size < 2) return

    val originalAlpha = paint.alpha
    val originalWidth = paint.strokeWidth
    val count = rawPoints.size

    for (segment in 0 until TRAIL_SEGMENTS) {
      val startIndex = floor(segment * (count - 1).toDouble() / TRAIL_SEGMENTS).toInt()
      val endIndex = minOf(
        count - 1,
        floor((segment + 1) * (count - 1).toDouble() / TRAIL_SEGMENTS).toInt(),
      )
      if (endIndex <= startIndex) continue

      val t = segment.toDouble() / (TRAIL_SEGMENTS - 1)
      paint.alpha = ((TRAIL_MIN_OPACITY + t * TRAIL_OPACITY_RANGE) * 255).toInt()
      paint.strokeWidth = (TRAIL_MAX_WIDTH - t * TRAIL_WIDTH_RANGE).toFloat()

      val segmentPath = Path()
      val first = rawPoints[startIndex]
      segmentPath.moveTo(first.x.toFloat(), first.y.toFloat())
      for (index in startIndex + 1..endIndex) {
        val point = rawPoints[index]
        segmentPath.lineTo(point.x.toFloat(), point.y.toFloat())
      }
      canvas.drawPath(segmentPath, paint)
    }

    paint.alpha = originalAlpha
    paint.strokeWidth = originalWidth
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
