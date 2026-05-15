package com.thejusticeman.glyphos

import kotlin.math.roundToInt

// Home icon layout, anchor persistence, physics ghost nodes, and animated reset.
//
// Position state overview:
//   homeIconAnchors      — persisted user-set positions; soft-pull targets for the physics solver
//   dragOverridePositions — hard-fixed position during an active drag; cleared on drop
//   homeIconResetState   — drives the step-by-step animated reset; non-null only while reset is running
//
// Invariant: any user input (drag, gesture) cancels an in-progress reset.

internal data class HomeIconResetStep(
  val packageName: String,
  val position: Point,
)

internal data class HomeIconResetState(
  val steps: List<HomeIconResetStep>,
  val fixedPositions: Map<String, Point> = emptyMap(),
  val nextIndex: Int = 0,
  val activeStep: HomeIconResetStep? = null,
)

internal fun MainActivity.updateLauncherIcons(apps: List<AppDetail> = cachedInstalledApps()) {
  if (!isCanvasViewInitialized) return
  if (canvasView.width <= 0 || canvasView.height <= 0) {
    canvasView.post { updateLauncherIcons(apps) }
    return
  }

  val minIconSize = dp(HOME_ICON_MIN_DP).toDouble()
  val maxIconSize = dp(HOME_ICON_MAX_DP).toDouble()
  val previousIcons = canvasView.launcherIcons
  canvasView.launcherIcons = LauncherIconLayout.build(
    apps = selectHomeApps(apps, minIconSize, maxIconSize),
    launchCounts = launchCounts,
    widthPx = canvasView.width,
    heightPx = canvasView.height,
    minSizePx = minIconSize,
    maxSizePx = maxIconSize,
    previousNodes = previousIcons,
    anchorPositions = homeIconAnchors,
    fixedPositions = fixedHomeIconPositions(),
    ghostNodes = gestureGhostNodes(),
  )
}

internal fun MainActivity.selectHomeApps(apps: List<AppDetail>, minIconSize: Double, maxIconSize: Double): List<AppDetail> {
  if (apps.isEmpty()) return emptyList()

  val maxCount = launchCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
  val sortedApps = apps.sortedWith(
    compareByDescending<AppDetail> { launchCounts[it.packageName]?.coerceAtLeast(0) ?: 0 }
      .thenBy { it.label.lowercase() }
      .thenBy { it.packageName },
  )
  val canvasArea = canvasView.width.toDouble() * canvasView.height.toDouble()
  val areaBudget = canvasArea * HOME_ICON_AREA_BUDGET
  val adaptiveMaxVisible = (canvasArea / (minIconSize * minIconSize * HOME_ICON_FOOTPRINT_FACTOR))
    .roundToInt()
    .coerceIn(HOME_ICON_MIN_VISIBLE, HOME_ICON_MAX_VISIBLE)
    .coerceAtMost(apps.size)
  val minimumVisible = HOME_ICON_MIN_VISIBLE.coerceAtMost(apps.size)
  val selected = mutableListOf<AppDetail>()
  var usedArea = 0.0

  for (app in sortedApps) {
    val launchCount = launchCounts[app.packageName]?.coerceAtLeast(0) ?: 0
    val iconSize = LauncherIconLayout.iconSize(launchCount, maxCount, minIconSize, maxIconSize)
    val footprint = iconSize * iconSize * HOME_ICON_FOOTPRINT_FACTOR
    val mustFillStarterSet = selected.size < minimumVisible
    val fitsFrequencySurface = selected.size < adaptiveMaxVisible && usedArea + footprint <= areaBudget
    if (!mustFillStarterSet && !fitsFrequencySurface) continue

    selected += app
    usedArea += footprint
  }

  return selected
}

internal fun MainActivity.handleHomeIconPositionChanging(
  app: AppDetail,
  x: Float,
  y: Float,
  width: Int,
  height: Int,
) {
  homeIconResetState = null
  updateTrashDropTargetHighlight(isTrashDropTargetHit(x, y))
  val anchor = Point(x.toDouble(), y.toDouble())
  homeIconAnchors = homeIconAnchors + (app.packageName to anchor)
  dragOverridePositions = mapOf(app.packageName to anchor)
  updateLauncherIcons()
}

