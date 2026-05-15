package com.thejusticeman.glyphos

import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import kotlin.math.roundToInt

// Gesture matching, assignment, execution, conflict resolution, and default seeding.

internal data class PendingGesture(
  val label: String,
  val normalizedPath: List<Point>,
)

internal fun MainActivity.handleGestureComplete(normalizedPoints: List<Point>) {
  val match = matchGesture(
    normalizedPoints,
    savedGestures,
    allowBackward = settings.allowBackwardGestures,
  )

  if (match != null) {
    val executed = executeGesture(match.gesture)
    if (executed) {
      adaptMatchedGesture(match.gestureIndex, normalizedPoints)
    }
    return
  }

  requestAssignApp("gesture_${System.currentTimeMillis()}", normalizedPoints)
}

internal fun MainActivity.requestAssignApp(label: String, normalizedPath: List<Point>) {
  pendingGesture = PendingGesture(label, normalizedPath)
  val prioritizedTargetKeys = rankSimilarTargets(
    normalizedPath,
    savedGestures,
    limit = 5,
    allowBackward = settings.allowBackwardGestures,
  ).map { it.targetKey }

  showAppPickerDialog(
    title = "Assign App to Gesture",
    prioritizedTargetKeys = prioritizedTargetKeys,
    includeSpecialFunctions = true,
  ) { selectedApp ->
    handleAssignApp(selectedApp)
  }
}

internal fun MainActivity.handleAssignApp(target: AppDetail) {
  val incoming = pendingGesture ?: return
  val selectedTargetKey = target.targetKey()
  val existingForApp = savedGestures.filter { gesture ->
    gesture.targetKey() == selectedTargetKey && gesture.normalizedPath.size >= 2
  }

  if (existingForApp.isEmpty()) {
    appendBinding(target, incoming)
    return
  }

  val mergeTarget = existingForApp.minByOrNull { gesture ->
    calculateBestDirectionDifference(
      incoming.normalizedPath,
      gesture.normalizedPath,
      settings.allowBackwardGestures,
    )
  }

  if (mergeTarget == null) {
    appendBinding(target, incoming)
    return
  }

  val bounds = findBlendBoundsForDualMatch(mergeTarget.normalizedPath, incoming.normalizedPath)
  if (!bounds.canMerge) {
    appendBinding(target, incoming)
    return
  }

  showMergeDialog(target, mergeTarget, incoming, bounds)
}

internal fun MainActivity.appendBinding(target: AppDetail, gesture: PendingGesture) {
  savedGestures += SavedGesture(
    label = gesture.label,
    packageName = target.packageName.takeUnless { target.isSpecialFunction() },
    specialActionId = target.specialActionId,
    normalizedPath = gesture.normalizedPath,
  )
  persistGestures()
  maybeLaunchAfterAssign(target)
  pendingGesture = null
}

internal fun MainActivity.maybeLaunchAfterAssign(target: AppDetail) {
  if (!settings.launchOnCreateShortcut) return
  if (target.isSpecialFunction()) {
    executeSpecialFunction(target.specialActionId)
  } else {
    val launched = launchAppPackage(target.packageName)
    if (!launched) showFeedback("Assigned, but launch failed")
  }
}

internal fun MainActivity.executeGesture(gesture: SavedGesture): Boolean {
  return when {
    gesture.specialActionId != null -> executeSpecialFunction(gesture.specialActionId)
    gesture.packageName != null -> {
      val launched = launchAppPackage(gesture.packageName)
      if (!launched) showFeedback("Launch failed")
      launched
    }
    else -> {
      requestAssignApp(gesture.label, gesture.normalizedPath)
      false
    }
  }
}

internal fun MainActivity.adaptMatchedGesture(gestureIndex: Int, normalizedPoints: List<Point>) {
  val gesture = savedGestures.getOrNull(gestureIndex) ?: return
  val adaptedPath = adaptNormalizedPath(
    savedPath = gesture.normalizedPath,
    usedPath = normalizedPoints,
    allowBackward = settings.allowBackwardGestures,
  )
  if (adaptedPath == gesture.normalizedPath) return

  savedGestures[gestureIndex] = gesture.copy(normalizedPath = adaptedPath)
  persistGestures()
}

internal fun MainActivity.executeSpecialFunction(actionId: String?): Boolean {
  return when (actionId) {
    SPECIAL_ACTION_OPEN_APP_LIST -> {
      showLaunchListDialog()
      true
    }
    else -> {
      false
    }
  }
}

internal fun MainActivity.showLaunchListDialog() {
  showAppPickerDialog(
    title = "Open App",
    includeSpecialFunctions = false,
  ) { app ->
    val launched = launchAppPackage(app.packageName)
    if (!launched) showFeedback("Launch failed")
  }
}

