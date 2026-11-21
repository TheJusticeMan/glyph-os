package com.thejusticeman.glyphos

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGridSnapTest {
  @Test
  fun positionSnapsTopLeftToNearestGridLine() {
    assertEquals(
      20,
      WidgetGridSnap.position(coord = 18, widgetSize = 40, boundsSize = 240, gridCells = 12),
    )
    assertEquals(20, WidgetGridSnap.cellSize(boundsSize = 240, gridCells = 12))
    assertEquals(83, WidgetGridSnap.cellSize(boundsSize = 1000, gridCells = 12))
    assertEquals(83, WidgetGridSnap.cellSize(boundsSize = 1004, gridCells = 12))
  }

  @Test
  fun positionRemainsInsideBoundsAfterSnapping() {
    assertEquals(200, WidgetGridSnap.position(coord = 228, widgetSize = 40, boundsSize = 240, gridCells = 12))
  }

  @Test
  fun sizeUsesGridStepsFromProviderMinimumAndRespectsBounds() {
    assertEquals(120, WidgetGridSnap.size(size = 109, minSize = 101, maxSize = 300, gridSize = 20))
    assertEquals(120, WidgetGridSnap.size(size = 129, minSize = 101, maxSize = 300, gridSize = 20))
    assertEquals(140, WidgetGridSnap.size(size = 131, minSize = 101, maxSize = 300, gridSize = 20))
    assertEquals(300, WidgetGridSnap.size(size = 300, minSize = 101, maxSize = 300, gridSize = 20))
    assertEquals(280, WidgetGridSnap.size(size = 295, minSize = 101, maxSize = 295, gridSize = 20))
  }
}
