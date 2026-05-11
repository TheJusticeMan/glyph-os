package com.thejusticeman.glyphos

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Point(val x: Double, val y: Double)

data class SavedGesture(
  val label: String,
  val packageName: String? = null,
  val specialActionId: String? = null,
  val normalizedPath: List<Point>,
) {
  fun targetKey(): String? {
    packageName?.let { return "package:$it" }
    specialActionId?.let { return "special:$it" }
    return null
  }
}

data class MatchResult(
  val gesture: SavedGesture,
  val angularDifference: Double,
  val gestureIndex: Int,
)

data class SimilarAppMatch(
  val packageName: String,
  val angularDifference: Double,
)

data class SimilarTargetMatch(
  val targetKey: String,
  val angularDifference: Double,
)

data class BlendBoundsResult(
  val canMerge: Boolean,
  val minT: Double,
  val maxT: Double,
)

const val NUM_POINTS = 40
const val ANGULAR_THRESHOLD = 0.5
const val MIN_GESTURE_LENGTH_PX = 200.0
const val GESTURE_ADAPTATION_RATE = 0.05
const val GOOGLE_APP_PACKAGE_NAME = "com.google.android.googlequicksearchbox"

private fun distance(a: Point, b: Point): Double {
  val dx = b.x - a.x
  val dy = b.y - a.y
  return sqrt(dx * dx + dy * dy)
}

fun totalPathLength(points: List<Point>): Double {
  var length = 0.0
  for (index in 1 until points.size) {
    length += distance(points[index - 1], points[index])
  }
  return length
}

fun normalizeTo40Points(rawPoints: List<Point>): List<Point>? {
  if (rawPoints.size < 2) return null

  val total = totalPathLength(rawPoints)
  if (total < MIN_GESTURE_LENGTH_PX) return null

  val interval = total / (NUM_POINTS - 1)
  val resampled = mutableListOf(rawPoints.first().copy())

  var accumulated = 0.0
  var previous = rawPoints.first()
  var index = 1

  while (index < rawPoints.size && resampled.size < NUM_POINTS - 1) {
    val current = rawPoints[index]
    val segmentLength = distance(previous, current)
    val remaining = interval - accumulated

    if (segmentLength >= remaining) {
      val t = remaining / segmentLength
      val newPoint = Point(
        x = previous.x + t * (current.x - previous.x),
        y = previous.y + t * (current.y - previous.y),
      )
      resampled += newPoint
      previous = newPoint
      accumulated = 0.0
    } else {
      accumulated += segmentLength
      previous = current
      index += 1
    }
  }

  resampled += rawPoints.last().copy()
  return resampled
}

fun calculateDifference(line1: List<Point>, line2: List<Point>): Double {
  val count = min(line1.size, line2.size)
  if (count < 2) return Double.POSITIVE_INFINITY

  var totalDifference = 0.0
  for (index in 0 until count - 1) {
    val v1x = line1[index + 1].x - line1[index].x
    val v1y = line1[index + 1].y - line1[index].y
    val v2x = line2[index + 1].x - line2[index].x
    val v2y = line2[index + 1].y - line2[index].y

    val angle1 = atan2(v1y, v1x)
    val angle2 = atan2(v2y, v2x)

    var difference = abs(angle1 - angle2)
    if (difference > PI) {
      difference = 2 * PI - difference
    }
    totalDifference += difference
  }

  return totalDifference / (count - 1)
}

fun calculateBestDirectionDifference(
  candidate: List<Point>,
  reference: List<Point>,
  allowBackward: Boolean,
): Double {
  val forward = calculateDifference(candidate, reference)
  if (!allowBackward) return forward
  val reversed = calculateDifference(candidate, reference.asReversed())
  return min(forward, reversed)
}

fun matchGesture(
  normalizedPoints: List<Point>,
  savedGestures: List<SavedGesture>,
  allowBackward: Boolean = false,
): MatchResult? {
  if (savedGestures.isEmpty()) return null

  var best: MatchResult? = null
  var minDifference = Double.POSITIVE_INFINITY

  for ((index, gesture) in savedGestures.withIndex()) {
    if (gesture.normalizedPath.size < 2) continue

    val difference = calculateBestDirectionDifference(
      normalizedPoints,
      gesture.normalizedPath,
      allowBackward,
    )
    if (difference < minDifference) {
      minDifference = difference
      best = MatchResult(gesture, difference, index)
    }
  }

  return if (best != null && minDifference < ANGULAR_THRESHOLD) best else null
}

fun rankSimilarApps(
  normalizedPoints: List<Point>,
  savedGestures: List<SavedGesture>,
  limit: Int = 5,
  allowBackward: Boolean = false,
): List<SimilarAppMatch> {
  val byPackage = mutableMapOf<String, Double>()

  for (gesture in savedGestures) {
    val packageName = gesture.packageName ?: continue
    if (gesture.normalizedPath.size < 2) continue

    val difference = calculateBestDirectionDifference(
      normalizedPoints,
      gesture.normalizedPath,
      allowBackward,
    )
    val current = byPackage[packageName]
    if (current == null || difference < current) {
      byPackage[packageName] = difference
    }
  }

  return byPackage.entries
    .map { SimilarAppMatch(it.key, it.value) }
    .sortedWith(compareBy<SimilarAppMatch> { it.angularDifference }.thenBy { it.packageName })
    .take(max(0, limit))
}