internal fun MainActivity.showMergeDialog(
  target: AppDetail,
  mergeTarget: SavedGesture,
  incoming: PendingGesture,
  bounds: BlendBoundsResult,
) {
  var currentT = (bounds.minT + bounds.maxT) / 2

  val content = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(0, dp(8), 0, 0)
  }

  content.addView(bodyText("${target.label} already has a gesture. Merge the old and new swipe, or keep both bindings."))

  val previews = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER
    setPadding(0, dp(12), 0, dp(8))
  }
  val mergedPreview = GesturePreviewView(this)
  previews.addView(previewColumn("Old", mergeTarget.normalizedPath), weightedParams())
  previews.addView(previewColumn("Merged", blendNormalizedPaths(mergeTarget.normalizedPath, incoming.normalizedPath, currentT), mergedPreview), weightedParams())
  previews.addView(previewColumn("New", incoming.normalizedPath), weightedParams())
  content.addView(previews)

  val choiceGroup = RadioGroup(this).apply {
    orientation = RadioGroup.HORIZONTAL
    setPadding(0, dp(8), 0, dp(8))
  }
  val mergeChoice = RadioButton(this).apply {
    text = "Merge"
    isChecked = true
  }
  val createChoice = RadioButton(this).apply {
    text = "Create Another"
  }
  choiceGroup.addView(mergeChoice, weightedParams())
  choiceGroup.addView(createChoice, weightedParams())
  content.addView(choiceGroup)

  val sliderLabel = bodyText("Merge Amount: ${(currentT * 100).roundToInt()}%")
  content.addView(sliderLabel)
  val seekBar = SeekBar(this).apply {
    max = 1000
    progress = 500
    setPadding(0, dp(4), 0, dp(8))
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        val normalized = progress / 1000.0
        currentT = bounds.minT + normalized * (bounds.maxT - bounds.minT)
        sliderLabel.text = "Merge Amount: ${(currentT * 100).roundToInt()}%"
        mergedPreview.points = blendNormalizedPaths(mergeTarget.normalizedPath, incoming.normalizedPath, currentT)
      }

      override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
      override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })
  }
  content.addView(seekBar)

  val dialog = fullScreenDialog()
  val root = screenDialogRoot("Gesture Conflict Found")
  root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
  val actions = dialogActions()
  actions.addView(primaryButton("Continue") {
      if (createChoice.isChecked) {
        appendBinding(target, incoming)
        dialog.dismiss()
        return@primaryButton
      }

      val blendedPath = blendNormalizedPaths(mergeTarget.normalizedPath, incoming.normalizedPath, currentT)
      if (blendedPath.size < 2) {
        appendBinding(target, incoming)
      } else {
        savedGestures = savedGestures.map { gesture ->
          if (gesture.label == mergeTarget.label) gesture.copy(normalizedPath = blendedPath) else gesture
        }.toMutableList()
        persistGestures()
        maybeLaunchAfterAssign(target)
        pendingGesture = null
      }
      dialog.dismiss()
  }, actionButtonParams())
  root.addView(actions)

  dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  dialog.setContentView(root)
  trackDialog(dialog)
  dialog.show()
  expandDialog(dialog)
}

internal fun MainActivity.targetLabel(
  gesture: SavedGesture,
  appLabels: Map<String, String> = cachedAppLabels(),
): String {
  gesture.specialActionId?.let { actionId ->
    return SpecialFunctions.find(actionId)?.label ?: gesture.label
  }
  gesture.packageName?.let { packageName ->
    return appLabels[packageName] ?: packageName
  }
  return "No app assigned"
}

internal fun MainActivity.targetSubtitle(gesture: SavedGesture): String {
  gesture.specialActionId?.let { actionId ->
    return SpecialFunctions.find(actionId)?.subtitle ?: "Special function"
  }
  return gesture.packageName ?: "No app assigned"
}

internal fun MainActivity.previewColumn(title: String, points: List<Point>, preview: GesturePreviewView = GesturePreviewView(this)): View {
  preview.points = points
  return LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    addView(android.widget.TextView(this@previewColumn).apply {
      text = title
      setTextColor(secondaryTextColor())
      textSize = 11f
      gravity = Gravity.CENTER
    })
    addView(preview)
  }
}

internal fun MainActivity.persistGestures() {
  gestureStore.saveGestures(savedGestures)
}

internal fun MainActivity.seedDefaultGesturesIfNeeded() {
  migrateHorizontalGestureFormatIfNeeded()
  seedDefaultOpenListGestureIfNeeded()
  seedDefaultGoogleGestureIfNeeded()
}

private fun MainActivity.migrateHorizontalGestureFormatIfNeeded() {
  val horizontalPath = defaultHorizontalLineGesturePath()
  var changed = false

  savedGestures = savedGestures.map { gesture ->
    if (gesture.specialActionId == SPECIAL_ACTION_OPEN_APP_LIST) {
      val looksLikeOldHorizontalDefault = calculateBestDirectionDifference(
        gesture.normalizedPath,
        horizontalPath,
        allowBackward = true,
      ) < ANGULAR_THRESHOLD
      if (looksLikeOldHorizontalDefault) {
        changed = true
        defaultOpenAppListGesture()
      } else {
        gesture
      }
    } else {
      gesture
    }
  }.toMutableList()

  if (changed) persistGestures()
}

private fun MainActivity.seedDefaultOpenListGestureIfNeeded() {
  val hasOpenListGesture = savedGestures.any { it.specialActionId == SPECIAL_ACTION_OPEN_APP_LIST }
  if (!hasOpenListGesture) {
    savedGestures += defaultOpenAppListGesture()
    persistGestures()
  }
}

private fun MainActivity.seedDefaultGoogleGestureIfNeeded() {
  val horizontalPath = defaultHorizontalLineGesturePath()
  val hasHorizontalGoogleGesture = savedGestures.any { gesture ->
    gesture.packageName == GOOGLE_APP_PACKAGE_NAME &&
      calculateBestDirectionDifference(
        gesture.normalizedPath,
        horizontalPath,
        allowBackward = true,
      ) < ANGULAR_THRESHOLD
  }
  if (!hasHorizontalGoogleGesture) {
    savedGestures += defaultOpenGoogleGesture()
    persistGestures()
  }
}
