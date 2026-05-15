package com.thejusticeman.glyphos

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// Edit mode: bottom bar, trash drop target, settings/onboarding dialogs, system intents.

internal enum class TrashDropTargetMode {
  ICON_RESET,
  WIDGET_REMOVE,
}

internal fun MainActivity.buildBottomSettingsBar(): View {
  val bar = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER
    setPadding(dp(8), dp(6), dp(8), dp(6))
    background = GradientDrawable().apply {
      setColor(Color.argb(190, 18, 18, 18))
      cornerRadius = dp(8).toFloat()
      setStroke(1, Color.argb(70, 255, 255, 255))
    }
    elevation = dp(8).toFloat()
    visibility = if (canvasView.editMode) View.VISIBLE else View.GONE
  }

  bar.addView(bottomSettingsButton("Settings", ::showManagementDialog), bottomSettingsButtonParams())
  bar.addView(bottomSettingsButton("Gestures", ::showGestureLibraryDialog), bottomSettingsButtonParams())
  bar.addView(bottomSettingsButton("Add Widget", ::showWidgetPickerDialog), bottomSettingsButtonParams())

  return bar.apply {
    layoutParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
    ).apply {
      leftMargin = dp(12)
      rightMargin = dp(12)
      bottomMargin = dp(12)
    }
  }
}

internal fun MainActivity.bottomSettingsButton(text: String, onClick: () -> Unit): Button {
  return Button(this).apply {
    this.text = text
    textSize = 13f
    minHeight = dp(44)
    minimumHeight = dp(44)
    setPadding(dp(6), 0, dp(6), 0)
    setOnClickListener { onClick() }
  }
}

internal fun MainActivity.bottomSettingsButtonParams(): LinearLayout.LayoutParams {
  return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
    leftMargin = dp(4)
    rightMargin = dp(4)
  }
}

internal fun MainActivity.buildTrashDropTarget(): TextView {
  return TextView(this).apply {
    text = trashDropTargetText()
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    textSize = 13f
    typeface = Typeface.DEFAULT_BOLD
    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_delete_24, 0, 0)
    compoundDrawablePadding = dp(4)
    setPadding(dp(12), dp(8), dp(12), dp(8))
    background = trashDropTargetBackground(highlighted = false)
    alpha = 0.95f
    visibility = View.INVISIBLE
    elevation = dp(12).toFloat()
    layoutParams = FrameLayout.LayoutParams(dp(144), dp(76), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
      bottomMargin = dp(88)
    }
  }
}

internal fun MainActivity.setTrashDropTargetVisible(visible: Boolean, mode: TrashDropTargetMode) {
  trashDropTargetMode = mode
  if (!isTrashDropTargetViewInitialized) return
  trashDropTargetView.text = trashDropTargetText()
  trashDropTargetView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
  if (!visible) updateTrashDropTargetHighlight(false)
}

internal fun MainActivity.trashDropTargetText(): String {
  return when (trashDropTargetMode) {
    TrashDropTargetMode.ICON_RESET -> "Reset count\nDrop here"
    TrashDropTargetMode.WIDGET_REMOVE -> "Remove widget\nDrop here"
  }
}

internal fun MainActivity.updateTrashDropTargetHighlight(highlighted: Boolean) {
  if (!isTrashDropTargetViewInitialized || trashDropTargetView.visibility != View.VISIBLE) return
  trashDropTargetView.background = trashDropTargetBackground(highlighted)
  trashDropTargetView.alpha = if (highlighted) 1.0f else 0.95f
}

internal fun MainActivity.trashDropTargetBackground(highlighted: Boolean): GradientDrawable {
  return GradientDrawable().apply {
    setColor(if (highlighted) Color.rgb(186, 26, 26) else Color.argb(220, 42, 42, 42))
    cornerRadius = dp(8).toFloat()
    setStroke(dp(if (highlighted) 2 else 1), Color.argb(if (highlighted) 240 else 120, 255, 255, 255))
  }
}

internal fun MainActivity.isTrashDropTargetHit(x: Float, y: Float): Boolean {
  if (!isTrashDropTargetViewInitialized || trashDropTargetView.visibility != View.VISIBLE) return false
  if (trashDropTargetView.width <= 0 || trashDropTargetView.height <= 0) return false
  return x >= trashDropTargetView.left &&
    x <= trashDropTargetView.right &&
    y >= trashDropTargetView.top &&
    y <= trashDropTargetView.bottom
}

