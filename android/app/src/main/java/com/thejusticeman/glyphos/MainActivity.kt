package com.thejusticeman.glyphos

import android.app.Activity
import android.app.Dialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.util.concurrent.Executors

internal const val HOME_ICON_MIN_DP = 44
internal const val HOME_ICON_MAX_DP = 112
internal const val HOME_ICON_AREA_BUDGET = 0.42
internal const val HOME_ICON_FOOTPRINT_FACTOR = 1.55
internal const val HOME_ICON_MIN_VISIBLE = 24
internal const val HOME_ICON_MAX_VISIBLE = 64
internal const val HOME_ICON_RESET_SETTLE_PX = 1.5
internal const val HOME_ICON_GHOST_RADIUS_DP = 64
internal const val WIDGET_HOST_ID = 1001
internal const val REQUEST_PICK_WIDGET = 2001
internal const val REQUEST_CREATE_WIDGET = 2002
internal const val REQUEST_BIND_WIDGET = 2003

class MainActivity : Activity() {
  internal lateinit var settings: AppSettings
  internal lateinit var gestureStore: GestureStore
  internal lateinit var installedApps: InstalledApps
  internal lateinit var launchUsageStore: LaunchUsageStore
  internal lateinit var homeIconAnchorStore: HomeIconAnchorStore
  internal lateinit var canvasView: GestureCanvasView
  internal lateinit var widgetStore: WidgetStore
  internal lateinit var widgetHost: AppWidgetHost
  internal lateinit var widgetLayerView: WidgetLayerView
  internal lateinit var bottomSettingsBar: View
  internal lateinit var trashDropTargetView: TextView

  // Helpers for lateinit initialization checks (backing fields not accessible from extension functions)
  internal val isCanvasViewInitialized: Boolean get() = ::canvasView.isInitialized
  internal val isTrashDropTargetViewInitialized: Boolean get() = ::trashDropTargetView.isInitialized
  internal val isWidgetLayerViewInitialized: Boolean get() = ::widgetLayerView.isInitialized
  internal val isBottomSettingsBarInitialized: Boolean get() = ::bottomSettingsBar.isInitialized
  internal val mainHandler = Handler(Looper.getMainLooper())
  internal val appRefreshExecutor = Executors.newSingleThreadExecutor()

  internal var savedGestures: MutableList<SavedGesture> = mutableListOf()
  internal var pendingGesture: PendingGesture? = null
  internal var launchCounts: Map<String, Int> = emptyMap()
  internal var homeIconAnchors: Map<String, Point> = emptyMap()
  internal var dragOverridePositions: Map<String, Point> = emptyMap()
  internal var homeIconResetState: HomeIconResetState? = null
  internal var gestureGhostPosition: Point? = null
  internal val gestureGhostLayoutSnapshot = GestureGhostLayoutSnapshot()
  internal var widgetPlacements: MutableMap<Int, WidgetPlacement> = mutableMapOf()
  internal var pendingWidgetAllocationId: Int? = null
  internal val activeDialogs = mutableSetOf<Dialog>()
  internal var trashDropTargetMode: TrashDropTargetMode = TrashDropTargetMode.ICON_RESET
  internal var homePerfSequence: Int = 0
  internal var homePerfReason: String? = null
  internal var homePerfStartMs: Long = 0L
  internal var lastPauseAtMs: Long = 0L
  internal var widgetRebindGeneration: Int = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    startHomePerf("onCreate")
    setTheme(R.style.AppTheme)
    super.onCreate(savedInstanceState)

    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT

    settings = AppSettings(this)
    gestureStore = GestureStore(this)
    widgetStore = WidgetStore(this)
    homeIconAnchorStore = HomeIconAnchorStore(this)
    widgetHost = AppWidgetHost(this, WIDGET_HOST_ID)
    installedApps = InstalledApps(this)
    installedApps.startPackageMonitor {
      refreshInstalledAppCache { apps ->
        updateLauncherIcons(apps)
      }
    }
    launchUsageStore = LaunchUsageStore(this)
    launchCounts = launchUsageStore.getLaunchCounts()
    homeIconAnchors = homeIconAnchorStore.loadAnchors()
    savedGestures = gestureStore.loadGestures().toMutableList()
    widgetPlacements = widgetStore.loadPlacements().associateBy { it.appWidgetId }.toMutableMap()
    seedDefaultGesturesIfNeeded()
    markHomePerf(
      stage = "stores-loaded",
      details = "gestures=${savedGestures.size} widgets=${widgetPlacements.size} launchCounts=${launchCounts.size} anchors=${homeIconAnchors.size}",
    )

    setContentView(buildRootView())
    markHomePerf("content-view-ready")
    updateLauncherIcons()
    refreshInstalledAppCache {
      markHomePerf("refreshInstalledAppCache-callback", "apps=${it.size}")
      updateLauncherIcons(it)
      scheduleWidgetRebindRetries("refreshInstalledAppCache")
      finishHomePerf("refreshInstalledAppCache-callback")
    }