fun rankSimilarTargets(
  normalizedPoints: List<Point>,
  savedGestures: List<SavedGesture>,
  limit: Int = 5,
  allowBackward: Boolean = false,
): List<SimilarTargetMatch> {
  val byTarget = mutableMapOf<String, Double>()

  for (gesture in savedGestures) {
    val targetKey = gesture.targetKey() ?: continue
    if (gesture.normalizedPath.size < 2) continue

    val difference = calculateBestDirectionDifference(
      normalizedPoints,
      gesture.normalizedPath,
      allowBackward,
    )
    val current = byTarget[targetKey]
    if (current == null || difference < current) {
      byTarget[targetKey] = difference
    }
  }

  return byTarget.entries
    .map { SimilarTargetMatch(it.key, it.value) }
    .sortedWith(compareBy<SimilarTargetMatch> { it.angularDifference }.thenBy { it.targetKey })
    .take(max(0, limit))
}

fun defaultOpenAppListGesture(): SavedGesture {
  val path = upwardLinePath()
  return SavedGesture(
    label = "Open app list",
    specialActionId = SPECIAL_ACTION_OPEN_APP_LIST,
    normalizedPath = path,
  )
}

fun defaultOpenGoogleGesture(): SavedGesture {
  val path = horizontalLinePath()
  return SavedGesture(
    label = "Open Google",
    packageName = GOOGLE_APP_PACKAGE_NAME,
    normalizedPath = path,
  )
}

fun defaultHorizontalLineGesturePath(): List<Point> = horizontalLinePath()

private fun horizontalLinePath(): List<Point> {
  return List(NUM_POINTS) { index ->
    Point(x = index * 240.0 / (NUM_POINTS - 1), y = 0.0)
  }
}

private fun upwardLinePath(): List<Point> {
  val path = List(NUM_POINTS) { index ->
    Point(x = 0.0, y = -index * 240.0 / (NUM_POINTS - 1))
  }
  return path
}

fun blendNormalizedPaths(lineA: List<Point>, lineB: List<Point>, t: Double): List<Point> {
  val count = min(lineA.size, lineB.size)
  if (count < 2) return emptyList()

  val clamped = max(0.0, min(1.0, t))
  return (0 until count).map { index ->
    Point(
      x = lineA[index].x * (1 - clamped) + lineB[index].x * clamped,
      y = lineA[index].y * (1 - clamped) + lineB[index].y * clamped,
    )
  }
}

fun adaptNormalizedPath(
  savedPath: List<Point>,
  usedPath: List<Point>,
  amount: Double = GESTURE_ADAPTATION_RATE,
  allowBackward: Boolean = false,
): List<Point> {
  if (savedPath.size < 2 || usedPath.size != savedPath.size) return savedPath

  val alignedUsedPath = if (allowBackward) {
    val forwardDifference = calculateDifference(usedPath, savedPath)
    val backwardDifference = calculateDifference(usedPath, savedPath.asReversed())
    if (backwardDifference < forwardDifference) usedPath.asReversed() else usedPath
  } else {
    usedPath
  }

  return blendNormalizedPaths(savedPath, alignedUsedPath, amount).takeIf { it.size == savedPath.size } ?: savedPath
}

fun isWithinThreshold(
  candidate: List<Point>,
  reference: List<Point>,
  threshold: Double = ANGULAR_THRESHOLD,
): Boolean {
  return calculateDifference(candidate, reference) < threshold
}

fun findBlendBoundsForDualMatch(oldPath: List<Point>, newPath: List<Point>): BlendBoundsResult {
  val midpoint = blendNormalizedPaths(oldPath, newPath, 0.5)

  fun matchesBoth(candidate: List<Point>): Boolean {
    return isWithinThreshold(candidate, oldPath) && isWithinThreshold(candidate, newPath)
  }

  if (midpoint.size < 2 || !matchesBoth(midpoint)) {
    return BlendBoundsResult(canMerge = false, minT = 0.5, maxT = 0.5)
  }

  fun matchesAt(t: Double): Boolean = matchesBoth(blendNormalizedPaths(oldPath, newPath, t))

  val step = 0.05
  val iterations = 18

  var left = 0.5
  while (left - step >= 0 && matchesAt(left - step)) {
    left -= step
  }

  var right = 0.5
  while (right + step <= 1 && matchesAt(right + step)) {
    right += step
  }

  var minT = left
  if (left > 0 && !matchesAt(0.0)) {
    var lo = max(0.0, left - step)
    var hi = left
    repeat(iterations) {
      val mid = (lo + hi) / 2
      if (matchesAt(mid)) {
        hi = mid
      } else {
        lo = mid
      }
    }
    minT = hi
  } else if (matchesAt(0.0)) {
    minT = 0.0
  }

  var maxT = right
  if (right < 1 && !matchesAt(1.0)) {
    var lo = right
    var hi = min(1.0, right + step)
    repeat(iterations) {
      val mid = (lo + hi) / 2
      if (matchesAt(mid)) {
        lo = mid
      } else {
        hi = mid
      }
    }
    maxT = lo
  } else if (matchesAt(1.0)) {
    maxT = 1.0
  }

  return BlendBoundsResult(canMerge = true, minT = minT, maxT = maxT)
}
