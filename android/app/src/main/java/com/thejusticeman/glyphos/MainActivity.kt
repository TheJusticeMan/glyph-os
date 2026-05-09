package com.thejusticeman.glyphos

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

private const val FEEDBACK_DURATION_MS = 2000L

class MainActivity : Activity() {
  private lateinit var settings: AppSettings
  private lateinit var gestureStore: GestureStore
  private lateinit var installedApps: InstalledApps
  private lateinit var canvasView: GestureCanvasView
  private lateinit var feedbackView: TextView

  private var savedGestures: MutableList<SavedGesture> = mutableListOf()
  private var pendingGesture: PendingGesture? = null
  private var feedbackHideRunnable: Runnable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.AppTheme)
    super.onCreate(savedInstanceState)

    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT

    settings = AppSettings(this)
    gestureStore = GestureStore(this)
    installedApps = InstalledApps(this)
    savedGestures = gestureStore.loadGestures().toMutableList()

    setContentView(buildRootView())

    if (!settings.onboardingDone) {
      showOnboardingDialog()
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    // A launcher should not exit when Back is pressed from the root screen.
  }

  private fun buildRootView(): View {
    val root = FrameLayout(this).apply {
      setBackgroundColor(Color.TRANSPARENT)
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    }

    canvasView = GestureCanvasView(this).apply {
      trailEffect = settings.trailEffect
      onGestureComplete = ::handleGestureComplete
      onLongPressOpenManagement = ::showManagementDialog
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    }

    feedbackView = TextView(this).apply {
      visibility = View.GONE
      alpha = 0f
      setTextColor(Color.WHITE)
      textSize = 14f
      gravity = Gravity.CENTER
      setPadding(dp(24), dp(12), dp(24), dp(12))
      background = roundedBackground(Color.rgb(0, 51, 51), Color.rgb(0, 255, 204), dp(32).toFloat())
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
      ).apply {
        bottomMargin = dp(60)
        leftMargin = dp(20)
        rightMargin = dp(20)
      }
    }

    root.addView(canvasView)
    root.addView(feedbackView)
    return root
  }

  private fun handleGestureComplete(normalizedPoints: List<Point>) {
    val match = matchGesture(
      normalizedPoints,
      savedGestures,
      allowBackward = settings.allowBackwardGestures,
    )

    if (match != null) {
      val packageName = match.gesture.packageName
      if (packageName != null) {
        val launched = installedApps.launchApp(packageName)
        showFeedback(if (launched) "Launching ${match.gesture.label}" else "Launch failed")
      } else {
        requestAssignApp(match.gesture.label, match.gesture.normalizedPath)
        showFeedback("Assign an app to this gesture")
      }
      return
    }

    requestAssignApp("gesture_${System.currentTimeMillis()}", normalizedPoints)
    showFeedback("New gesture! Assign an app.")
  }

  private fun requestAssignApp(label: String, normalizedPath: List<Point>) {
    pendingGesture = PendingGesture(label, normalizedPath)
    val prioritizedPackageNames = rankSimilarApps(
      normalizedPath,
      savedGestures,
      limit = 5,
      allowBackward = settings.allowBackwardGestures,
    ).map { it.packageName }

    showAppPickerDialog(
      title = "Assign App to Gesture",
      prioritizedPackageNames = prioritizedPackageNames,
    ) { selectedApp ->
      handleAssignApp(selectedApp)
    }
  }

  private fun handleAssignApp(app: AppDetail) {
    val incoming = pendingGesture ?: return
    val existingForApp = savedGestures.filter { gesture ->
      gesture.packageName == app.packageName && gesture.normalizedPath.size >= 2
    }

    if (existingForApp.isEmpty()) {
      appendBinding(app, incoming)
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
      appendBinding(app, incoming)
      return
    }

    val bounds = findBlendBoundsForDualMatch(mergeTarget.normalizedPath, incoming.normalizedPath)
    if (!bounds.canMerge) {
      appendBinding(app, incoming)
      return
    }

    showMergeDialog(app, mergeTarget, incoming, bounds)
  }

  private fun appendBinding(app: AppDetail, gesture: PendingGesture) {
    savedGestures += SavedGesture(
      label = gesture.label,
      packageName = app.packageName,
      normalizedPath = gesture.normalizedPath,
    )
    persistGestures()
    showFeedback("App assigned!")
    maybeLaunchAfterAssign(app)
    pendingGesture = null
  }

  private fun maybeLaunchAfterAssign(app: AppDetail) {
    if (!settings.launchOnCreateShortcut) return
    val launched = installedApps.launchApp(app.packageName)
    if (!launched) showFeedback("Assigned, but launch failed")
  }

  private fun showMergeDialog(
    app: AppDetail,
    mergeTarget: SavedGesture,
    incoming: PendingGesture,
    bounds: BlendBoundsResult,
  ) {
    val dialog = Dialog(this)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    var currentT = (bounds.minT + bounds.maxT) / 2

    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(18), dp(18), dp(18), dp(18))
      setBackgroundColor(Color.rgb(19, 19, 19))
    }

    content.addView(titleText("Gesture Conflict Found", 20f))
    content.addView(bodyText("${app.label} already has a gesture. Merge the old and new swipe, or keep both bindings."))

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
      setTextColor(Color.WHITE)
      isChecked = true
    }
    val createChoice = RadioButton(this).apply {
      text = "Create Another"
      setTextColor(Color.WHITE)
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

    val actions = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.END
    }
    actions.addView(secondaryButton("Cancel") { dialog.dismiss() }, weightedParams())
    actions.addView(primaryButton("Continue") {
      if (createChoice.isChecked) {
        appendBinding(app, incoming)
        dialog.dismiss()
        return@primaryButton
      }

      val blendedPath = blendNormalizedPaths(mergeTarget.normalizedPath, incoming.normalizedPath, currentT)
      if (blendedPath.size < 2) {
        appendBinding(app, incoming)
      } else {
        savedGestures = savedGestures.map { gesture ->
          if (gesture.label == mergeTarget.label) gesture.copy(normalizedPath = blendedPath) else gesture
        }.toMutableList()
        persistGestures()
        showFeedback("Gestures merged!")
        maybeLaunchAfterAssign(app)
        pendingGesture = null
      }
      dialog.dismiss()
    }, weightedParams())
    content.addView(actions)

    dialog.setContentView(content)
    dialog.show()
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
  }

  private fun showAppPickerDialog(
    title: String,
    prioritizedPackageNames: List<String> = emptyList(),
    onSelect: (AppDetail) -> Unit,
  ) {
    val dialog = Dialog(this)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val apps = installedApps.getInstalledApps()
    val priorityOrder = prioritizedPackageNames.withIndex().associate { it.value to it.index }
    val adapter = AppListAdapter(this)

    fun applyFilter(query: String) {
      val base = installedApps.filterApps(apps, query)
      adapter.apps = base.sortedWith { left, right ->
        val leftRank = priorityOrder[left.packageName] ?: Int.MAX_VALUE
        val rightRank = priorityOrder[right.packageName] ?: Int.MAX_VALUE
        when {
          leftRank != rightRank -> leftRank - rightRank
          else -> 0
        }
      }
    }

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(16), dp(44), dp(16), dp(18))
      setBackgroundColor(Color.rgb(17, 17, 17))
    }
    root.addView(titleText(title, 20f).apply { gravity = Gravity.CENTER })

    val search = EditText(this).apply {
      hint = "Search apps"
      setHintTextColor(Color.rgb(85, 85, 85))
      setTextColor(Color.WHITE)
      textSize = 15f
      setSingleLine(true)
      setPadding(dp(12), dp(10), dp(12), dp(10))
      background = roundedBackground(Color.rgb(26, 26, 26), Color.rgb(0, 255, 204), dp(8).toFloat())
    }
    root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
      topMargin = dp(16)
      bottomMargin = dp(8)
    })

    val hint = bodyText("Top 5 similar apps are pinned first").apply {
      visibility = if (prioritizedPackageNames.isEmpty()) View.GONE else View.VISIBLE
      setTextColor(Color.rgb(111, 111, 111))
      textSize = 12f
    }
    root.addView(hint)

    val listView = ListView(this).apply {
      divider = ColorDrawable(Color.rgb(34, 34, 34))
      dividerHeight = 1
      setBackgroundColor(Color.TRANSPARENT)
      this.adapter = adapter
      setOnItemClickListener { _, _, position, _ ->
        val selectedApp = adapter.getItem(position)
        dialog.dismiss()
        onSelect(selectedApp)
      }
    }
    root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    root.addView(secondaryButton("Cancel") { dialog.dismiss() })

    search.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        applyFilter(s?.toString().orEmpty())
      }
      override fun afterTextChanged(s: Editable?) = Unit
    })

    applyFilter("")
    dialog.setContentView(root)
    dialog.show()
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    search.requestFocus()
  }

  private fun showManagementDialog() {
    val dialog = Dialog(this)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val appLabels = installedApps.getInstalledApps().associate { it.packageName to it.label }
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(16), dp(44), dp(16), dp(16))
      setBackgroundColor(Color.BLACK)
    }

    val header = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    header.addView(titleText("Gesture Library", 22f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    header.addView(secondaryButton("Close") { dialog.dismiss() })
    root.addView(header)

    root.addView(settingsRow("Trail effect", settings.trailEffect) { checked ->
      settings.trailEffect = checked
      canvasView.trailEffect = checked
    })
    root.addView(settingsRow("Open app after create", settings.launchOnCreateShortcut) { checked ->
      settings.launchOnCreateShortcut = checked
    })
    root.addView(settingsRow("Match backwards gestures", settings.allowBackwardGestures) { checked ->
      settings.allowBackwardGestures = checked
    })

    val utilityRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(0, dp(8), 0, dp(8))
    }
    utilityRow.addView(secondaryButton("Wallpaper") { openWallpaperChooser() }, weightedParams())
    utilityRow.addView(secondaryButton("Home App") { openHomeAppSettings() }, weightedParams())
    root.addView(utilityRow)

    root.addView(primaryButton("Clear All") {
      AlertDialog.Builder(this)
        .setTitle("Clear All Gestures")
        .setMessage("This will permanently delete every saved gesture. Continue?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Clear All") { _, _ ->
          savedGestures.clear()
          gestureStore.clearGestures()
          dialog.dismiss()
          showManagementDialog()
          showFeedback("Gestures cleared")
        }
        .show()
    })

    val scroll = ScrollView(this)
    val list = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(0, dp(12), 0, dp(12))
    }

    if (savedGestures.isEmpty()) {
      list.addView(bodyText("No gestures saved yet.").apply { gravity = Gravity.CENTER })
    } else {
      savedGestures.forEach { gesture ->
        list.addView(gestureRow(gesture, appLabels[gesture.packageName] ?: gesture.packageName ?: "No app assigned", dialog))
      }
    }
    scroll.addView(list)
    root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

    dialog.setContentView(root)
    dialog.show()
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
  }

  private fun gestureRow(gesture: SavedGesture, appLabel: String, ownerDialog: Dialog): View {
    val row = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(0, dp(10), 0, dp(10))
    }

    row.addView(GesturePreviewView(this).apply { points = gesture.normalizedPath })

    val labels = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(12), 0, dp(8), 0)
    }
    labels.addView(TextView(this).apply {
      text = appLabel
      setTextColor(Color.WHITE)
      textSize = 15f
      maxLines = 1
    })
    labels.addView(TextView(this).apply {
      text = gesture.packageName ?: "No app assigned"
      setTextColor(Color.rgb(136, 136, 136))
      textSize = 12f
      maxLines = 1
    })
    row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

    row.addView(secondaryButton("Reassign") {
      showAppPickerDialog("Reassign Gesture") { app ->
        savedGestures = savedGestures.map { current ->
          if (current.label == gesture.label) current.copy(packageName = app.packageName) else current
        }.toMutableList()
        persistGestures()
        ownerDialog.dismiss()
        showManagementDialog()
        showFeedback("Gesture reassigned")
      }
    })
    row.addView(secondaryButton("Delete") {
      AlertDialog.Builder(this)
        .setTitle("Delete Gesture")
        .setMessage("Delete ${gesture.label}?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Delete") { _, _ ->
          savedGestures.removeAll { it.label == gesture.label }
          persistGestures()
          ownerDialog.dismiss()
          showManagementDialog()
          showFeedback("Gesture deleted")
        }
        .show()
    })
    return row
  }

  private fun showOnboardingDialog() {
    AlertDialog.Builder(this)
      .setTitle("GlyphOS")
      .setMessage("Draw a gesture anywhere to assign or launch an app. Long press the screen to manage gestures.")
      .setPositiveButton("Start") { _, _ -> settings.onboardingDone = true }
      .show()
  }

  private fun showFeedback(message: String) {
    feedbackHideRunnable?.let { feedbackView.removeCallbacks(it) }
    feedbackView.text = message
    feedbackView.visibility = View.VISIBLE
    feedbackView.animate().cancel()
    feedbackView.alpha = 0f
    feedbackView.animate().alpha(1f).setDuration(200).start()

    val hideRunnable = Runnable {
      feedbackView.visibility = View.GONE
      feedbackHideRunnable = null
    }
    feedbackHideRunnable = hideRunnable
    feedbackView.postDelayed(hideRunnable, FEEDBACK_DURATION_MS)
  }

  private fun persistGestures() {
    gestureStore.saveGestures(savedGestures)
  }

  private fun openWallpaperChooser() {
    try {
      startActivity(Intent("android.intent.action.SET_WALLPAPER"))
    } catch (_: Exception) {
      showFeedback("Could not open wallpaper chooser")
    }
  }

  private fun openHomeAppSettings() {
    try {
      startActivity(Intent("android.settings.HOME_SETTINGS"))
    } catch (_: Exception) {
      showFeedback("Could not open home app settings")
    }
  }

  private fun previewColumn(title: String, points: List<Point>, preview: GesturePreviewView = GesturePreviewView(this)): View {
    preview.points = points
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      addView(TextView(this@MainActivity).apply {
        text = title
        setTextColor(Color.rgb(154, 154, 154))
        textSize = 11f
        gravity = Gravity.CENTER
      })
      addView(preview)
    }
  }

  private fun settingsRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
    return LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(0, dp(8), 0, dp(8))
      addView(TextView(this@MainActivity).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 15f
      }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
      addView(Switch(this@MainActivity).apply {
        isChecked = checked
        setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean -> onChange(isChecked) }
      })
    }
  }

  private fun titleText(text: String, size: Float): TextView {
    return TextView(this).apply {
      this.text = text
      setTextColor(Color.rgb(0, 255, 204))
      textSize = size
      gravity = Gravity.START
      setPadding(0, dp(4), 0, dp(4))
    }
  }

  private fun bodyText(text: String): TextView {
    return TextView(this).apply {
      this.text = text
      setTextColor(Color.rgb(204, 204, 204))
      textSize = 14f
      setPadding(0, dp(4), 0, dp(4))
    }
  }

  private fun primaryButton(text: String, onClick: () -> Unit): Button {
    return Button(this).apply {
      this.text = text
      setTextColor(Color.BLACK)
      setBackgroundColor(Color.rgb(0, 255, 204))
      setOnClickListener { onClick() }
    }
  }

  private fun secondaryButton(text: String, onClick: () -> Unit): Button {
    return Button(this).apply {
      this.text = text
      setTextColor(Color.rgb(0, 255, 204))
      setBackgroundColor(Color.rgb(42, 42, 42))
      setOnClickListener { onClick() }
    }
  }

  private fun roundedBackground(fillColor: Int, strokeColor: Int, radius: Float): android.graphics.drawable.Drawable {
    return android.graphics.drawable.GradientDrawable().apply {
      shape = android.graphics.drawable.GradientDrawable.RECTANGLE
      cornerRadius = radius
      setColor(fillColor)
      setStroke(dp(1), strokeColor)
    }
  }

  private fun weightedParams(): LinearLayout.LayoutParams {
    return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
      leftMargin = dp(4)
      rightMargin = dp(4)
    }
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  private data class PendingGesture(
    val label: String,
    val normalizedPath: List<Point>,
  )
}