internal fun MainActivity.handleHomeIconPositionCommitted(
  app: AppDetail,
  x: Float,
  y: Float,
  width: Int,
  height: Int,
) {
  homeIconResetState = null
  if (isTrashDropTargetHit(x, y)) {
    resetAppLaunchCount(app)
    setTrashDropTargetVisible(false, trashDropTargetMode)
    return
  }
  homeIconAnchors = homeIconAnchors + (app.packageName to Point(x.toDouble(), y.toDouble()))
  dragOverridePositions = emptyMap()
  persistHomeIconAnchors()
  updateLauncherIcons()
}

internal fun MainActivity.resetAppLaunchCount(app: AppDetail) {
  launchUsageStore.reset(app.packageName)
  launchCounts = launchCounts - app.packageName
  homeIconAnchors = homeIconAnchors - app.packageName
  dragOverridePositions = emptyMap()
  persistHomeIconAnchors()
  updateLauncherIcons()
}

internal fun MainActivity.fixedHomeIconPositions(): Map<String, Point> {
  if (!isCanvasViewInitialized || canvasView.width <= 0 || canvasView.height <= 0) return emptyMap()
  return homeIconResetState?.fixedPositions.orEmpty() + dragOverridePositions
}

internal fun MainActivity.gestureGhostNodes(): List<LauncherIconGhostNode> {
  val gestureGhosts = gestureGhostPosition?.let { position ->
    listOf(
      LauncherIconGhostNode(
        x = position.x,
        y = position.y,
        radiusPx = dp(HOME_ICON_GHOST_RADIUS_DP).toDouble(),
      ),
    )
  }.orEmpty()

  val maxGhostRadius = if (isCanvasViewInitialized && canvasView.width > 0 && canvasView.height > 0) {
    minOf(canvasView.width, canvasView.height) * 0.45
  } else {
    Double.MAX_VALUE
  }

  val widgetGhosts = widgetPlacements.values.map { placement ->
    val longestSide = maxOf(placement.width, placement.height).toDouble()
    val shortestSide = minOf(placement.width, placement.height).toDouble()
    val radius = (
      longestSide * 0.28 +
        shortestSide * 0.08 +
        dp(6)
      )
      .coerceAtLeast(dp(20).toDouble())
      .coerceAtMost(maxGhostRadius)
    LauncherIconGhostNode(
      x = placement.x + placement.width / 2.0,
      y = placement.y + placement.height / 2.0,
      radiusPx = radius,
    )
  }

  return gestureGhosts + widgetGhosts
}

internal fun MainActivity.handleGestureGhostChanged(position: Point?) {
  if (position != null) homeIconResetState = null
  if (gestureGhostPosition == position) return

  gestureGhostPosition = position
  updateLauncherIcons()
}

internal fun MainActivity.beginHomeIconLayoutReset() {
  if (!isCanvasViewInitialized) return
  if (canvasView.width <= 0 || canvasView.height <= 0) {
    canvasView.post { beginHomeIconLayoutReset() }
    return
  }

  val apps = cachedInstalledApps()
  val minIconSize = dp(HOME_ICON_MIN_DP).toDouble()
  val maxIconSize = dp(HOME_ICON_MAX_DP).toDouble()
  val selectedApps = selectHomeApps(apps, minIconSize, maxIconSize)
  val steps = buildHomeIconResetSteps(selectedApps, minIconSize, maxIconSize)
  if (steps.isEmpty()) {
    return
  }

  homeIconAnchors = emptyMap()
  dragOverridePositions = emptyMap()
  canvasView.editMode = false
  homeIconResetState = HomeIconResetState(steps = steps)
  advanceHomeIconResetStep()
}

