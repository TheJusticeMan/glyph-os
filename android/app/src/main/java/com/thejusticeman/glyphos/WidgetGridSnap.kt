package com.thejusticeman.glyphos

internal const val WIDGET_GRID_CELLS = 12

internal object WidgetGridSnap {
  fun cellSize(boundsSize: Int, gridCells: Int): Int {
    val cells = gridCells.coerceAtLeast(1)
    return (boundsSize.coerceAtLeast(0) / cells).coerceAtLeast(1)
  }

  fun position(coord: Int, widgetSize: Int, boundsSize: Int, gridCells: Int): Int {
    if (boundsSize <= 0) return 0

    val cells = gridCells.coerceAtLeast(1)
    val maxCoordinate = (boundsSize - widgetSize).coerceAtLeast(0)
    val boundedCoordinate = coord.coerceIn(0, maxCoordinate)
    val maxGridLine = (maxCoordinate.toLong() * cells / boundsSize).toInt()
    val gridLine = ((boundedCoordinate.toLong() * cells + boundsSize / 2) / boundsSize)
      .toInt()
      .coerceIn(0, maxGridLine)
    return (gridLine * boundsSize / cells).coerceIn(0, maxCoordinate)
  }

  fun size(size: Int, minSize: Int, maxSize: Int, gridSize: Int): Int {
    val minimum = minSize.coerceAtLeast(1)
    val maximum = maxSize.coerceAtLeast(minimum)
    val requested = size.coerceIn(minimum, maximum)
    val grid = gridSize.coerceAtLeast(1)
    val minimumGridSize = ((minimum + grid - 1) / grid) * grid
    val maximumGridSize = (maximum / grid) * grid
    if (maximumGridSize < minimumGridSize) return minimum

    val snappedSize = ((requested + grid / 2) / grid) * grid
    return snappedSize.coerceIn(minimumGridSize, maximumGridSize)
  }
}
