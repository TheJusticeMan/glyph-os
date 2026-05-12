package com.thejusticeman.glyphos

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot

private const val WIDGET_LONG_PRESS_MS = 200L
private const val WIDGET_LONG_PRESS_CANCEL_PX = 10.0

class WidgetLayerView(context: Context) : FrameLayout(context) {
  var onLongPressEditMode: (() -> Unit)? = null
  var onWidgetDragStateChanged: ((Boolean) -> Unit)? = null
  var onWidgetPlacementChanging: ((WidgetPlacement) -> Unit)? = null
  var onWidgetPlacementCommitted: ((WidgetPlacement) -> Unit)? = null
  var editMode: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      if (value) cancelWidgetLongPress()
      applyEditModeVisuals()
    }

  private val widgetViews = mutableMapOf<Int, AppWidgetHostView>()
  private val placementsById = mutableMapOf<Int, WidgetPlacement>()
  private var activeTouchWidgetId: Int? = null
  private var activeMode: TouchMode = TouchMode.NONE
  private var touchStartRawX = 0f
  private var touchStartRawY = 0f
  private var touchStartX = 0
  private var touchStartY = 0
  private var touchStartWidth = 0
  private var touchStartHeight = 0
  private var focusedWidgetId: Int? = null
  private var activeResizeHandle: ResizeHandle = ResizeHandle.NONE
  private var longPressStartRawX = 0f
  private var longPressStartRawY = 0f
  private var widgetLongPressTriggered = false
  private var longPressWidgetId: Int? = null
  private val longPressHandler = Handler(Looper.getMainLooper())
  private val widgetLongPressRunnable = Runnable {
    widgetLongPressTriggered = true
    focusedWidgetId = longPressWidgetId
    onLongPressEditMode?.invoke()
    applyEditModeVisuals()
  }

  private val editHandleThresholdPx = dp(42)
  private val minimumVisibleSizePx = dp(56)
  private val gridSizePx = dp(24)
  private val snapThresholdPx = dp(12)

  private enum class TouchMode {
    NONE,
    DRAG,
    RESIZE,
  }

  private enum class ResizeHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
  }

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    if (widgetLongPressTriggered) return true
    if (!editMode) return handleWidgetLongPressIntercept(event)
    
    // In edit mode, intercept all touch events to prevent widgets from opening
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        // Find which widget was touched
        val childCount = childCount
        for (i in childCount - 1 downTo 0) {
          val child = getChildAt(i)
          if (child is AppWidgetHostView && isEventInChild(event, child)) {
            return true // Intercept to handle in onTouchEvent
          }
        }
      }
      MotionEvent.ACTION_MOVE,
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        if (activeTouchWidgetId != null) {
          return true // Keep intercepting ongoing touches
        }
      }
    }
    return super.onInterceptTouchEvent(event)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (widgetLongPressTriggered) {
      if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
        widgetLongPressTriggered = false
        longPressWidgetId = null
      }
      return true
    }

    if (!editMode) return super.onTouchEvent(event)
    
    val targetWidgetId = activeTouchWidgetId ?: widgetIdAt(event)
    val placement = targetWidgetId?.let { placementsById[it] } ?: return super.onTouchEvent(event)

    return when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val appWidgetId = targetWidgetId
        activeTouchWidgetId = appWidgetId
        focusWidget(appWidgetId)
        activeResizeHandle = resizeHandleAt(event.x - placement.x, event.y - placement.y, placement)
        activeMode = if (activeResizeHandle != ResizeHandle.NONE) TouchMode.RESIZE else TouchMode.DRAG
        if (activeMode == TouchMode.DRAG) onWidgetDragStateChanged?.invoke(true)
        touchStartRawX = event.rawX
        touchStartRawY = event.rawY
        touchStartX = placement.x
        touchStartY = placement.y
        touchStartWidth = placement.width
        touchStartHeight = placement.height
        requestDisallowInterceptTouchEvent(true)
        true
      }

      MotionEvent.ACTION_MOVE -> {
        val appWidgetId = activeTouchWidgetId ?: return true
        val activePlacement = placementsById[appWidgetId] ?: return true
        val deltaX = (event.rawX - touchStartRawX).toInt()
        val deltaY = (event.rawY - touchStartRawY).toInt()
        val nextPlacement = when (activeMode) {
          TouchMode.DRAG -> {
            var nextX = (touchStartX + deltaX).coerceIn(0, (width - activePlacement.width).coerceAtLeast(0))
            var nextY = (touchStartY + deltaY).coerceIn(0, (height - activePlacement.height).coerceAtLeast(0))

            val snappedX = snapCoordinate(nextX, activePlacement.width, true, appWidgetId)
            val snappedY = snapCoordinate(nextY, activePlacement.height, false, appWidgetId)
            nextX = snappedX ?: nextX
            nextY = snappedY ?: nextY

            activePlacement.copy(
              x = nextX,
              y = nextY,
              lastUpdated = System.currentTimeMillis(),
            )
          }

          TouchMode.RESIZE -> resizePlacement(activePlacement, deltaX, deltaY)
          TouchMode.NONE -> activePlacement
        }
        updatePlacement(nextPlacement)
        onWidgetPlacementChanging?.invoke(nextPlacement)
        true
      }

      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        val appWidgetId = activeTouchWidgetId
        if (appWidgetId != null) {
          placementsById[appWidgetId]?.let { onWidgetPlacementCommitted?.invoke(it) }
        }
        resetTouchState()
        true
      }

      else -> super.onTouchEvent(event)
    }
  }

  private fun isEventInChild(event: MotionEvent, child: View): Boolean {
    val x = event.x.toInt()
    val y = event.y.toInt()
    return x >= child.left && x < child.right && y >= child.top && y < child.bottom
  }

  private fun widgetIdAt(event: MotionEvent): Int? {
    for (index in childCount - 1 downTo 0) {
      val child = getChildAt(index)
      if (child is AppWidgetHostView && isEventInChild(event, child)) {
        return child.tag as? Int
      }
    }
    return null
  }

  fun clearWidgets() {
    removeAllViews()
    widgetViews.clear()
    placementsById.clear()
    focusedWidgetId = null
    resetTouchState()
  }

  fun addWidgetView(hostView: AppWidgetHostView, placement: WidgetPlacement) {
    val existing = widgetViews.remove(placement.appWidgetId)
    if (existing != null) removeView(existing)

    hostView.tag = placement.appWidgetId
    hostView.isFocusable = false
    hostView.isClickable = !editMode
    hostView.setOnTouchListener(::handleWidgetTouch)
    widgetViews[placement.appWidgetId] = hostView
    placementsById[placement.appWidgetId] = placement
    addView(hostView, layoutParamsFor(placement))
    applyEditModeVisual(hostView)
  }

  fun removeWidgetView(appWidgetId: Int) {
    val view = widgetViews.remove(appWidgetId) ?: return
    placementsById.remove(appWidgetId)
    if (focusedWidgetId == appWidgetId) focusedWidgetId = null
    removeView(view)
    if (activeTouchWidgetId == appWidgetId) {
      resetTouchState()
    }
  }

  fun updateLayout(placements: List<WidgetPlacement>) {
    val placementById = placements.associateBy { it.appWidgetId }
    val staleIds = widgetViews.keys.filterNot(placementById::containsKey)
    staleIds.forEach { removeWidgetView(it) }

    widgetViews.forEach { (id, hostView) ->
      val placement = placementById[id] ?: return@forEach
      placementsById[id] = placement
      hostView.layoutParams = layoutParamsFor(placement)
    }
  }

  fun currentPlacements(): List<WidgetPlacement> {
    return placementsById.values.sortedBy { it.appWidgetId }
  }

  private fun handleWidgetTouch(view: View, event: MotionEvent): Boolean {
    if (!editMode) return handleWidgetLongPress(event)

    val appWidgetId = (view.tag as? Int) ?: return false
    val placement = placementsById[appWidgetId] ?: return false

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        activeTouchWidgetId = appWidgetId
        focusWidget(appWidgetId)
        activeResizeHandle = resizeHandleAt(event.x, event.y, placement)
        activeMode = if (activeResizeHandle != ResizeHandle.NONE) TouchMode.RESIZE else TouchMode.DRAG
        if (activeMode == TouchMode.DRAG) onWidgetDragStateChanged?.invoke(true)
        touchStartRawX = event.rawX
        touchStartRawY = event.rawY
        touchStartX = placement.x
        touchStartY = placement.y
        touchStartWidth = placement.width
        touchStartHeight = placement.height
        view.parent?.requestDisallowInterceptTouchEvent(true)
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        if (activeTouchWidgetId != appWidgetId) return true
        val deltaX = (event.rawX - touchStartRawX).toInt()
        val deltaY = (event.rawY - touchStartRawY).toInt()
        val nextPlacement = when (activeMode) {
          TouchMode.DRAG -> {
            var nextX = (touchStartX + deltaX).coerceIn(0, (width - placement.width).coerceAtLeast(0))
            var nextY = (touchStartY + deltaY).coerceIn(0, (height - placement.height).coerceAtLeast(0))
            
            // Apply snapping
            val snappedX = snapCoordinate(nextX, placement.width, true, appWidgetId)
            val snappedY = snapCoordinate(nextY, placement.height, false, appWidgetId)
            nextX = snappedX ?: nextX
            nextY = snappedY ?: nextY
            
            placement.copy(
              x = nextX,
              y = nextY,
              lastUpdated = System.currentTimeMillis(),
            )
          }

          TouchMode.RESIZE -> resizePlacement(placement, deltaX, deltaY)

          TouchMode.NONE -> placement
        }

        updatePlacement(nextPlacement)
        onWidgetPlacementChanging?.invoke(nextPlacement)
        return true
      }

      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        if (activeTouchWidgetId == appWidgetId) {
          placementsById[appWidgetId]?.let { onWidgetPlacementCommitted?.invoke(it) }
        }
        resetTouchState()
        return true
      }
    }

    return true
  }

  private fun updatePlacement(placement: WidgetPlacement) {
    placementsById[placement.appWidgetId] = placement
    val view = widgetViews[placement.appWidgetId] ?: return
    view.layoutParams = layoutParamsFor(placement)
    view.requestLayout()
  }

  private fun resetTouchState() {
    val wasDragging = activeMode == TouchMode.DRAG
    activeTouchWidgetId = null
    activeMode = TouchMode.NONE
    activeResizeHandle = ResizeHandle.NONE
    if (wasDragging) onWidgetDragStateChanged?.invoke(false)
  }

  private fun focusWidget(appWidgetId: Int) {
    if (focusedWidgetId == appWidgetId) return
    focusedWidgetId = appWidgetId
    applyEditModeVisuals()
  }

  private fun handleWidgetLongPress(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        startWidgetLongPress(event)
        return false
      }

      MotionEvent.ACTION_MOVE -> {
        cancelWidgetLongPressIfMoved(event)
        return widgetLongPressTriggered
      }

      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        cancelWidgetLongPress()
        val consumed = widgetLongPressTriggered
        widgetLongPressTriggered = false
        return consumed
      }
    }
    return widgetLongPressTriggered
  }

  private fun handleWidgetLongPressIntercept(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        longPressWidgetId = widgetIdAt(event) ?: return false
        startWidgetLongPress(event)
        return false
      }

      MotionEvent.ACTION_MOVE -> {
        if (longPressWidgetId == null) return false
        cancelWidgetLongPressIfMoved(event)
        return widgetLongPressTriggered
      }

      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        val consumed = widgetLongPressTriggered
        cancelWidgetLongPress()
        widgetLongPressTriggered = false
        longPressWidgetId = null
        return consumed
      }
    }
    return widgetLongPressTriggered
  }

  private fun startWidgetLongPress(event: MotionEvent) {
    cancelWidgetLongPress()
    widgetLongPressTriggered = false
    longPressStartRawX = event.rawX
    longPressStartRawY = event.rawY
    longPressHandler.postDelayed(widgetLongPressRunnable, WIDGET_LONG_PRESS_MS)
  }

  private fun cancelWidgetLongPressIfMoved(event: MotionEvent) {
    val movement = hypot(
      (event.rawX - longPressStartRawX).toDouble(),
      (event.rawY - longPressStartRawY).toDouble(),
    )
    if (movement > WIDGET_LONG_PRESS_CANCEL_PX) {
      cancelWidgetLongPress()
      longPressWidgetId = null
    }
  }

  private fun cancelWidgetLongPress() {
    longPressHandler.removeCallbacks(widgetLongPressRunnable)
  }

  private fun resizeHandleAt(localX: Float, localY: Float, placement: WidgetPlacement): ResizeHandle {
    val threshold = editHandleThresholdPx.toFloat()
    val nearLeft = localX <= threshold
    val nearRight = abs(localX - placement.width) <= threshold
    val nearTop = localY <= threshold
    val nearBottom = abs(localY - placement.height) <= threshold
    return when {
      nearLeft && nearTop -> ResizeHandle.TOP_LEFT
      nearRight && nearTop -> ResizeHandle.TOP_RIGHT
      nearLeft && nearBottom -> ResizeHandle.BOTTOM_LEFT
      nearRight && nearBottom -> ResizeHandle.BOTTOM_RIGHT
      else -> ResizeHandle.NONE
    }
  }

  private fun resizePlacement(placement: WidgetPlacement, deltaX: Int, deltaY: Int): WidgetPlacement {
    val minWidth = placement.minWidth.takeIf { it > 0 } ?: minimumVisibleSizePx
    val minHeight = placement.minHeight.takeIf { it > 0 } ?: minimumVisibleSizePx
    var nextX = touchStartX
    var nextY = touchStartY
    var nextWidth = touchStartWidth
    var nextHeight = touchStartHeight

    when (activeResizeHandle) {
      ResizeHandle.TOP_LEFT -> {
        val maxDeltaX = touchStartWidth - minWidth
        val maxDeltaY = touchStartHeight - minHeight
        val boundedDeltaX = deltaX.coerceIn(-touchStartX, maxDeltaX)
        val boundedDeltaY = deltaY.coerceIn(-touchStartY, maxDeltaY)
        nextX = touchStartX + boundedDeltaX
        nextY = touchStartY + boundedDeltaY
        nextWidth = touchStartWidth - boundedDeltaX
        nextHeight = touchStartHeight - boundedDeltaY
      }

      ResizeHandle.TOP_RIGHT -> {
        val boundedDeltaX = deltaX.coerceIn(minWidth - touchStartWidth, width - touchStartX - touchStartWidth)
        val maxDeltaY = touchStartHeight - minHeight
        val boundedDeltaY = deltaY.coerceIn(-touchStartY, maxDeltaY)
        nextY = touchStartY + boundedDeltaY
        nextWidth = touchStartWidth + boundedDeltaX
        nextHeight = touchStartHeight - boundedDeltaY
      }

      ResizeHandle.BOTTOM_LEFT -> {
        val maxDeltaX = touchStartWidth - minWidth
        val boundedDeltaX = deltaX.coerceIn(-touchStartX, maxDeltaX)
        val boundedDeltaY = deltaY.coerceIn(minHeight - touchStartHeight, height - touchStartY - touchStartHeight)
        nextX = touchStartX + boundedDeltaX
        nextWidth = touchStartWidth - boundedDeltaX
        nextHeight = touchStartHeight + boundedDeltaY
      }

      ResizeHandle.BOTTOM_RIGHT -> {
        val boundedDeltaX = deltaX.coerceIn(minWidth - touchStartWidth, width - touchStartX - touchStartWidth)
        val boundedDeltaY = deltaY.coerceIn(minHeight - touchStartHeight, height - touchStartY - touchStartHeight)
        nextWidth = touchStartWidth + boundedDeltaX
        nextHeight = touchStartHeight + boundedDeltaY
      }

      ResizeHandle.NONE -> return placement
    }

    return placement.copy(
      x = nextX,
      y = nextY,
      width = nextWidth.coerceAtLeast(minWidth),
      height = nextHeight.coerceAtLeast(minHeight),
      lastUpdated = System.currentTimeMillis(),
    )
  }

  private fun applyEditModeVisuals() {
    widgetViews.values.forEach { view ->
      applyEditModeVisual(view)
      view.isClickable = !editMode
    }
  }

  private fun applyEditModeVisual(view: AppWidgetHostView) {
    if (editMode) {
      val widgetId = view.tag as? Int
      view.foreground = WidgetEditOverlayDrawable(
        focused = widgetId != null && widgetId == focusedWidgetId,
        strokeColor = Color.argb(210, 255, 255, 255),
        handleColor = Color.WHITE,
        accentColor = Color.argb(230, 70, 130, 255),
        cornerRadius = dp(8).toFloat(),
        handleRadius = dp(9).toFloat(),
        strokeWidth = dp(if (widgetId != null && widgetId == focusedWidgetId) 3 else 2).toFloat(),
      )
      view.alpha = 0.96f
    } else {
      view.foreground = null
      view.alpha = 1.0f
    }
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  private class WidgetEditOverlayDrawable(
    private val focused: Boolean,
    private val strokeColor: Int,
    private val handleColor: Int,
    private val accentColor: Int,
    private val cornerRadius: Float,
    private val handleRadius: Float,
    private val strokeWidth: Float,
  ) : Drawable() {
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
      val rect = RectF(bounds).apply {
        inset(strokeWidth / 2f, strokeWidth / 2f)
      }
      borderPaint.color = if (focused) accentColor else strokeColor
      borderPaint.strokeWidth = strokeWidth
      canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

      if (!focused) return

      val handleCenters = listOf(
        rect.left to rect.top,
        rect.right to rect.top,
        rect.left to rect.bottom,
        rect.right to rect.bottom,
      )
      handlePaint.color = handleColor
      handleStrokePaint.color = accentColor
      handleStrokePaint.strokeWidth = strokeWidth
      for ((x, y) in handleCenters) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
        canvas.drawCircle(x, y, handleRadius, handleStrokePaint)
      }
    }

    override fun setAlpha(alpha: Int) {
      borderPaint.alpha = alpha
      handlePaint.alpha = alpha
      handleStrokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
      borderPaint.colorFilter = colorFilter
      handlePaint.colorFilter = colorFilter
      handleStrokePaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
  }

  private fun snapCoordinate(coord: Int, size: Int, isHorizontal: Boolean, excludeWidgetId: Int): Int? {
    var bestSnap: Int? = null
    var bestDistance = snapThresholdPx + 1

    val currentCenter = coord + size / 2
    val gridSnappedCenter = (currentCenter / gridSizePx) * gridSizePx
    val gridSnapX = gridSnappedCenter - size / 2

    val gridDist = abs(coord - gridSnapX)
    if (gridDist <= snapThresholdPx && gridDist < bestDistance) {
      bestSnap = gridSnapX
      bestDistance = gridDist
    }

    // Snap to other widget centers and edges
    for ((id, otherPlacement) in placementsById) {
      if (id == excludeWidgetId) continue

      if (isHorizontal) {
        // Snap to left/right edges and center
        listOf(
          otherPlacement.x,                                   // snap to left edge
          otherPlacement.x + otherPlacement.width,           // snap to right edge
          otherPlacement.x + otherPlacement.width / 2 - size / 2  // snap centers
        ).forEach { snapPos ->
          val distance = abs(coord - snapPos)
          if (distance <= snapThresholdPx && distance < bestDistance) {
            bestSnap = snapPos
            bestDistance = distance
          }
        }
      } else {
        // Snap to top/bottom edges and center
        listOf(
          otherPlacement.y,                                   // snap to top edge
          otherPlacement.y + otherPlacement.height,          // snap to bottom edge
          otherPlacement.y + otherPlacement.height / 2 - size / 2  // snap centers
        ).forEach { snapPos ->
          val distance = abs(coord - snapPos)
          if (distance <= snapThresholdPx && distance < bestDistance) {
            bestSnap = snapPos
            bestDistance = distance
          }
        }
      }
    }

    return bestSnap?.coerceIn(0, if (isHorizontal) (width - size).coerceAtLeast(0) else (height - size).coerceAtLeast(0))
  }

  private fun layoutParamsFor(placement: WidgetPlacement): LayoutParams {
    val width = placement.width.coerceAtLeast(1)
    val height = placement.height.coerceAtLeast(1)
    return LayoutParams(width, height).apply {
      leftMargin = placement.x
      topMargin = placement.y
    }
  }
}