internal fun MainActivity.buildHomeIconResetSteps(
  selectedApps: List<AppDetail>,
  minIconSize: Double,
  maxIconSize: Double,
): List<HomeIconResetStep> {
  if (selectedApps.isEmpty()) return emptyList()

  val slots = LauncherIconLayout.automaticSlots(
    apps = selectedApps,
    launchCounts = launchCounts,
    widthPx = canvasView.width,
    heightPx = canvasView.height,
    minSizePx = minIconSize,
    maxSizePx = maxIconSize,
  )
  val currentByPackage = canvasView.launcherIcons.associateBy { it.app.packageName }
  val fallbackByPackage = LauncherIconLayout.build(
    apps = selectedApps,
    launchCounts = launchCounts,
    widthPx = canvasView.width,
    heightPx = canvasView.height,
    minSizePx = minIconSize,
    maxSizePx = maxIconSize,
    iterations = 0,
  ).associateBy { it.app.packageName }
  val availableIcons = selectedApps.mapNotNull { app ->
    currentByPackage[app.packageName] ?: fallbackByPackage[app.packageName]
  }.toMutableList()

  return slots.mapNotNull { slot ->
    var nextIcon: LauncherIconNode? = null
    for (icon in availableIcons) {
      val currentBest = nextIcon
      if (currentBest == null || isBetterResetCandidate(icon, currentBest, slot)) {
        nextIcon = icon
      }
    }

    nextIcon ?: return@mapNotNull null
    availableIcons.remove(nextIcon)
    HomeIconResetStep(
      packageName = nextIcon.app.packageName,
      position = Point(slot.x, slot.y),
    )
  }
}

internal fun MainActivity.isBetterResetCandidate(
  candidate: LauncherIconNode,
  currentBest: LauncherIconNode,
  slot: LauncherIconSlot,
): Boolean {
  val candidateCount = launchCounts[candidate.app.packageName]?.coerceAtLeast(0) ?: 0
  val currentCount = launchCounts[currentBest.app.packageName]?.coerceAtLeast(0) ?: 0
  if (candidateCount != currentCount) return candidateCount > currentCount

  val candidateDistance = distanceSquared(candidate.x, candidate.y, slot.x, slot.y)
  val currentDistance = distanceSquared(currentBest.x, currentBest.y, slot.x, slot.y)
  if (candidateDistance != currentDistance) return candidateDistance < currentDistance

  val candidateLabel = candidate.app.label.lowercase()
  val currentLabel = currentBest.app.label.lowercase()
  if (candidateLabel != currentLabel) return candidateLabel < currentLabel

  return candidate.app.packageName < currentBest.app.packageName
}

internal fun MainActivity.advanceHomeIconResetStep() {
  val state = homeIconResetState ?: return
  if (state.nextIndex >= state.steps.size) {
    finishHomeIconLayoutReset()
    return
  }

  val step = state.steps[state.nextIndex]
  val fixedPositions = state.fixedPositions + (step.packageName to step.position)
  homeIconResetState = state.copy(
    fixedPositions = fixedPositions,
    activeStep = step,
  )
  homeIconAnchors = homeIconAnchors + (step.packageName to step.position)
  updateLauncherIcons()
}

internal fun MainActivity.handleHomeIconLayoutSettled() {
  val state = homeIconResetState
  if (state == null) {
    return
  }
  val activeStep = state.activeStep ?: return
  val activeIcon = canvasView.launcherIcons.firstOrNull { icon -> icon.app.packageName == activeStep.packageName } ?: return
  val settleDistanceSquared = HOME_ICON_RESET_SETTLE_PX * HOME_ICON_RESET_SETTLE_PX
  if (distanceSquared(activeIcon.x, activeIcon.y, activeStep.position.x, activeStep.position.y) > settleDistanceSquared) {
    return
  }

  val nextState = state.copy(
    nextIndex = state.nextIndex + 1,
    activeStep = null,
  )
  homeIconResetState = nextState
  if (nextState.nextIndex >= nextState.steps.size) {
    finishHomeIconLayoutReset()
  } else {
    advanceHomeIconResetStep()
  }
}

internal fun MainActivity.finishHomeIconLayoutReset() {
  val finalPositions = homeIconResetState?.fixedPositions.orEmpty()
  homeIconResetState = null
  if (finalPositions.isNotEmpty()) {
    homeIconAnchors = finalPositions
    persistHomeIconAnchors()
  }
  updateLauncherIcons()
}

internal fun MainActivity.persistHomeIconAnchors() {
  homeIconAnchorStore.saveAnchors(homeIconAnchors)
}

internal fun MainActivity.distanceSquared(leftX: Double, leftY: Double, rightX: Double, rightY: Double): Double {
  val dx = leftX - rightX
  val dy = leftY - rightY
  return dx * dx + dy * dy
}
