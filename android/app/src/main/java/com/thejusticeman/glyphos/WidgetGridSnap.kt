package com.thejusticeman.glyphos

internal const val WIDGET_GRID_SIZE_DP = 24

internal object WidgetGridSnap {
  fun position(coord: Int, widgetSize: Int, boundsSize: Int, gridSize: Int): Int {
    val maxCoordinate = (boundsSize - widgetSize).coerceAtLeast(0)
    val boundedCoordinate = coord.coerceIn(0, maxCoordinate)
    val grid = gridSize.coerceAtLeast(1)
    val widgetCenter = boundedCoordinate + widgetSize / 2
    val snappedCenter = ((widgetCenter + grid / 2) / grid) * grid
    return (snappedCenter - widgetSize / 2).coerceIn(0, maxCoordinate)
  }

  fun size(size: Int, minSize: Int, maxSize: Int, gridSize: Int): Int {
    val minimum = minSize.coerceAtLeast(1)
    val maximum = maxSize.coerceAtLeast(minimum)
    val requested = size.coerceIn(minimum, maximum)
    if (requested == maximum) return maximum

    val grid = gridSize.coerceAtLeast(1)
    val steps = ((requested - minimum + grid / 2) / grid).coerceAtLeast(0)
    return (minimum + steps * grid).coerceIn(minimum, maximum)
  }
}
