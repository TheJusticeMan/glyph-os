package com.thejusticeman.glyphos

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val GOLDEN_ANGLE = 2.399963229728653
private const val DEFAULT_ITERATIONS = 90
private const val PADDING_FACTOR = 0.28
private const val ANCHOR_PULL = 0.006
private const val REPULSION_DAMPING = 0.58
private const val DEFAULT_SIZE_COUNT_CAP = 16

data class LauncherIconNode(
  val app: AppDetail,
  val launchCount: Int,
  val sizePx: Double,
  val radiusPx: Double,
  val x: Double,
  val y: Double,
)

data class LauncherIconSlot(
  val index: Int,
  val x: Double,
  val y: Double,
)

data class LauncherIconGhostNode(
  val x: Double,
  val y: Double,
  val radiusPx: Double,
)

object LauncherIconLayout {
  fun build(
    apps: List<AppDetail>,
    launchCounts: Map<String, Int>,
    widthPx: Int,
    heightPx: Int,
    minSizePx: Double,
    maxSizePx: Double,
    previousNodes: List<LauncherIconNode> = emptyList(),
    anchorPositions: Map<String, Point> = emptyMap(),
    fixedPositions: Map<String, Point> = emptyMap(),
    ghostNodes: List<LauncherIconGhostNode> = emptyList(),
    iterations: Int = DEFAULT_ITERATIONS,
  ): List<LauncherIconNode> {
    if (apps.isEmpty() || widthPx <= 0 || heightPx <= 0) return emptyList()

    val maxCount = DEFAULT_SIZE_COUNT_CAP
    val previousByPackage = previousNodes.associateBy { it.app.packageName }
    val sortedApps = sortedLayoutApps(apps)
    val nodes = sortedApps.mapIndexed { index, app ->
      val count = launchCounts[app.packageName]?.coerceAtLeast(0) ?: 0
      val size = iconSize(count, maxCount, minSizePx, maxSizePx)
      val radius = size / 2.0
      val anchor = anchorFor(index, sortedApps.size, widthPx.toDouble(), heightPx.toDouble(), radius)
      val previous = previousByPackage[app.packageName]
      val anchorPosition = anchorPositions[app.packageName]
      val fixedPosition = fixedPositions[app.packageName]
      val targetAnchor = fixedPosition ?: anchorPosition ?: anchor
      val startX = fixedPosition?.x ?: previous?.x ?: anchor.x
      val startY = fixedPosition?.y ?: previous?.y ?: anchor.y
      MutableNode(
        app = app,
        launchCount = count,
        sizePx = size,
        radiusPx = radius,
        anchorX = targetAnchor.x,
        anchorY = targetAnchor.y,
        fixed = fixedPosition != null,
        x = startX,
        y = startY,
      )
    } + ghostNodes.map { ghost ->
      MutableNode(
        app = null,
        launchCount = 0,
        sizePx = ghost.radiusPx * 2.0,
        radiusPx = ghost.radiusPx,
        anchorX = ghost.x,
        anchorY = ghost.y,
        fixed = true,
        x = ghost.x,
        y = ghost.y,
      )
    }

    repeat(iterations.coerceAtLeast(0)) {
      separate(nodes)
      pullToAnchors(nodes)
      clamp(nodes, widthPx.toDouble(), heightPx.toDouble())
    }

    return nodes.mapNotNull { node ->
      val app = node.app ?: return@mapNotNull null
      LauncherIconNode(
        app = app,
        launchCount = node.launchCount,
        sizePx = node.sizePx,
        radiusPx = node.radiusPx,
        x = node.x,
        y = node.y,
      )
    }
  }

  fun automaticSlots(
    apps: List<AppDetail>,
    launchCounts: Map<String, Int>,
    widthPx: Int,
    heightPx: Int,
    minSizePx: Double,
    maxSizePx: Double,
  ): List<LauncherIconSlot> {
    if (apps.isEmpty() || widthPx <= 0 || heightPx <= 0) return emptyList()

    return sortedLayoutApps(apps).mapIndexed { index, app ->
      val count = launchCounts[app.packageName]?.coerceAtLeast(0) ?: 0
      val size = iconSize(count, DEFAULT_SIZE_COUNT_CAP, minSizePx, maxSizePx)
      val radius = size / 2.0
      val anchor = anchorFor(index, apps.size, widthPx.toDouble(), heightPx.toDouble(), radius)
      LauncherIconSlot(index = index, x = anchor.x, y = anchor.y)
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
    if (index == 0 || total == 1) return Point(width / 2.0, height / 2.0)

    val usableWidth = (width - radius * 2.0).coerceAtLeast(1.0)
    val usableHeight = (height - radius * 2.0).coerceAtLeast(1.0)
    val maxOrbitX = usableWidth / 2.0
    val maxOrbitY = usableHeight / 2.0
    val t = sqrt(index / total.toDouble())
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
        val pushX = dx / distance * overlap
        val pushY = dy / distance * overlap
        when {
          left.fixed && right.fixed -> Unit
          left.fixed -> {
            right.x += pushX
            right.y += pushY
          }
          right.fixed -> {
            left.x -= pushX
            left.y -= pushY
          }
          else -> {
            left.x -= pushX / 2.0
            left.y -= pushY / 2.0
            right.x += pushX / 2.0
            right.y += pushY / 2.0
          }
        }
      }
    }
  }

  private fun pullToAnchors(nodes: List<MutableNode>) {
    for (node in nodes) {
      if (node.fixed) continue
      node.x += (node.anchorX - node.x) * ANCHOR_PULL
      node.y += (node.anchorY - node.y) * ANCHOR_PULL
    }
  }

  private fun clamp(nodes: List<MutableNode>, width: Double, height: Double) {
    for (node in nodes) {
      val minX = node.radiusPx
      val maxX = width - node.radiusPx
      node.x = if (maxX >= minX) {
        node.x.coerceIn(minX, maxX)
      } else {
        width / 2.0
      }

      val minY = node.radiusPx
      val maxY = height - node.radiusPx
      node.y = if (maxY >= minY) {
        node.y.coerceIn(minY, maxY)
      } else {
        height / 2.0
      }
    }
  }

  private fun sortedLayoutApps(apps: List<AppDetail>): List<AppDetail> {
    return apps.sortedWith(
      compareBy<AppDetail> { stableRank(it.packageName) }
        .thenBy { it.label.lowercase() }
        .thenBy { it.packageName },
    )
  }

  private fun stableRank(value: String): Int {
    var hash = 0
    value.forEach { character ->
      hash = hash * 31 + character.code
    }
    return abs(hash)
  }

  private data class MutableNode(
    val app: AppDetail?,
    val launchCount: Int,
    val sizePx: Double,
    val radiusPx: Double,
    val anchorX: Double,
    val anchorY: Double,
    val fixed: Boolean,
    var x: Double,
    var y: Double,
  )
}
