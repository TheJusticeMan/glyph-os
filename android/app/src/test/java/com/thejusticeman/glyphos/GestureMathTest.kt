package com.thejusticeman.glyphos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class GestureMathTest {
  @Test
  fun normalizeTo40PointsRejectsInvalidInput() {
    assertNull(normalizeTo40Points(emptyList()))
    assertNull(normalizeTo40Points(listOf(Point(0.0, 0.0))))
    assertNull(normalizeTo40Points(List(10) { Point(5.0, 5.0) }))
  }

  @Test
  fun normalizeTo40PointsRequiresMinimumGestureLength() {
    assertNull(normalizeTo40Points(horizontalLine(rawCount = 20, length = MIN_GESTURE_LENGTH_PX - 0.1)))
    assertNotNull(normalizeTo40Points(horizontalLine(rawCount = 20, length = MIN_GESTURE_LENGTH_PX)))
  }

  @Test
  fun normalizeTo40PointsProducesExpectedHorizontalSpacing() {
    val result = normalizeTo40Points(horizontalLine(rawCount = 200, length = 390.0))
    assertNotNull(result)
    requireNotNull(result)

    assertEquals(NUM_POINTS, result.size)
    assertEquals(0.0, result.first().x, 0.0001)
    assertEquals(390.0, result.last().x, 0.0001)

    val expectedInterval = 390.0 / (NUM_POINTS - 1)
    for (index in 1 until result.size) {
      assertEquals(expectedInterval, distance(result[index - 1], result[index]), 0.5)
    }
  }

  @Test
  fun calculateDifferenceMatchesExpectedAngles() {
    val horizontal = straightLinePoints()
    val vertical = List(NUM_POINTS) { index -> Point(0.0, index * 100.0 / (NUM_POINTS - 1)) }

    assertEquals(0.0, calculateDifference(horizontal, horizontal), 0.000001)
    assertEquals(PI / 2, calculateDifference(horizontal, vertical), 0.00001)
    assertEquals(Double.POSITIVE_INFINITY, calculateDifference(emptyList(), horizontal), 0.0)
  }

  @Test
  fun backwardMatchingIsOptional() {
    val path = straightLinePoints()
    val reversed = path.asReversed()

    assertTrue(calculateBestDirectionDifference(path, reversed, allowBackward = false) > ANGULAR_THRESHOLD)
    assertEquals(0.0, calculateBestDirectionDifference(path, reversed, allowBackward = true), 0.000001)
  }

  @Test
  fun matchGestureUsesStrictAngularThreshold() {
    val horizontal = straightLinePoints()
    val vertical = List(NUM_POINTS) { index -> Point(0.0, index * 100.0 / (NUM_POINTS - 1)) }

    assertNotNull(matchGesture(horizontal, listOf(SavedGesture("line", normalizedPath = horizontal))))
    assertNull(matchGesture(horizontal, listOf(SavedGesture("vertical", normalizedPath = vertical))))
  }

  @Test
  fun blendNormalizedPathsInterpolatesCoordinates() {
    val first = straightLinePoints()
    val second = first.map { Point(it.x + 10.0, it.y + 20.0) }
    val midpoint = blendNormalizedPaths(first, second, 0.5)

    assertEquals(first.size, midpoint.size)
    assertEquals((first[0].x + second[0].x) / 2, midpoint[0].x, 0.000001)
    assertEquals((first[0].y + second[0].y) / 2, midpoint[0].y, 0.000001)
  }

  @Test
  fun adaptNormalizedPathMovesFivePercentTowardUsedGesture() {
    val saved = straightLinePoints()
    val used = saved.map { Point(it.x + 40.0, it.y + 80.0) }
    val adapted = adaptNormalizedPath(saved, used)

    assertEquals(saved.size, adapted.size)
    assertEquals(saved[0].x + 2.0, adapted[0].x, 0.000001)
    assertEquals(saved[0].y + 4.0, adapted[0].y, 0.000001)
    assertEquals(saved.last().x + 2.0, adapted.last().x, 0.000001)
    assertEquals(saved.last().y + 4.0, adapted.last().y, 0.000001)
  }

  @Test
  fun adaptNormalizedPathAlignsBackwardGesturesWhenAllowed() {
    val saved = List(NUM_POINTS) { index ->
      val progress = index.toDouble() / (NUM_POINTS - 1)
      Point(x = progress * 120.0, y = progress * progress * 80.0)
    }
    val usedBackward = saved.asReversed()
    val adapted = adaptNormalizedPath(saved, usedBackward, allowBackward = true)

    assertEquals(saved.size, adapted.size)
    for (index in saved.indices) {
      assertEquals(saved[index].x, adapted[index].x, 0.000001)
      assertEquals(saved[index].y, adapted[index].y, 0.000001)
    }
  }

  @Test
  fun defaultOpenAppListGestureMatchesSwipeUp() {
    val defaultGesture = defaultOpenAppListGesture()
    val candidate = upwardLinePoints(length = 320.0)

    assertEquals(SPECIAL_ACTION_OPEN_APP_LIST, defaultGesture.specialActionId)
    assertNotNull(matchGesture(candidate, listOf(defaultGesture), allowBackward = true))
  }

  @Test
  fun defaultOpenGoogleGestureMatchesSidewaysLines() {
    val defaultGesture = defaultOpenGoogleGesture()
    val candidate = straightLinePoints(length = 320.0)

    assertEquals(GOOGLE_APP_PACKAGE_NAME, defaultGesture.packageName)
    assertNotNull(matchGesture(candidate, listOf(defaultGesture), allowBackward = true))
  }

  @Test
  fun rankSimilarTargetsIncludesSpecialFunctions() {
    val candidate = upwardLinePoints()
    val ranked = rankSimilarTargets(
      candidate,
      listOf(defaultOpenAppListGesture()),
      limit = 5,
      allowBackward = true,
    )

    assertEquals(1, ranked.size)
    assertEquals("special:$SPECIAL_ACTION_OPEN_APP_LIST", ranked.first().targetKey)
  }

  private fun horizontalLine(rawCount: Int, length: Double): List<Point> {
    return List(rawCount) { index -> Point(index * length / (rawCount - 1), 0.0) }
  }

  private fun straightLinePoints(length: Double = 100.0): List<Point> {
    return List(NUM_POINTS) { index -> Point(index * length / (NUM_POINTS - 1), 0.0) }
  }

  private fun upwardLinePoints(length: Double = 100.0): List<Point> {
    return List(NUM_POINTS) { index -> Point(0.0, -index * length / (NUM_POINTS - 1)) }
  }

  private fun distance(a: Point, b: Point): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
  }
}
