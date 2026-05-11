package com.thejusticeman.glyphos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

private const val LONG_PRESS_MS = 200L
private const val LONG_PRESS_CANCEL_PX = 10.0
private const val CLEAR_CANVAS_MS = 400L
private const val TRAIL_PARTICLE_LIFETIME_MS = 650L
private const val TRAIL_PARTICLE_MAX_ALPHA = 220
private const val TRAIL_PARTICLE_RADIUS_DP = 5
private const val TAP_MAX_MOVEMENT_DP = 12
private const val LABEL_MIN_ICON_DP = 58
private const val ICON_SCALE_MIN = 0.0f
private const val ICON_SCALE_MAX = 1.0f
private const val MIN_PINCH_DISTANCE_PX = 24f
private const val PINCH_ICON_SCALE_RESPONSE = 2.0f
private const val ICON_LAYOUT_ANIMATION_STEP = 0.22
private const val ICON_LAYOUT_SETTLE_PX = 0.6
private const val EDIT_FRAME_ALPHA = 190
private const val EDIT_RING_ALPHA = 170
private const val EDIT_HALO_ALPHA = 55

class GestureCanvasView(context: Context) : View(context) {
  var onGestureComplete: ((List<Point>) -> Unit)? = null
  var onLongPressOpenManagement: (() -> Unit)? = null
  var onIconTapped: ((AppDetail) -> Unit)? = null
  var onCanvasSizeChanged: (() -> Unit)? = null
  var onIconScaleChanged: ((Float) -> Unit)? = null
  var onIconPositionChanging: ((AppDetail, Float, Float, Int, Int) -> Unit)? = null
  var onIconPositionCommitted: ((AppDetail, Float, Float, Int, Int) -> Unit)? = null
  var onEditModeChanged: ((Boolean) -> Unit)? = null
  var onLauncherIconLayoutSettled: (() -> Unit)? = null
  var onGestureGhostChanged: ((Point?) -> Unit)? = null
  var launcherIcons: List<LauncherIconNode>
    get() = displayedLauncherIcons.ifEmpty { targetLauncherIcons }
    set(value) {
      updateLauncherIconTargets(value)
    }
  var iconScale: Float = 1.0f
    set(value) {
      field = value.coerceIn(ICON_SCALE_MIN, ICON_SCALE_MAX)
      invalidate()
    }
  var trailEffect: Boolean = false
    set(value) {
      field = value
      if (!value) trailParticles.clear()
      invalidate()
    }
  var editMode: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      onEditModeChanged?.invoke(value)
      invalidate()
    }

  private val rawPoints = mutableListOf<Point>()
  private val trailParticles = mutableListOf<TrailParticle>()
  private var targetLauncherIcons: List<LauncherIconNode> = emptyList()
  private var displayedLauncherIcons: List<LauncherIconNode> = emptyList()
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
  private val trailParticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.themeColor(android.R.attr.colorAccent, Color.WHITE)
    style = Paint.Style.FILL
  }
  private val editFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.themeColor(android.R.attr.colorAccent, Color.WHITE)
    alpha = EDIT_FRAME_ALPHA
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    strokeWidth = dp(3).toFloat()
  }
  private val editRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.themeColor(android.R.attr.colorAccent, Color.WHITE)
    alpha = EDIT_RING_ALPHA
    style = Paint.Style.STROKE
    strokeWidth = dp(1).toFloat()
  }
  private val editHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.themeColor(android.R.attr.colorAccent, Color.WHITE)
    alpha = EDIT_HALO_ALPHA
    style = Paint.Style.FILL
  }

  private var downX = 0f
  private var downY = 0f
  private var longPressTriggered = false
  private var pinching = false
  private var suppressGestureUntilAllPointersUp = false
  private var pinchStartDistance = 0f
  private var pinchStartScale = 1.0f
  private var pressedIcon: LauncherIconNode? = null
  private var draggingPackageName: String? = null
  private var dragOffsetX = 0.0
  private var dragOffsetY = 0.0
  private var draggedDuringEdit = false
  private var launcherIconSettleCallbackPending = false
  private var gestureGhostActive = false

  private val longPressRunnable = Runnable {
    longPressTriggered = true
    clearNow()
    val icon = pressedIcon
    if (icon == null || iconScale <= 0f) {
      onLongPressOpenManagement?.invoke()
    } else {
      editMode = true
      beginIconDrag(icon, downX, downY)
    }
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
        pinching = false
        suppressGestureUntilAllPointersUp = false
        downX = event.x
        downY = event.y
        pressedIcon = hitTestIcon(event.x, event.y)
        if (editMode) {
          rawPoints.clear()
          path.reset()
          clearGestureGhost()
          pressedIcon?.let { icon -> beginIconDrag(icon, event.x, event.y) }
          invalidate()
          return true
        }
        rawPoints.clear()
        path.reset()
        addGesturePoint(event.x, event.y, event.eventTime, updateGhost = false)
        path.moveTo(event.x, event.y)
        postDelayed(longPressRunnable, LONG_PRESS_MS)
        invalidate()
        return true
      }

      MotionEvent.ACTION_POINTER_DOWN -> {
        if (event.pointerCount >= 2) {
          beginPinch(event)
        }
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        if (draggingPackageName != null) {
          updateIconDrag(event.x, event.y)
          return true
        }

        if (pinching) {
          updatePinch(event)
          return true
        }

        if (suppressGestureUntilAllPointersUp) {
          clearNow()
          return true
        }

        if (event.pointerCount >= 2) {
          beginPinch(event)
          return true
        }

        if (longPressTriggered) return true

        val totalMovement = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())
        if (totalMovement > LONG_PRESS_CANCEL_PX) {
          removeCallbacks(longPressRunnable)
        }

        addGesturePoint(event.x, event.y, event.eventTime, updateGhost = !isTap(event.x, event.y))
        path.lineTo(event.x, event.y)
        invalidate()
        return true
      }

      MotionEvent.ACTION_POINTER_UP -> {
        if (draggingPackageName != null) {
          finishIconDrag(commit = true)
        }
        if (pinching && event.pointerCount <= 2) {
          finishPinch()
        }
        return true
      }

      MotionEvent.ACTION_UP -> {
        removeCallbacks(longPressRunnable)
        if (draggingPackageName != null) {
          finishIconDrag(commit = true)
          return true
        }

        if (pinching) {
          finishPinch()
          suppressGestureUntilAllPointersUp = false
          return true
        }

        if (suppressGestureUntilAllPointersUp) {
          suppressGestureUntilAllPointersUp = false
          clearNow()
          return true
        }

        if (longPressTriggered) {
          longPressTriggered = false
          clearNow()
          return true
        }

        if (editMode) {
          if (pressedIcon == null && isTap(event.x, event.y)) {
            editMode = false
          }
          clearNow()
          return true
        }

        if (isTap(event.x, event.y)) {
          (pressedIcon ?: hitTestIcon(event.x, event.y))?.let { icon ->
            clearNow()
            onIconTapped?.invoke(icon.app)
            return true
          }
        }

        clearGestureGhost()
        normalizeTo40Points(rawPoints)?.let { onGestureComplete?.invoke(it) }
        postDelayed({ clearNow() }, CLEAR_CANVAS_MS)
        return true
      }

      MotionEvent.ACTION_CANCEL -> {
        removeCallbacks(longPressRunnable)
        longPressTriggered = false
        pinching = false
        suppressGestureUntilAllPointersUp = false
        finishIconDrag(commit = false)
        clearNow()
        return true
      }
    }
    return true
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    advanceLauncherIconAnimation()
    drawLauncherIcons(canvas)
    if (editMode) drawEditModeOverlay(canvas)
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
    clearGestureGhost()
    invalidate()
  }

  private fun addGesturePoint(x: Float, y: Float, eventTimeMs: Long, updateGhost: Boolean) {
    rawPoints += Point(x.toDouble(), y.toDouble())
    if (updateGhost) {
      gestureGhostActive = true
      onGestureGhostChanged?.invoke(Point(x.toDouble(), y.toDouble()))
    }
    if (trailEffect) {
      trailParticles += TrailParticle(x = x, y = y, createdAtMs = eventTimeMs)
    }
  }

  private fun clearGestureGhost() {
    if (!gestureGhostActive) return
    gestureGhostActive = false
    onGestureGhostChanged?.invoke(null)
  }

  private fun updateLauncherIconTargets(icons: List<LauncherIconNode>) {
    targetLauncherIcons = icons
    launcherIconSettleCallbackPending = true

    if (icons.isEmpty()) {
      displayedLauncherIcons = emptyList()
      invalidate()
      notifyLauncherIconLayoutSettled()
      return
    }

    if (displayedLauncherIcons.isEmpty()) {
      displayedLauncherIcons = icons
      invalidate()
      notifyLauncherIconLayoutSettled()
      return
    }

    val currentByPackage = displayedLauncherIcons.associateBy { it.app.packageName }
    displayedLauncherIcons = icons.map { target ->
      currentByPackage[target.app.packageName]?.let { current ->
        target.copy(
          sizePx = current.sizePx,
          radiusPx = current.radiusPx,
          x = current.x,
          y = current.y,
        )
      } ?: target
    }
    postInvalidateOnAnimation()
  }

  private fun advanceLauncherIconAnimation() {
    if (displayedLauncherIcons.isEmpty() || targetLauncherIcons.isEmpty()) return

    val currentByPackage = displayedLauncherIcons.associateBy { it.app.packageName }
    var settled = true
    displayedLauncherIcons = targetLauncherIcons.map { target ->
      val current = currentByPackage[target.app.packageName] ?: return@map target.also { settled = false }
      val nextX = approach(current.x, target.x)
      val nextY = approach(current.y, target.y)
      val nextSize = approach(current.sizePx, target.sizePx)
      val nextRadius = nextSize / 2.0
      if (
        abs(nextX - target.x) > ICON_LAYOUT_SETTLE_PX ||
        abs(nextY - target.y) > ICON_LAYOUT_SETTLE_PX ||
        abs(nextSize - target.sizePx) > ICON_LAYOUT_SETTLE_PX
      ) {
        settled = false
      }
      target.copy(x = nextX, y = nextY, sizePx = nextSize, radiusPx = nextRadius)
    }

    if (settled) {
      displayedLauncherIcons = targetLauncherIcons
      notifyLauncherIconLayoutSettled()
    } else {
      postInvalidateOnAnimation()
    }
  }

  private fun notifyLauncherIconLayoutSettled() {
    if (!launcherIconSettleCallbackPending) return
    launcherIconSettleCallbackPending = false
    onLauncherIconLayoutSettled?.invoke()
  }

  private fun approach(current: Double, target: Double): Double {
    val next = current + (target - current) * ICON_LAYOUT_ANIMATION_STEP
    return if (abs(next - target) <= ICON_LAYOUT_SETTLE_PX) target else next
  }

  private fun isTap(x: Float, y: Float): Boolean {
    val movement = hypot((x - downX).toDouble(), (y - downY).toDouble())
    return movement <= dp(TAP_MAX_MOVEMENT_DP)
  }

  private fun hitTestIcon(x: Float, y: Float): LauncherIconNode? {
    for (icon in displayedLauncherIcons.asReversed()) {
      val dx = x - icon.x.toFloat()
      val dy = y - icon.y.toFloat()
      if (hypot(dx.toDouble(), dy.toDouble()) <= icon.radiusPx * iconScale) {
        return icon
      }
    }
    return null
  }

  private fun beginIconDrag(icon: LauncherIconNode, touchX: Float, touchY: Float) {
    removeCallbacks(longPressRunnable)
    draggingPackageName = icon.app.packageName
    dragOffsetX = icon.x - touchX
    dragOffsetY = icon.y - touchY
    draggedDuringEdit = false
    rawPoints.clear()
    path.reset()
    invalidate()
  }

  private fun updateIconDrag(touchX: Float, touchY: Float) {
    val packageName = draggingPackageName ?: return
    val current = displayedLauncherIcons.firstOrNull { it.app.packageName == packageName } ?: return
    val nextX = (touchX + dragOffsetX).coerceIn(current.radiusPx, width - current.radiusPx)
    val nextY = (touchY + dragOffsetY).coerceIn(current.radiusPx, height - current.radiusPx)
    draggedDuringEdit = true
    moveDisplayedIcon(packageName, nextX, nextY)
    onIconPositionChanging?.invoke(current.app, nextX.toFloat(), nextY.toFloat(), width, height)
    invalidate()
  }

  private fun finishIconDrag(commit: Boolean) {
    val packageName = draggingPackageName ?: return
    val current = displayedLauncherIcons.firstOrNull { it.app.packageName == packageName }
    draggingPackageName = null
    if (commit && current != null && draggedDuringEdit) {
      onIconPositionCommitted?.invoke(current.app, current.x.toFloat(), current.y.toFloat(), width, height)
    }
    draggedDuringEdit = false
    clearNow()
  }

  private fun moveDisplayedIcon(packageName: String, x: Double, y: Double) {
    displayedLauncherIcons = displayedLauncherIcons.map { icon ->
      if (icon.app.packageName == packageName) icon.copy(x = x, y = y) else icon
    }
    targetLauncherIcons = targetLauncherIcons.map { icon ->
      if (icon.app.packageName == packageName) icon.copy(x = x, y = y) else icon
    }
  }

  private fun beginPinch(event: MotionEvent) {
    removeCallbacks(longPressRunnable)
    longPressTriggered = false
    pinching = true
    suppressGestureUntilAllPointersUp = true
    pinchStartDistance = pointerDistance(event).coerceAtLeast(MIN_PINCH_DISTANCE_PX)
    pinchStartScale = iconScale
    clearNow()
  }

  private fun updatePinch(event: MotionEvent) {
    if (event.pointerCount < 2) return
    val distance = pointerDistance(event).coerceAtLeast(MIN_PINCH_DISTANCE_PX)
    val pinchRatio = distance / pinchStartDistance
    val nextScale = (pinchStartScale + (pinchRatio - 1.0f) * PINCH_ICON_SCALE_RESPONSE)
      .coerceIn(ICON_SCALE_MIN, ICON_SCALE_MAX)
    if (nextScale != iconScale) {
      iconScale = nextScale
      onIconScaleChanged?.invoke(nextScale)
    }
  }

  private fun finishPinch() {
    pinching = false
    rawPoints.clear()
    path.reset()
    invalidate()
  }

  private fun pointerDistance(event: MotionEvent): Float {
    if (event.pointerCount < 2) return 0f
    return hypot(
      (event.getX(1) - event.getX(0)).toDouble(),
      (event.getY(1) - event.getY(0)).toDouble(),
    ).toFloat()
  }

  private fun drawLauncherIcons(canvas: Canvas) {
    for (icon in displayedLauncherIcons) {
      val scaledSize = icon.sizePx * iconScale
      if (scaledSize < 1.0) continue

      val halfSize = (scaledSize / 2.0).toInt()
      val left = icon.x.toInt() - halfSize
      val top = icon.y.toInt() - halfSize
      val right = icon.x.toInt() + halfSize
      val bottom = icon.y.toInt() + halfSize
      iconBounds.set(left, top, right, bottom)

      val drawable = icon.app.icon
      if (drawable == null) {
        canvas.drawCircle(icon.x.toFloat(), icon.y.toFloat(), (icon.radiusPx * iconScale).toFloat(), fallbackIconPaint)
      } else {
        drawable.setBounds(iconBounds)
        drawable.draw(canvas)
      }

      if (scaledSize >= dp(LABEL_MIN_ICON_DP)) {
        drawIconLabel(canvas, icon, scaledSize)
      }
    }
  }

  private fun drawIconLabel(canvas: Canvas, icon: LauncherIconNode, scaledSize: Double) {
    val maxWidth = (scaledSize * 1.25).toFloat()
    val label = icon.app.label
    val measuredCount = labelPaint.breakText(label, true, maxWidth, null)
    val visibleLabel = if (measuredCount < label.length && measuredCount > 1) {
      label.take(measuredCount - 1) + "..."
    } else {
      label
    }
    val baseline = (icon.y + (scaledSize / 2.0) + dp(14)).toFloat().coerceAtMost(height - dp(6).toFloat())
    canvas.drawText(visibleLabel, icon.x.toFloat(), baseline, labelPaint)
  }

  private fun drawEditModeOverlay(canvas: Canvas) {
    drawEditFrame(canvas)
    drawEditIconRings(canvas)
  }

  private fun drawEditFrame(canvas: Canvas) {
    val inset = dp(10).toFloat()
    val corner = dp(34).toFloat()
    val left = inset
    val top = inset
    val right = width - inset
    val bottom = height - inset

    canvas.drawLine(left, top, left + corner, top, editFramePaint)
    canvas.drawLine(left, top, left, top + corner, editFramePaint)
    canvas.drawLine(right, top, right - corner, top, editFramePaint)
    canvas.drawLine(right, top, right, top + corner, editFramePaint)
    canvas.drawLine(left, bottom, left + corner, bottom, editFramePaint)
    canvas.drawLine(left, bottom, left, bottom - corner, editFramePaint)
    canvas.drawLine(right, bottom, right - corner, bottom, editFramePaint)
    canvas.drawLine(right, bottom, right, bottom - corner, editFramePaint)
  }

  private fun drawEditIconRings(canvas: Canvas) {
    for (icon in displayedLauncherIcons) {
      val scaledRadius = icon.radiusPx * iconScale
      if (scaledRadius < 1.0) continue

      val isDragging = icon.app.packageName == draggingPackageName
      val ringRadius = (scaledRadius + dp(if (isDragging) 8 else 5)).toFloat()
      if (isDragging) {
        canvas.drawCircle(icon.x.toFloat(), icon.y.toFloat(), ringRadius + dp(4), editHaloPaint)
      }
      editRingPaint.strokeWidth = dp(if (isDragging) 3 else 1).toFloat()
      canvas.drawCircle(icon.x.toFloat(), icon.y.toFloat(), ringRadius, editRingPaint)
      canvas.drawCircle(icon.x.toFloat(), icon.y.toFloat(), dp(if (isDragging) 4 else 2).toFloat(), editFramePaint)
    }
  }

  private fun drawTrail(canvas: Canvas) {
    if (trailParticles.isEmpty()) return

    val now = SystemClock.uptimeMillis()
    trailParticles.removeAll { particle -> now - particle.createdAtMs >= TRAIL_PARTICLE_LIFETIME_MS }
    val baseRadius = dp(TRAIL_PARTICLE_RADIUS_DP).toFloat()

    for (particle in trailParticles) {
      val age = (now - particle.createdAtMs).coerceAtLeast(0L)
      val life = 1f - (age.toFloat() / TRAIL_PARTICLE_LIFETIME_MS).coerceIn(0f, 1f)
      trailParticlePaint.alpha = (TRAIL_PARTICLE_MAX_ALPHA * life).toInt()
      canvas.drawCircle(particle.x, particle.y, baseRadius * (0.35f + life * 0.65f), trailParticlePaint)
    }

    if (trailParticles.isNotEmpty()) {
      postInvalidateOnAnimation()
    }
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  private data class TrailParticle(
    val x: Float,
    val y: Float,
    val createdAtMs: Long,
  )
}
