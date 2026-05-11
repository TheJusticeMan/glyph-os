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
  fun buildKeepsStableOrderWhenLaunchCountsChange() {
    val apps = sampleApps(10)
    val initial = LauncherIconLayout.build(apps, emptyMap(), 900, 1400, 44.0, 112.0)
    val updated = LauncherIconLayout.build(
      apps = apps,
      launchCounts = mapOf(apps.last().packageName to 20),
      widthPx = 900,
      heightPx = 1400,
      minSizePx = 44.0,
      maxSizePx = 112.0,
      previousNodes = initial,
    )

    assertEquals(
      initial.map { it.app.packageName },
      updated.map { it.app.packageName },
    )
  }

  @Test
  fun automaticSlotsStartAtCanvasCenter() {
    val slots = LauncherIconLayout.automaticSlots(sampleApps(6), emptyMap(), 900, 1400, 44.0, 112.0)

    assertEquals(6, slots.size)
    assertEquals(0, slots.first().index)
    assertEquals(450.0, slots.first().x, 0.0001)
    assertEquals(700.0, slots.first().y, 0.0001)
  }

  @Test
  fun buildCanStartFromPreviousPositions() {
    val app = sampleApps(1).first()
    val previous = listOf(
      LauncherIconNode(
        app = app,
        launchCount = 0,
        sizePx = 44.0,
        radiusPx = 22.0,
        x = 120.0,
        y = 340.0,
      ),
    )

    val nodes = LauncherIconLayout.build(
      apps = listOf(app),
      launchCounts = mapOf(app.packageName to 4),
      widthPx = 900,
      heightPx = 1400,
      minSizePx = 44.0,
      maxSizePx = 112.0,
      previousNodes = previous,
      iterations = 0,
    )

    assertEquals(120.0, nodes.single().x, 0.0001)
    assertEquals(340.0, nodes.single().y, 0.0001)
  }

  @Test
  fun previousPositionSeedsButDoesNotReplaceFormationAnchor() {
    val app = sampleApps(1).first()
    val previous = listOf(
      LauncherIconNode(
        app = app,
        launchCount = 0,
        sizePx = 44.0,
        radiusPx = 22.0,
        x = 120.0,
        y = 340.0,
      ),
    )

    val nodes = LauncherIconLayout.build(
      apps = listOf(app),
      launchCounts = emptyMap(),
      widthPx = 900,
      heightPx = 1400,
      minSizePx = 44.0,
      maxSizePx = 112.0,
      previousNodes = previous,
    )

    assertTrue(nodes.single().x > 120.0)
    assertTrue(nodes.single().y > 340.0)
    assertTrue(nodes.single().x < 450.0)
    assertTrue(nodes.single().y < 700.0)
  }

  @Test
  fun fixedPositionPinsIconDuringLayout() {
    val apps = sampleApps(8)
    val pinnedApp = apps[3]
    val nodes = LauncherIconLayout.build(
      apps = apps,
      launchCounts = emptyMap(),
      widthPx = 900,
      heightPx = 1400,
      minSizePx = 44.0,
      maxSizePx = 112.0,
      fixedPositions = mapOf(pinnedApp.packageName to Point(300.0, 500.0)),
    )

    val pinnedNode = nodes.first { it.app.packageName == pinnedApp.packageName }
    assertEquals(300.0, pinnedNode.x, 0.0001)
    assertEquals(500.0, pinnedNode.y, 0.0001)
  }

  @Test
  fun ghostNodePushesIconsWithoutBeingReturned() {
    val app = sampleApps(1).first()
    val nodes = LauncherIconLayout.build(
      apps = listOf(app),
      launchCounts = emptyMap(),
      widthPx = 900,
      heightPx = 1400,
      minSizePx = 44.0,
      maxSizePx = 44.0,
      ghostNodes = listOf(LauncherIconGhostNode(450.0, 700.0, 80.0)),
    )

    assertEquals(1, nodes.size)
    assertEquals(app.packageName, nodes.single().app.packageName)
    val distanceFromFinger = hypot(nodes.single().x - 450.0, nodes.single().y - 700.0)
    assertTrue(distanceFromFinger > nodes.single().radiusPx)
  }

  @Test
  fun anchorPositionPullsIconWithoutFixingIt() {
    val app = sampleApps(1).first()
    val previous = listOf(
      LauncherIconNode(
        app = app,
        launchCount = 0,
        sizePx = 44.0,
        radiusPx = 22.0,
        x = 100.0,
        y = 100.0,
      ),
    )

    val nodes = LauncherIconLayout.build(
      apps = listOf(app),
      launchCounts = emptyMap(),
      widthPx = 900,
      heightPx = 1400,
      minSizePx = 44.0,
      maxSizePx = 112.0,
      previousNodes = previous,
      anchorPositions = mapOf(app.packageName to Point(400.0, 600.0)),
    )

    assertTrue(nodes.single().x > 100.0)
    assertTrue(nodes.single().y > 100.0)
    assertTrue(nodes.single().x < 400.0)
    assertTrue(nodes.single().y < 600.0)
  }

  @Test
  fun closeResetAnchorsSettleApartAfterFixedModeEnds() {
    val apps = sampleApps(2)
    val closePositions = mapOf(
      apps[0].packageName to Point(450.0, 700.0),
      apps[1].packageName to Point(452.0, 702.0),
    )
    val fixedNodes = LauncherIconLayout.build(
      apps = apps,
      launchCounts = emptyMap(),
      widthPx = 900,
      heightPx = 1400,
      minSizePx = 44.0,
      maxSizePx = 112.0,
      anchorPositions = closePositions,
      fixedPositions = closePositions,
    )

    val settledNodes = LauncherIconLayout.build(
      apps = apps,
      launchCounts = emptyMap(),
      widthPx = 900,
      heightPx = 1400,
      minSizePx = 44.0,
      maxSizePx = 112.0,
      previousNodes = fixedNodes,
      anchorPositions = closePositions,
    )

    val distance = hypot(
      settledNodes[0].x - settledNodes[1].x,
      settledNodes[0].y - settledNodes[1].y,
    )
    assertTrue(distance >= settledNodes[0].radiusPx + settledNodes[1].radiusPx)
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
