package com.thejusticeman.glyphos

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private const val HOME_ICON_MIN_DP = 44
private const val HOME_ICON_MAX_DP = 112
private const val HOME_ICON_AREA_BUDGET = 0.42
private const val HOME_ICON_FOOTPRINT_FACTOR = 1.55
private const val HOME_ICON_MIN_VISIBLE = 24
private const val HOME_ICON_MAX_VISIBLE = 64
private const val HOME_ICON_RESET_SETTLE_PX = 1.5
private const val HOME_ICON_GHOST_RADIUS_DP = 64

class MainActivity : Activity() {
  private lateinit var settings: AppSettings
  private lateinit var gestureStore: GestureStore
  private lateinit var installedApps: InstalledApps
  private lateinit var launchUsageStore: LaunchUsageStore
  private lateinit var canvasView: GestureCanvasView
  private val mainHandler = Handler(Looper.getMainLooper())
  private val appRefreshExecutor = Executors.newSingleThreadExecutor()

  private var savedGestures: MutableList<SavedGesture> = mutableListOf()
  private var pendingGesture: PendingGesture? = null
  private var launchCounts: Map<String, Int> = emptyMap()
  private var homeIconAnchors: Map<String, Point> = emptyMap()
  private var draggingHomeIconPositions: Map<String, Point> = emptyMap()
  private var homeIconResetState: HomeIconResetState? = null
  private var gestureGhostPosition: Point? = null
  private val activeDialogs = mutableSetOf<Dialog>()

  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.AppTheme)
    super.onCreate(savedInstanceState)

    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT

    settings = AppSettings(this)
    gestureStore = GestureStore(this)
    installedApps = InstalledApps(this)
    launchUsageStore = LaunchUsageStore(this)
    launchCounts = launchUsageStore.getLaunchCounts()
    savedGestures = gestureStore.loadGestures().toMutableList()
    seedDefaultOpenListGestureIfNeeded()

    setContentView(buildRootView())
    updateLauncherIcons()
    refreshInstalledAppCache { updateLauncherIcons(it) }

    if (!settings.onboardingDone) {
      showOnboardingDialog()
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    // A launcher should not exit when Back is pressed from the root screen.
  }

  override fun onPause() {
    dismissActiveDialogs()
    super.onPause()
  }

  override fun onDestroy() {
    mainHandler.removeCallbacksAndMessages(null)
    appRefreshExecutor.shutdownNow()
    super.onDestroy()
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
      iconScale = settings.homeIconScale
      onGestureComplete = ::handleGestureComplete
      onLongPressOpenManagement = ::showManagementDialog
      onIconTapped = ::handleLauncherIconTapped
      onCanvasSizeChanged = { updateLauncherIcons() }
      onIconScaleChanged = { scale -> settings.homeIconScale = scale }
      onIconPositionChanging = ::handleHomeIconPositionChanging
      onIconPositionCommitted = ::handleHomeIconPositionCommitted
      onEditModeChanged = { editing -> showFeedback(if (editing) "Edit mode" else "Edit mode off") }
      onLauncherIconLayoutSettled = { mainHandler.post { handleHomeIconLayoutSettled() } }
      onGestureGhostChanged = ::handleGestureGhostChanged
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    }

    root.addView(canvasView)
    return root
  }

  private fun handleGestureComplete(normalizedPoints: List<Point>) {
    val match = matchGesture(
      normalizedPoints,
      savedGestures,
      allowBackward = settings.allowBackwardGestures,
    )

    if (match != null) {
      executeGesture(match.gesture)
      return
    }

    requestAssignApp("gesture_${System.currentTimeMillis()}", normalizedPoints)
    showFeedback("New gesture! Assign an app.")
  }

  private fun requestAssignApp(label: String, normalizedPath: List<Point>) {
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

  private fun handleAssignApp(target: AppDetail) {
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

  private fun appendBinding(target: AppDetail, gesture: PendingGesture) {
    savedGestures += SavedGesture(
      label = gesture.label,
      packageName = target.packageName.takeUnless { target.isSpecialFunction() },
      specialActionId = target.specialActionId,
      normalizedPath = gesture.normalizedPath,
    )
    persistGestures()
    showFeedback("App assigned!")
    maybeLaunchAfterAssign(target)
    pendingGesture = null
  }

  private fun maybeLaunchAfterAssign(target: AppDetail) {
    if (!settings.launchOnCreateShortcut) return
    if (target.isSpecialFunction()) {
      executeSpecialFunction(target.specialActionId)
    } else {
      val launched = launchAppPackage(target.packageName)
      if (!launched) showFeedback("Assigned, but launch failed")
    }
  }

  private fun executeGesture(gesture: SavedGesture) {
    when {
      gesture.specialActionId != null -> executeSpecialFunction(gesture.specialActionId)
      gesture.packageName != null -> {
        val launched = launchAppPackage(gesture.packageName)
        if (!launched) showFeedback("Launch failed")
      }
      else -> {
        requestAssignApp(gesture.label, gesture.normalizedPath)
        showFeedback("Assign an app to this gesture")
      }
    }
  }

  private fun executeSpecialFunction(actionId: String?) {
    when (actionId) {
      SPECIAL_ACTION_OPEN_APP_LIST -> showLaunchListDialog()
      else -> showFeedback("Special function unavailable")
    }
  }

  private fun showLaunchListDialog() {
    showAppPickerDialog(
      title = "Open App",
      includeSpecialFunctions = false,
    ) { app ->
      val launched = launchAppPackage(app.packageName)
      showFeedback(if (launched) "Launching ${app.label}" else "Launch failed")
    }
  }

  private fun handleLauncherIconTapped(app: AppDetail) {
    val launched = launchAppPackage(app.packageName)
    if (!launched) showFeedback("Launch failed")
  }

  private fun showMergeDialog(
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
          showFeedback("Gestures merged!")
          maybeLaunchAfterAssign(target)
          pendingGesture = null
        }
        dialog.dismiss()
    }, actionButtonParams())
    root.addView(actions)

    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setContentView(root)
    trackDialog(dialog)
    dialog.show()
    expandDialog(dialog)
  }

  private fun showAppPickerDialog(
    title: String,
    prioritizedTargetKeys: List<String> = emptyList(),
    includeSpecialFunctions: Boolean = false,
    onSelect: (AppDetail) -> Unit,
  ) {
    var apps = buildTargetList(includeSpecialFunctions, cachedInstalledApps())
    val priorityOrder = prioritizedTargetKeys.withIndex().associate { it.value to it.index }
    val adapter = AppListAdapter(this)
    lateinit var dialog: Dialog
    val root = screenDialogRoot(title)
    lateinit var search: EditText
    var selectionHandled = false

    fun selectApp(app: AppDetail) {
      if (selectionHandled) return
      selectionHandled = true
      hideKeyboard(search)
      dialog.dismiss()
      onSelect(app)
    }

    fun selectTopApp(): Boolean {
      if (adapter.count == 0) return false
      selectApp(adapter.getItem(0))
      return true
    }

    fun applyFilter(query: String) {
      val result = installedApps.filterApps(apps, query)
      adapter.showPackageNames = result.isPackageSearch
      adapter.apps = result.apps.sortedWith { left, right ->
        val leftRank = priorityOrder[left.targetKey()] ?: Int.MAX_VALUE
        val rightRank = priorityOrder[right.targetKey()] ?: Int.MAX_VALUE
        when {
          leftRank != rightRank -> leftRank - rightRank
          left.isSpecialFunction() != right.isSpecialFunction() -> if (left.isSpecialFunction()) -1 else 1
          else -> 0
        }
      }

      if (settings.autoPickOnlySearchResult && query.trim().isNotEmpty() && adapter.count == 1) {
        val stableQuery = query
        root.post {
          if (!selectionHandled && search.text?.toString() == stableQuery && adapter.count == 1) {
            selectTopApp()
          }
        }
      }
    }

    search = EditText(this).apply {
      hint = "Search apps"
      textSize = 15f
      setSingleLine(true)
      imeOptions = EditorInfo.IME_ACTION_GO
      setOnEditorActionListener { _, actionId, event ->
        val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
        if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || isEnterKey) {
          selectTopApp()
        } else {
          false
        }
      }
    }
    root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
      bottomMargin = dp(8)
    })

    val hint = bodyText("Top 5 similar apps are pinned first").apply {
      visibility = if (prioritizedTargetKeys.isEmpty()) View.GONE else View.VISIBLE
      textSize = 12f
    }
    root.addView(hint)

    val listView = ListView(this).apply {
      this.adapter = adapter
      setOnItemClickListener { _, _, position, _ ->
        selectApp(adapter.getItem(position))
      }
      setOnScrollListener(object : AbsListView.OnScrollListener {
        private var lastFirstVisibleItem = 0

        override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
          if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
            hideKeyboard(search)
          }
        }

        override fun onScroll(
          view: AbsListView?,
          firstVisibleItem: Int,
          visibleItemCount: Int,
          totalItemCount: Int,
        ) {
          if (firstVisibleItem > lastFirstVisibleItem) {
            hideKeyboard(search)
          }
          lastFirstVisibleItem = firstVisibleItem
        }
      })
    }
    root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

    search.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        applyFilter(s?.toString().orEmpty())
      }
      override fun afterTextChanged(s: Editable?) = Unit
    })

    applyFilter("")

    dialog = fullScreenDialog()
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.window?.setSoftInputMode(
      WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
    )
    dialog.setContentView(root)
    dialog.setOnShowListener {
      search.requestFocus()
      showKeyboard(search)
    }
    trackDialog(dialog) { hideKeyboard(search) }
    dialog.show()
    expandDialog(dialog, showKeyboard = true)

    refreshInstalledAppCache { refreshedApps ->
      if (!dialog.isShowing || selectionHandled) return@refreshInstalledAppCache
      apps = buildTargetList(includeSpecialFunctions, refreshedApps)
      applyFilter(search.text?.toString().orEmpty())
    }
  }

  private fun showManagementDialog() {
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

  private fun showGestureLibraryDialog() {
    val dialog = fullScreenDialog()

    val appLabels = cachedAppLabels()
    val root = screenDialogRoot("Gesture Library")

    val scroll = ScrollView(this)
    val list = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(0, dp(4), 0, dp(12))
    }


    if (savedGestures.isEmpty()) {
      list.addView(bodyText("No gestures saved yet.").apply { gravity = Gravity.CENTER })
    } else {
      savedGestures.forEach { gesture ->
        list.addView(gestureRow(gesture, targetLabel(gesture, appLabels), targetSubtitle(gesture), dialog))
     list.addView(settingDivider())
     }
    }
    list.addView(settingsActionRow("Clear all gestures", "Delete every saved gesture", destructive = true) {
      showConfirmDialog(
        title = "Clear All Gestures",
        message = "This will permanently delete every saved gesture. Continue?",
        confirmText = "Clear All",
      ) {
        savedGestures.clear()
        gestureStore.clearGestures()
        dialog.dismiss()
        showGestureLibraryDialog()
        showFeedback("Gestures cleared")
      }
    })
    scroll.addView(list)
    root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setContentView(root)
    trackDialog(dialog)
    dialog.show()
    expandDialog(dialog)
  }

  private fun gestureRow(gesture: SavedGesture, appLabel: String, targetSubtitle: String, ownerDialog: Dialog): View {
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
      setTextColor(primaryTextColor())
      textSize = 15f
      maxLines = 1
    })
    labels.addView(TextView(this).apply {
      text = targetSubtitle
      setTextColor(secondaryTextColor())
      textSize = 12f
      maxLines = 1
    })
    row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

    row.addView(secondaryButton("Reassign") {
      showAppPickerDialog(
        title = "Reassign Gesture",
        includeSpecialFunctions = true,
      ) { app ->
        savedGestures = savedGestures.map { current ->
          if (current.label == gesture.label) {
            current.copy(
              packageName = app.packageName.takeUnless { app.isSpecialFunction() },
              specialActionId = app.specialActionId,
            )
          } else {
            current
          }
        }.toMutableList()
        persistGestures()
        ownerDialog.dismiss()
        showGestureLibraryDialog()
        showFeedback("Gesture reassigned")
      }
    })
    row.addView(secondaryButton("Delete") {
      showConfirmDialog(
        title = "Delete Gesture",
        message = "Delete ${gesture.label}?",
        confirmText = "Delete",
      ) {
        savedGestures.removeAll { it.label == gesture.label }
        persistGestures()
        ownerDialog.dismiss()
        showGestureLibraryDialog()
        showFeedback("Gesture deleted")
      }
    })
    return row
  }

  private fun showOnboardingDialog() {
    val dialog = Dialog(this)
    val root = compactDialogRoot("GlyphOS")
    root.addView(bodyText("Draw a straight line to open the app list. Draw any other gesture to assign or launch an app. Long press the screen to manage gestures."))
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

  private fun showFeedback(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

  private fun seedDefaultOpenListGestureIfNeeded() {
    val alreadyExists = savedGestures.any { it.specialActionId == SPECIAL_ACTION_OPEN_APP_LIST }
    if (!alreadyExists) {
      savedGestures += defaultOpenAppListGesture()
      persistGestures()
    }
  }

  private fun buildTargetList(includeSpecialFunctions: Boolean, apps: List<AppDetail>): List<AppDetail> {
    val specialTargets = if (includeSpecialFunctions) {
      SpecialFunctions.all.map(SpecialFunctions::asTarget)
    } else {
      emptyList()
    }
    return specialTargets + apps
  }

  private fun cachedInstalledApps(): List<AppDetail> = installedApps.getCachedInstalledApps().orEmpty()

  private fun cachedAppLabels(): Map<String, String> {
    return cachedInstalledApps().associate { it.packageName to it.label }
  }

  private fun refreshInstalledAppCache(onLoaded: ((List<AppDetail>) -> Unit)? = null) {
    appRefreshExecutor.execute {
      val refreshedApps = installedApps.getInstalledApps(forceRefresh = true)
      mainHandler.post {
        if (!isFinishing && !isDestroyed) {
          onLoaded?.invoke(refreshedApps)
        }
      }
    }
  }

  private fun launchAppPackage(packageName: String): Boolean {
    val launched = installedApps.launchApp(packageName)
    if (launched) {
      val nextCount = launchUsageStore.increment(packageName)
      launchCounts = launchCounts + (packageName to nextCount)
      updateLauncherIcons()
    }
    return launched
  }

  private fun updateLauncherIcons(apps: List<AppDetail> = cachedInstalledApps()) {
    if (!::canvasView.isInitialized) return
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

  private fun selectHomeApps(apps: List<AppDetail>, minIconSize: Double, maxIconSize: Double): List<AppDetail> {
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

  private fun handleHomeIconPositionChanging(
    app: AppDetail,
    x: Float,
    y: Float,
    width: Int,
    height: Int,
  ) {
    homeIconResetState = null
    val anchor = Point(x.toDouble(), y.toDouble())
    homeIconAnchors = homeIconAnchors + (app.packageName to anchor)
    draggingHomeIconPositions = mapOf(app.packageName to anchor)
    updateLauncherIcons()
  }

  private fun handleHomeIconPositionCommitted(
    app: AppDetail,
    x: Float,
    y: Float,
    width: Int,
    height: Int,
  ) {
    homeIconResetState = null
    homeIconAnchors = homeIconAnchors + (app.packageName to Point(x.toDouble(), y.toDouble()))
    draggingHomeIconPositions = emptyMap()
    updateLauncherIcons()
  }

  private fun fixedHomeIconPositions(): Map<String, Point> {
    if (!::canvasView.isInitialized || canvasView.width <= 0 || canvasView.height <= 0) return emptyMap()
    return homeIconResetState?.fixedPositions.orEmpty() + draggingHomeIconPositions
  }

  private fun gestureGhostNodes(): List<LauncherIconGhostNode> {
    val position = gestureGhostPosition ?: return emptyList()
    return listOf(
      LauncherIconGhostNode(
        x = position.x,
        y = position.y,
        radiusPx = dp(HOME_ICON_GHOST_RADIUS_DP).toDouble(),
      ),
    )
  }

  private fun handleGestureGhostChanged(position: Point?) {
    if (position != null) homeIconResetState = null
    if (gestureGhostPosition == position) return

    gestureGhostPosition = position
    updateLauncherIcons()
  }

  private fun beginHomeIconLayoutReset() {
    if (!::canvasView.isInitialized) return
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
      showFeedback("No home icons to reset")
      return
    }

    homeIconAnchors = emptyMap()
    draggingHomeIconPositions = emptyMap()
    canvasView.editMode = false
    homeIconResetState = HomeIconResetState(steps = steps)
    showFeedback("Resetting layout")
    advanceHomeIconResetStep()
  }

  private fun buildHomeIconResetSteps(
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

  private fun isBetterResetCandidate(
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

  private fun advanceHomeIconResetStep() {
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

  private fun handleHomeIconLayoutSettled() {
    val state = homeIconResetState ?: return
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

  private fun finishHomeIconLayoutReset() {
    val finalPositions = homeIconResetState?.fixedPositions.orEmpty()
    if (finalPositions.isNotEmpty()) {
      homeIconAnchors = finalPositions
    }
    homeIconResetState = null
    updateLauncherIcons()
    showFeedback("Layout reset")
  }

  private fun distanceSquared(leftX: Double, leftY: Double, rightX: Double, rightY: Double): Double {
    val dx = leftX - rightX
    val dy = leftY - rightY
    return dx * dx + dy * dy
  }

  private fun targetLabel(
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

  private fun targetSubtitle(gesture: SavedGesture): String {
    gesture.specialActionId?.let { actionId ->
      return SpecialFunctions.find(actionId)?.subtitle ?: "Special function"
    }
    return gesture.packageName ?: "No app assigned"
  }

  private fun previewColumn(title: String, points: List<Point>, preview: GesturePreviewView = GesturePreviewView(this)): View {
    preview.points = points
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      addView(TextView(this@MainActivity).apply {
        text = title
        setTextColor(secondaryTextColor())
        textSize = 11f
        gravity = Gravity.CENTER
      })
      addView(preview)
    }
  }

  private fun settingsSwitchRow(
    label: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
  ): View {
    lateinit var switch: Switch
    return LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      minimumHeight = dp(72)
      setPadding(0, dp(8), 0, dp(8))
      applySelectableItemBackground()
      addView(settingsTextColumn(label, summary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
      switch = Switch(this@MainActivity).apply {
        isChecked = checked
        setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean -> onChange(isChecked) }
      }
      addView(switch)
      setOnClickListener {
        switch.isChecked = !switch.isChecked
      }
    }
  }

  private fun settingsActionRow(
    label: String,
    summary: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
  ): View {
    return LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      minimumHeight = dp(64)
      setPadding(0, dp(8), 0, dp(8))
      applySelectableItemBackground()
      addView(settingsTextColumn(label, summary, destructive), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
      addView(ImageView(this@MainActivity).apply {
        setImageResource(R.drawable.ic_chevron_right_24)
        setColorFilter(secondaryTextColor())
        contentDescription = null
      }, LinearLayout.LayoutParams(dp(32), dp(32)))
      setOnClickListener { onClick() }
    }
  }

  private fun settingsTextColumn(label: String, summary: String, destructive: Boolean = false): View {
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      addView(TextView(this@MainActivity).apply {
        text = label
        setTextColor(if (destructive) Color.rgb(186, 26, 26) else primaryTextColor())
        textSize = 16f
        maxLines = 1
      })
      addView(TextView(this@MainActivity).apply {
        text = summary
        setTextColor(secondaryTextColor())
        textSize = 13f
        maxLines = 2
      })
    }
  }

  private fun settingDivider(): View {
    return View(this).apply {
      setBackgroundColor(themeColor(android.R.attr.textColorHint, Color.LTGRAY))
      alpha = 0.35f
      layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
    }
  }

  private fun titleText(text: String, size: Float): TextView {
    return TextView(this).apply {
      this.text = text
      setTextColor(primaryTextColor())
      textSize = size
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.START
      setPadding(0, dp(4), 0, dp(4))
    }
  }

  private fun bodyText(text: String): TextView {
    return TextView(this).apply {
      this.text = text
      setTextColor(secondaryTextColor())
      textSize = 14f
      setPadding(0, dp(4), 0, dp(4))
    }
  }

  private fun primaryButton(text: String, onClick: () -> Unit): Button {
    return Button(this).apply {
      this.text = text
      setOnClickListener { onClick() }
    }
  }

  private fun secondaryButton(text: String, onClick: () -> Unit): Button {
    return Button(this).apply {
      this.text = text
      setOnClickListener { onClick() }
    }
  }

  private fun showConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
  ) {
    val dialog = Dialog(this)
    val root = compactDialogRoot(title)
    root.addView(bodyText(message))

    val actions = dialogActions()
    actions.addView(primaryButton(confirmText) {
      onConfirm()
      dialog.dismiss()
    }, actionButtonParams())
    root.addView(actions)

    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setContentView(root)
    trackDialog(dialog)
    dialog.show()
    fitCompactDialog(dialog)
  }

  private fun trackDialog(dialog: Dialog, onDismiss: (() -> Unit)? = null) {
    activeDialogs += dialog
    dialog.setOnDismissListener {
      activeDialogs -= dialog
      onDismiss?.invoke()
    }
  }

  private fun dismissActiveDialogs() {
    activeDialogs.toList().forEach { dialog ->
      if (dialog.isShowing) dialog.dismiss()
    }
  }

  private fun screenDialogRoot(title: String): LinearLayout {
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(16), dp(28), dp(16), dp(12))
      setBackgroundColor(dialogBackgroundColor())
      addView(titleText(title, 22f), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      ).apply {
        bottomMargin = dp(8)
      })
    }
  }

  private fun compactDialogRoot(title: String): LinearLayout {
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(24), dp(20), dp(24), dp(16))
      setBackgroundColor(dialogBackgroundColor())
      addView(titleText(title, 20f), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      ).apply {
        bottomMargin = dp(8)
      })
    }
  }

  private fun dialogActions(): LinearLayout {
    return LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.END
      setPadding(0, dp(8), 0, 0)
    }
  }

  private fun actionButtonParams(): LinearLayout.LayoutParams {
    return LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
      leftMargin = dp(8)
    }
  }

  private fun expandDialog(dialog: Dialog, showKeyboard: Boolean = false) {
    dialog.window?.apply {
      setBackgroundDrawable(ColorDrawable(dialogBackgroundColor()))
      val softInputMode = if (showKeyboard) {
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
          WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
      } else {
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
      }
      setSoftInputMode(softInputMode)
      setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
  }

  private fun fullScreenDialog(): Dialog {
    return Dialog(this, R.style.FullscreenDialogTheme)
  }

  private fun fitCompactDialog(dialog: Dialog) {
    dialog.window?.apply {
      setBackgroundDrawable(ColorDrawable(dialogBackgroundColor()))
      setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
  }

  private fun showKeyboard(view: View) {
    view.isFocusableInTouchMode = true
    view.requestFocus()
    view.post {
      view.requestFocus()
      val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
    view.postDelayed({
      view.requestFocus()
      val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }, 250)
  }

  private fun hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    view.clearFocus()
  }

  private fun primaryTextColor(): Int = themeColor(android.R.attr.textColorPrimary, Color.BLACK)

  private fun secondaryTextColor(): Int = themeColor(android.R.attr.textColorSecondary, Color.DKGRAY)

  private fun dialogBackgroundColor(): Int {
    val themedColor = themeColor(android.R.attr.colorBackground, Color.TRANSPARENT)
    if (Color.alpha(themedColor) > 0) return themedColor

    val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
      Color.rgb(18, 18, 18)
    } else {
      Color.WHITE
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

  private data class HomeIconResetStep(
    val packageName: String,
    val position: Point,
  )

  private data class HomeIconResetState(
    val steps: List<HomeIconResetStep>,
    val fixedPositions: Map<String, Point> = emptyMap(),
    val nextIndex: Int = 0,
    val activeStep: HomeIconResetStep? = null,
  )
}
