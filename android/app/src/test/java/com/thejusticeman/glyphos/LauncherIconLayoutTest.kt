package com.thejusticeman.glyphos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class LauncherIconLayoutTest {
  @Test
  fun iconSizeGrowsWithSqrtLaunchCount() {
    val minSize = 44.0
    val maxSize = 112.0

    val unused = LauncherIconLayout.iconSize(0, 100, minSize, maxSize)
    val partial = LauncherIconLayout.iconSize(25, 100, minSize, maxSize)
    val full = LauncherIconLayout.iconSize(100, 100, minSize, maxSize)

    assertEquals(minSize, unused, 0.0001)
    assertTrue(partial > unused)
    assertTrue(partial < full)
    assertEquals(maxSize, full, 0.0001)
  }

  @Test
  fun buildIsDeterministicForSameInput() {
    val apps = sampleApps(10)
    val counts = apps.withIndex().associate { (index, app) -> app.packageName to index * index }

    val first = LauncherIconLayout.build(apps, counts, 900, 1400, 44.0, 112.0)
    val second = LauncherIconLayout.build(apps, counts, 900, 1400, 44.0, 112.0)

    assertEquals(first.size, second.size)
    for (index in first.indices) {
      assertEquals(first[index].app.packageName, second[index].app.packageName)
      assertEquals(first[index].x, second[index].x, 0.0001)
      assertEquals(first[index].y, second[index].y, 0.0001)
      assertEquals(first[index].sizePx, second[index].sizePx, 0.0001)
    }
  }

  @Test
  fun buildKeepsIconsWithinCanvas() {
    val apps = sampleApps(16)
    val counts = apps.withIndex().associate { (index, app) -> app.packageName to index + 1 }
    val nodes = LauncherIconLayout.build(apps, counts, 1080, 1920, 44.0, 112.0)

    assertEquals(apps.size, nodes.size)
    nodes.forEach { node ->
      assertTrue(node.x >= node.radiusPx)
      assertTrue(node.y >= node.radiusPx)
      assertTrue(node.x <= 1080.0 - node.radiusPx)
      assertTrue(node.y <= 1920.0 - node.radiusPx)
    }
  }

  @Test
  fun buildResolvesMostPracticalOverlaps() {
    val apps = sampleApps(12)
    val counts = apps.withIndex().associate { (index, app) -> app.packageName to if (index < 3) 100 else 1 }
    val nodes = LauncherIconLayout.build(apps, counts, 1080, 1920, 44.0, 112.0)

    for (leftIndex in nodes.indices) {
      for (rightIndex in leftIndex + 1 until nodes.size) {
        val left = nodes[leftIndex]
        val right = nodes[rightIndex]
        val distance = hypot(left.x - right.x, left.y - right.y)
        val allowedOverlap = (left.radiusPx + right.radiusPx) * 0.06
        assertTrue(distance + allowedOverlap >= left.radiusPx + right.radiusPx)
      }
    }
  }

  private fun sampleApps(count: Int): List<AppDetail> {
    return List(count) { index ->
      AppDetail(
        label = "App $index",
        packageName = "com.example.app$index",
        icon = null,
      )
    }
  }
}
