package com.thejusticeman.glyphos

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val GOLDEN_ANGLE = 2.399963229728653
private const val DEFAULT_ITERATIONS = 90
private const val PADDING_FACTOR = 0.28
private const val ANCHOR_PULL = 0.025
private const val REPULSION_DAMPING = 0.58

data class LauncherIconNode(
  val app: AppDetail,
  val launchCount: Int,
  val sizePx: Double,
  val radiusPx: Double,
  val x: Double,
  val y: Double,
)

object LauncherIconLayout {
  fun build(
    apps: List<AppDetail>,
    launchCounts: Map<String, Int>,
    widthPx: Int,
    heightPx: Int,
    minSizePx: Double,
    maxSizePx: Double,
    iterations: Int = DEFAULT_ITERATIONS,
  ): List<LauncherIconNode> {
    if (apps.isEmpty() || widthPx <= 0 || heightPx <= 0) return emptyList()

    val maxCount = launchCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val sortedApps = apps.sortedWith(
      compareByDescending<AppDetail> { launchCounts[it.packageName]?.coerceAtLeast(0) ?: 0 }
        .thenBy { it.label.lowercase() }
        .thenBy { it.packageName },
    )
    val nodes = sortedApps.mapIndexed { index, app ->
      val count = launchCounts[app.packageName]?.coerceAtLeast(0) ?: 0
      val size = iconSize(count, maxCount, minSizePx, maxSizePx)
      val radius = size / 2.0
      val anchor = anchorFor(index, sortedApps.size, widthPx.toDouble(), heightPx.toDouble(), radius)
      MutableNode(app, count, size, radius, anchor.x, anchor.y, anchor.x, anchor.y)
    }

    repeat(iterations.coerceAtLeast(0)) {
      separate(nodes)
      pullToAnchors(nodes)
      clamp(nodes, widthPx.toDouble(), heightPx.toDouble())
    }

    return nodes.map { node ->
      LauncherIconNode(
        app = node.app,
        launchCount = node.launchCount,
        sizePx = node.sizePx,
        radiusPx = node.radiusPx,
        x = node.x,
        y = node.y,
      )
    }
  }

  fun iconSize(count: Int, maxCount: Int, minSizePx: Double, maxSizePx: Double): Double {
    val minSize = min(minSizePx, maxSizePx)
    val maxSize = max(minSizePx, maxSizePx)
    if (maxCount <= 0 || count <= 0) return minSize

    val normalized = sqrt(count.toDouble() / maxCount.toDouble()).coerceIn(0.0, 1.0)
    return minSize + (maxSize - minSize) * normalized
  }

  private fun anchorFor(index: Int, total: Int, width: Double, height: Double, radius: Double): Point {
    if (total == 1) return Point(width / 2.0, height / 2.0)

    val usableWidth = (width - radius * 2.0).coerceAtLeast(1.0)
    val usableHeight = (height - radius * 2.0).coerceAtLeast(1.0)
    val maxOrbitX = usableWidth / 2.0
    val maxOrbitY = usableHeight / 2.0
    val t = sqrt((index + 0.5) / total.toDouble())
    val angle = index * GOLDEN_ANGLE
    val orbit = t * 0.92

    return Point(
      x = width / 2.0 + cos(angle) * maxOrbitX * orbit,
      y = height / 2.0 + sin(angle) * maxOrbitY * orbit,
    )
  }

  private fun separate(nodes: List<MutableNode>) {
    for (leftIndex in nodes.indices) {
      val left = nodes[leftIndex]
      for (rightIndex in leftIndex + 1 until nodes.size) {
        val right = nodes[rightIndex]
        var dx = right.x - left.x
        var dy = right.y - left.y
        var distanceSquared = dx * dx + dy * dy

        if (distanceSquared < 0.0001) {
          val angle = ((leftIndex + 1) * 37 + (rightIndex + 1) * 17) * PI / 180.0
          dx = cos(angle)
          dy = sin(angle)
          distanceSquared = 1.0
        }

        val distance = sqrt(distanceSquared)
        val minimumDistance = left.radiusPx + right.radiusPx + max(left.radiusPx, right.radiusPx) * PADDING_FACTOR
        if (distance >= minimumDistance) continue

        val overlap = (minimumDistance - distance) * REPULSION_DAMPING
        val pushX = dx / distance * overlap / 2.0
        val pushY = dy / distance * overlap / 2.0
        left.x -= pushX
        left.y -= pushY
        right.x += pushX
        right.y += pushY
      }
    }
  }

  private fun pullToAnchors(nodes: List<MutableNode>) {
    for (node in nodes) {
      node.x += (node.anchorX - node.x) * ANCHOR_PULL
      node.y += (node.anchorY - node.y) * ANCHOR_PULL
    }
  }

  private fun clamp(nodes: List<MutableNode>, width: Double, height: Double) {
    for (node in nodes) {
      node.x = node.x.coerceIn(node.radiusPx, width - node.radiusPx)
      node.y = node.y.coerceIn(node.radiusPx, height - node.radiusPx)
    }
  }

  private data class MutableNode(
    val app: AppDetail,
    val launchCount: Int,
    val sizePx: Double,
    val radiusPx: Double,
    val anchorX: Double,
    val anchorY: Double,
    var x: Double,
    var y: Double,
  )
}
