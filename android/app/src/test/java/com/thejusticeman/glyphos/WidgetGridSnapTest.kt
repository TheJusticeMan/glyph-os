package com.thejusticeman.glyphos

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGridSnapTest {
  @Test
  fun positionSnapsWidgetCenterToNearestGridLine() {
    assertEquals(
      14,
      WidgetGridSnap.position(coord = 11, widgetSize = 20, boundsSize = 200, gridSize = 24),
    )
  }

  @Test
  fun positionRemainsInsideBoundsAfterSnapping() {
    assertEquals(160, WidgetGridSnap.position(coord = 198, widgetSize = 40, boundsSize = 200, gridSize = 24))
  }

  @Test
  fun sizeUsesGridStepsFromProviderMinimumAndRespectsBounds() {
    assertEquals(100, WidgetGridSnap.size(size = 111, minSize = 100, maxSize = 300, gridSize = 24))
    assertEquals(124, WidgetGridSnap.size(size = 113, minSize = 100, maxSize = 300, gridSize = 24))
    assertEquals(172, WidgetGridSnap.size(size = 161, minSize = 100, maxSize = 300, gridSize = 24))
    assertEquals(300, WidgetGridSnap.size(size = 300, minSize = 100, maxSize = 300, gridSize = 24))
  }
}