    if (!settings.onboardingDone) {
      showOnboardingDialog()
    }
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    // A launcher should not exit when Back is pressed from the root screen.
  }

  override fun onPause() {
    lastPauseAtMs = SystemClock.elapsedRealtime()
    dismissActiveDialogs()
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    when (requestCode) {
      REQUEST_PICK_WIDGET -> {
        if (resultCode == RESULT_OK) {
          val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
          pendingWidgetAllocationId = null
          if (appWidgetId > 0) {
            continueWidgetBinding(appWidgetId)
          } else {
            showFeedback("Widget selection failed")
          }
        } else {
          releaseWidgetIdFromIntent(data)
          pendingWidgetAllocationId?.let { releaseWidgetId(it) }
          pendingWidgetAllocationId = null
        }
      }

      REQUEST_BIND_WIDGET -> {
        val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
          ?.takeIf { it > 0 }
          ?: pendingWidgetAllocationId
          ?: -1
        pendingWidgetAllocationId = null
        if (resultCode == RESULT_OK && appWidgetId > 0) {
          continueWidgetBinding(appWidgetId)
        } else if (appWidgetId > 0) {
          releaseWidgetId(appWidgetId)
        }
      }

      REQUEST_CREATE_WIDGET -> {
        val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
          ?.takeIf { it > 0 }
          ?: pendingWidgetAllocationId
          ?: -1
        pendingWidgetAllocationId = null
        if (resultCode == RESULT_OK && appWidgetId > 0) {
          addWidgetPlacement(appWidgetId, configured = true)
        } else if (appWidgetId > 0) {
          releaseWidgetId(appWidgetId)
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    if (!isHomePerfActive()) {
      startHomePerf("onStart")
    } else {
      markHomePerf("onStart-reentered", "reason=${homePerfReason.orEmpty()}")
    }
    widgetHost.startListening()
    markHomePerf("widgetHost-startListening")
    restoreWidgets()
    markHomePerf("restoreWidgets-finished")
    scheduleWidgetRebindRetries("onStart")
    mainHandler.post {
      markHomePerf("post-first-main-loop")
    }
    mainHandler.postDelayed({
      finishHomePerf("onStart-timeout")
    }, 5_000L)
  }

  override fun onRestart() {
    super.onRestart()
    markHomePerf("onRestart")
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    markHomePerf("onNewIntent")
    if (intent != null) {
      setIntent(intent)
    }
  }

  override fun onStop() {
    widgetRebindGeneration += 1
    if (homeIconResetState == null) {
      persistHomeIconAnchors()
    }
    widgetHost.stopListening()
    super.onStop()
  }

  override fun onDestroy() {
    installedApps.stopPackageMonitor()
    mainHandler.removeCallbacksAndMessages(null)
    appRefreshExecutor.shutdownNow()
    if (::widgetLayerView.isInitialized) {
      widgetLayerView.clearWidgets()
    }
    super.onDestroy()
  }

  internal fun buildRootView(): View {
    val root = FrameLayout(this).apply {
      setBackgroundColor(Color.TRANSPARENT)
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    }

    canvasView = GestureCanvasView(this).apply {
      trailEffect = settings.trailEffect
      showGesturePaths = settings.showGesturePaths
      iconScale = settings.homeIconScale
      onGestureComplete = ::handleGestureComplete
      onIconTapped = ::handleLauncherIconTapped
      onCanvasSizeChanged = {
        updateLauncherIcons()
      }
      onIconScaleChanged = { scale -> settings.homeIconScale = scale }
      onIconPositionChanging = ::handleHomeIconPositionChanging
      onIconPositionCommitted = ::handleHomeIconPositionCommitted
      onIconDragStateChanged = { dragging -> setTrashDropTargetVisible(dragging, TrashDropTargetMode.ICON_RESET) }
      onEditModeChanged = { editing ->
        widgetLayerView.editMode = editing
        if (::bottomSettingsBar.isInitialized) {
          bottomSettingsBar.visibility = if (editing) View.VISIBLE else View.GONE
        }
      }
      onLauncherIconLayoutSettled = { mainHandler.post { handleHomeIconLayoutSettled() } }
      onGestureGhostChanged = ::handleGestureGhostChanged
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    }

    root.addView(canvasView)
    widgetLayerView = WidgetLayerView(this).apply {
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
      editMode = canvasView.editMode
      onLongPressEditMode = { canvasView.editMode = true }
      onWidgetDragStateChanged = { dragging -> setTrashDropTargetVisible(dragging, TrashDropTargetMode.WIDGET_REMOVE) }
      onWidgetPlacementChanging = { placement ->
        widgetPlacements[placement.appWidgetId] = placement
        updateTrashDropTargetHighlight(isTrashDropTargetHit(placement))
        updateLauncherIcons()
      }
      onWidgetPlacementCommitted = { placement ->
        if (isTrashDropTargetHit(placement)) {
          setTrashDropTargetVisible(false, trashDropTargetMode)
          removeWidget(placement.appWidgetId)
        } else {
          widgetPlacements[placement.appWidgetId] = placement
          persistWidgetPlacements()
          updateLauncherIcons()
        }
      }
    }
    root.addView(widgetLayerView)
    bottomSettingsBar = buildBottomSettingsBar()
    root.addView(bottomSettingsBar)
    trashDropTargetView = buildTrashDropTarget()
    root.addView(trashDropTargetView)
    syncLauncherGesturePaths()
    return root
  }

  internal fun startHomePerf(reason: String) {
    // Logging disabled.
  }

  internal fun isHomePerfActive(): Boolean {
    return false
  }

  internal fun markHomePerf(stage: String, details: String = "") {
    // Logging disabled.
  }

  internal fun finishHomePerf(finalStage: String) {
    // Logging disabled.
  }

  internal fun scheduleWidgetRebindRetries(reason: String) {
    if (widgetPlacements.isEmpty()) return

    widgetRebindGeneration += 1
    val generation = widgetRebindGeneration
    mainHandler.post {
      if (generation != widgetRebindGeneration) return@post
      if (isFinishing || isDestroyed || !isWidgetLayerViewInitialized) return@post
      restoreWidgets()
    }
  }
}
