package com.thejusticeman.glyphos

import android.app.Dialog
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView

// App data access and pickers: installed app cache, launch counts, app/gesture picker dialogs.

internal fun MainActivity.cachedInstalledApps(): List<AppDetail> = installedApps.getCachedInstalledApps().orEmpty()

internal fun MainActivity.cachedAppLabels(): Map<String, String> {
  return cachedInstalledApps().associate { it.packageName to it.label }
}

internal fun MainActivity.refreshInstalledAppCache(onLoaded: ((List<AppDetail>) -> Unit)? = null) {
  appRefreshExecutor.execute {
    val refreshedApps = installedApps.getInstalledApps(forceRefresh = true)
    mainHandler.post {
      if (!isFinishing && !isDestroyed) {
        onLoaded?.invoke(refreshedApps)
      }
    }
  }
}

internal fun MainActivity.launchAppPackage(packageName: String): Boolean {
  val launched = installedApps.launchApp(packageName)
  if (launched) {
    val nextCount = launchUsageStore.increment(packageName)
    launchCounts = launchCounts + (packageName to nextCount)
    updateLauncherIcons()
  }
  return launched
}

internal fun MainActivity.buildTargetList(includeSpecialFunctions: Boolean, apps: List<AppDetail>): List<AppDetail> {
  val specialTargets = if (includeSpecialFunctions) {
    SpecialFunctions.all.map(SpecialFunctions::asTarget)
  } else {
    emptyList()
  }
  return specialTargets + apps
}

internal fun MainActivity.showAppPickerDialog(
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

internal fun MainActivity.showGestureLibraryDialog() {
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

internal fun MainActivity.gestureRow(gesture: SavedGesture, appLabel: String, targetSubtitle: String, ownerDialog: Dialog): View {
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

  row.addView(primaryButton("Reassign") {
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
    }
  })
  row.addView(primaryButton("Delete") {
    showConfirmDialog(
      title = "Delete Gesture",
      message = "Delete ${gesture.label}?",
      confirmText = "Delete",
    ) {
      savedGestures.removeAll { it.label == gesture.label }
      persistGestures()
      ownerDialog.dismiss()
      showGestureLibraryDialog()
    }
  })
  return row
}

internal fun MainActivity.handleLauncherIconTapped(app: AppDetail) {
  val launched = launchAppPackage(app.packageName)
  if (!launched) showFeedback("Launch failed")
}