internal fun MainActivity.isTrashDropTargetHit(placement: WidgetPlacement): Boolean {
  if (!isTrashDropTargetViewInitialized || trashDropTargetView.visibility != View.VISIBLE) return false
  if (trashDropTargetView.width <= 0 || trashDropTargetView.height <= 0) return false
  val widgetLeft = placement.x.toFloat()
  val widgetTop = placement.y.toFloat()
  val widgetRight = widgetLeft + placement.width
  val widgetBottom = widgetTop + placement.height
  return widgetRight >= trashDropTargetView.left &&
    widgetLeft <= trashDropTargetView.right &&
    widgetBottom >= trashDropTargetView.top &&
    widgetTop <= trashDropTargetView.bottom
}

internal fun MainActivity.showManagementDialog() {
  val dialog = fullScreenDialog()

  val root = screenDialogRoot("Settings")
  val scroll = ScrollView(this)
  val list = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(0, dp(4), 0, dp(12))
  }

  list.addView(settingsSwitchRow("Trail effect", "Show a fading trail while drawing", settings.trailEffect) { checked ->
    settings.trailEffect = checked
    canvasView.trailEffect = checked
  })
  list.addView(settingDivider())
  list.addView(settingsSwitchRow("Open app after create", "Launch a target immediately after assigning it", settings.launchOnCreateShortcut) { checked ->
    settings.launchOnCreateShortcut = checked
  })
  list.addView(settingDivider())
  list.addView(settingsSwitchRow("Match backwards gestures", "Allow the same gesture in reverse", settings.allowBackwardGestures) { checked ->
    settings.allowBackwardGestures = checked
  })
  list.addView(settingDivider())
  list.addView(settingsSwitchRow("Auto-pick only search result", "Choose the app automatically when search leaves one match", settings.autoPickOnlySearchResult) { checked ->
    settings.autoPickOnlySearchResult = checked
  })
  list.addView(settingDivider())
  list.addView(settingsActionRow("Gestures", "Reassign or delete saved gesture bindings") {
    dialog.dismiss()
    showGestureLibraryDialog()
  })
  list.addView(settingDivider())
  list.addView(settingsActionRow("Add widget", "Place an Android widget on your home surface") {
    dialog.dismiss()
    showWidgetPickerDialog()
  })
  list.addView(settingDivider())
  list.addView(settingsActionRow("Remove widget", "Delete a widget from your home surface") {
    dialog.dismiss()
    showRemoveWidgetDialog()
  })
  list.addView(settingDivider())
  list.addView(settingsActionRow("Wallpaper", "Open Android wallpaper and style", onClick = ::openWallpaperChooser))
  list.addView(settingDivider())
  list.addView(settingsActionRow("Home app", "Choose the default launcher", onClick = ::openHomeAppSettings))
  list.addView(settingDivider())
  list.addView(settingsActionRow("Reset layout", "Put frequent apps near the center of the spiral") {
    dialog.dismiss()
    beginHomeIconLayoutReset()
  })

  scroll.addView(list)
  root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

  dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
  dialog.setContentView(root)
  trackDialog(dialog)
  dialog.show()
  expandDialog(dialog)
}

internal fun MainActivity.showFeedback(message: String) {
  Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.showOnboardingDialog() {
  val dialog = android.app.Dialog(this)
  val root = compactDialogRoot("GlyphOS")
  root.addView(bodyText("Swipe up to open the app list. Draw a sideways line to open Google. Draw any other gesture to assign or launch an app. Long press to edit the home screen; the settings buttons appear in edit mode."))
  root.addView(primaryButton("Start") {
    settings.onboardingDone = true
    dialog.dismiss()
  })
  dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
  dialog.setContentView(root)
  trackDialog(dialog)
  dialog.show()
  fitCompactDialog(dialog)
}

internal fun MainActivity.openWallpaperChooser() {
  try {
    startActivity(Intent("android.intent.action.SET_WALLPAPER"))
  } catch (_: Exception) {
    showFeedback("Could not open wallpaper chooser")
  }
}

internal fun MainActivity.openHomeAppSettings() {
  try {
    startActivity(Intent("android.settings.HOME_SETTINGS"))
  } catch (_: Exception) {
    showFeedback("Could not open home app settings")
  }
}
