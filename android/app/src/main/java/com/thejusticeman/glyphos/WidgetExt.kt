package com.thejusticeman.glyphos

import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

// Widget lifecycle: discovery, binding, placement, removal, and host view management.

internal fun MainActivity.restoreWidgets() {
  if (!isWidgetLayerViewInitialized) return

  val manager = AppWidgetManager.getInstance(this)
  val invalidWidgetIds = mutableListOf<Int>()
  widgetLayerView.clearWidgets()

  widgetPlacements.values.forEach { placement ->
    val providerInfo = manager.getAppWidgetInfo(placement.appWidgetId)
    if (providerInfo == null) {
      invalidWidgetIds += placement.appWidgetId
      return@forEach
    }

    val resolvedPlacement = resolveWidgetPlacementSize(
      placement,
      minWidthDp = providerInfo.minWidth,
      minHeightDp = providerInfo.minHeight,
    )
    if (resolvedPlacement != placement) {
      widgetPlacements[resolvedPlacement.appWidgetId] = resolvedPlacement
    }

    val hostView = widgetHost.createView(this, placement.appWidgetId, providerInfo)
    widgetLayerView.addWidgetView(hostView, resolvedPlacement)
  }

  if (invalidWidgetIds.isNotEmpty()) {
    invalidWidgetIds.forEach { widgetPlacements.remove(it) }
    persistWidgetPlacements()
  }

  widgetLayerView.updateLayout(widgetPlacements.values.toList())
}

internal fun MainActivity.resolveWidgetPlacementSize(
  placement: WidgetPlacement,
  minWidthDp: Int,
  minHeightDp: Int,
): WidgetPlacement {
  val minWidth = dp(minWidthDp.coerceAtLeast(48))
  val minHeight = dp(minHeightDp.coerceAtLeast(48))
  val width = placement.width.takeIf { it > 0 } ?: minWidth
  val height = placement.height.takeIf { it > 0 } ?: minHeight

  if (
    width == placement.width &&
    height == placement.height &&
    minWidth == placement.minWidth &&
    minHeight == placement.minHeight
  ) {
    return placement
  }

  return placement.copy(
    width = width,
    height = height,
    minWidth = minWidth,
    minHeight = minHeight,
    lastUpdated = System.currentTimeMillis(),
  )
}

internal fun MainActivity.persistWidgetPlacements() {
  widgetStore.savePlacements(widgetPlacements.values.sortedBy { it.appWidgetId })
}

internal fun MainActivity.showWidgetPickerDialog() {
  val providers = loadWidgetProviders()
  if (providers.isEmpty()) {
    return
  }

  val dialog = fullScreenDialog()
  val root = screenDialogRoot("Add Widget")
  val list = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(0, dp(4), 0, dp(12))
  }

  providers
    .groupBy { widgetProviderAppLabel(it) }
    .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    .forEach { (appLabel, appProviders) ->
      list.addView(widgetAppHeader(appLabel, appProviders.size))
      appProviders.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { widgetProviderLabel(it) })
        .forEach { provider ->
          list.addView(widgetProviderRow(provider, dialog))
          list.addView(settingDivider())
        }
    }

  val scroll = ScrollView(this).apply { addView(list) }
  root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

  dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
  dialog.setContentView(root)
  trackDialog(dialog)
  dialog.show()
  expandDialog(dialog)
}

internal fun MainActivity.loadWidgetProviders(): List<AppWidgetProviderInfo> {
  val manager = AppWidgetManager.getInstance(this)
  return runCatching { manager.installedProviders.orEmpty() }
    .getOrDefault(emptyList())
    .filter { it.provider != null }
}

internal fun MainActivity.selectWidgetProvider(providerInfo: AppWidgetProviderInfo, ownerDialog: Dialog) {
  val appWidgetId = widgetHost.allocateAppWidgetId()
  pendingWidgetAllocationId = appWidgetId
  ownerDialog.dismiss()

  val manager = AppWidgetManager.getInstance(this)
  val bound = runCatching {
    manager.bindAppWidgetIdIfAllowed(
      appWidgetId,
      providerInfo.profile,
      providerInfo.provider,
      null,
    )
  }.getOrDefault(false)

  if (bound) {
    pendingWidgetAllocationId = null
    continueWidgetBinding(appWidgetId)
    return
  }

  val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, providerInfo.profile)
  }
  try {
    startActivityForResult(intent, REQUEST_BIND_WIDGET)
  } catch (_: Exception) {
    pendingWidgetAllocationId = null
    releaseWidgetId(appWidgetId)
    showFeedback("Widget permission unavailable")
  }
}

internal fun MainActivity.continueWidgetBinding(appWidgetId: Int) {
  val manager = AppWidgetManager.getInstance(this)
  val providerInfo = manager.getAppWidgetInfo(appWidgetId)
  if (providerInfo == null) {
    releaseWidgetId(appWidgetId)
    showFeedback("Widget unavailable")
    return
  }

  val configComponent = providerInfo.configure
  if (configComponent == null) {
    addWidgetPlacement(appWidgetId, configured = true)
    return
  }

  val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
    component = configComponent
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
  }
  startActivityForResult(intent, REQUEST_CREATE_WIDGET)
}

internal fun MainActivity.addWidgetPlacement(appWidgetId: Int, configured: Boolean) {
  val manager = AppWidgetManager.getInstance(this)
  val providerInfo = manager.getAppWidgetInfo(appWidgetId)
  if (providerInfo == null) {
    releaseWidgetId(appWidgetId)
    showFeedback("Widget unavailable")
    return
  }

  val minWidthPx = dp(providerInfo.minWidth.coerceAtLeast(120))
  val minHeightPx = dp(providerInfo.minHeight.coerceAtLeast(80))
  val width = minWidthPx
  val height = minHeightPx
  val canvasWidth = if (isCanvasViewInitialized && canvasView.width > 0) {
    canvasView.width
  } else {
    resources.displayMetrics.widthPixels
  }
  val canvasHeight = if (isCanvasViewInitialized && canvasView.height > 0) {
    canvasView.height
  } else {
    resources.displayMetrics.heightPixels
  }

  val placement = WidgetPlacement(
    appWidgetId = appWidgetId,
    provider = providerInfo.provider.flattenToString(),
    x = ((canvasWidth - width) / 2).coerceAtLeast(0),
    y = ((canvasHeight - height) / 2).coerceAtLeast(0),
    width = width,
    height = height,
    minWidth = minWidthPx,
    minHeight = minHeightPx,
    configured = configured,
  )
  widgetPlacements[appWidgetId] = placement
  persistWidgetPlacements()

  val hostView = widgetHost.createView(this, appWidgetId, providerInfo)
  widgetLayerView.addWidgetView(hostView, placement)
  widgetLayerView.updateLayout(widgetPlacements.values.toList())
}

internal fun MainActivity.showRemoveWidgetDialog() {
  if (widgetPlacements.isEmpty()) {
    return
  }

  val manager = AppWidgetManager.getInstance(this)
  val dialog = fullScreenDialog()
  val root = screenDialogRoot("Remove Widget")
  val list = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(0, dp(4), 0, dp(12))
  }

  widgetPlacements.values.sortedBy { it.appWidgetId }.forEach { placement ->
    val providerInfo = manager.getAppWidgetInfo(placement.appWidgetId)
    val label = providerInfo?.loadLabel(packageManager)?.toString().orEmpty().ifBlank {
      "Widget #${placement.appWidgetId}"
    }
    val subtitle = providerInfo?.provider?.flattenToShortString() ?: placement.provider
    list.addView(widgetRemovalRow(label, subtitle, placement, providerInfo, dialog))
    list.addView(settingDivider())
  }

  val scroll = ScrollView(this).apply { addView(list) }
  root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

  dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
  dialog.setContentView(root)
  trackDialog(dialog)
  dialog.show()
  expandDialog(dialog)
}

internal fun MainActivity.removeWidget(appWidgetId: Int) {
  widgetLayerView.removeWidgetView(appWidgetId)
  widgetPlacements.remove(appWidgetId)
  releaseWidgetId(appWidgetId)
  persistWidgetPlacements()
}

internal fun MainActivity.releaseWidgetIdFromIntent(data: Intent?) {
  val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
  if (appWidgetId > 0) {
    releaseWidgetId(appWidgetId)
  }
}

internal fun MainActivity.releaseWidgetId(appWidgetId: Int) {
  runCatching { widgetHost.deleteAppWidgetId(appWidgetId) }
}

internal fun MainActivity.widgetAppHeader(appLabel: String, count: Int): View {
  return TextView(this).apply {
    text = if (count == 1) appLabel else "$appLabel ($count)"
    setTextColor(primaryTextColor())
    textSize = 15f
    typeface = Typeface.DEFAULT_BOLD
    setPadding(0, dp(18), 0, dp(6))
  }
}

internal fun MainActivity.widgetProviderRow(providerInfo: AppWidgetProviderInfo, ownerDialog: Dialog): View {
  val label = widgetProviderLabel(providerInfo)
  val subtitle = listOf(
    "${providerInfo.minWidth.coerceAtLeast(1)} x ${providerInfo.minHeight.coerceAtLeast(1)} dp",
    providerInfo.provider.flattenToShortString(),
  ).joinToString("  •  ")

  return LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = dp(104)
    setPadding(0, dp(8), 0, dp(8))
    applySelectableItemBackground()

    addView(widgetPreviewImage(providerInfo), LinearLayout.LayoutParams(dp(96), dp(72)).apply {
      rightMargin = dp(12)
    })
    addView(settingsTextColumn(label, subtitle), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    addView(ImageView(this@widgetProviderRow).apply {
      setImageResource(R.drawable.ic_chevron_right_24)
      setColorFilter(secondaryTextColor())
      contentDescription = null
    }, LinearLayout.LayoutParams(dp(32), dp(32)))

    setOnClickListener { selectWidgetProvider(providerInfo, ownerDialog) }
  }
}

internal fun MainActivity.widgetPreviewImage(providerInfo: AppWidgetProviderInfo): View {
  val preview = runCatching { providerInfo.loadPreviewImage(this, 0) }.getOrNull()
    ?: runCatching { providerInfo.loadIcon(this, 0) }.getOrNull()

  return FrameLayout(this).apply {
    background = GradientDrawable().apply {
      setColor(Color.argb(26, 127, 127, 127))
      cornerRadius = dp(8).toFloat()
      setStroke(1, Color.argb(45, 127, 127, 127))
    }
    addView(ImageView(this@widgetPreviewImage).apply {
      setImageDrawable(preview)
      scaleType = ImageView.ScaleType.CENTER_INSIDE
      adjustViewBounds = true
      contentDescription = null
      setPadding(dp(6), dp(6), dp(6), dp(6))
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
  }
}

internal fun MainActivity.widgetProviderLabel(providerInfo: AppWidgetProviderInfo): String {
  return providerInfo.loadLabel(packageManager)?.toString().orEmpty().ifBlank {
    providerInfo.provider.className.substringAfterLast('.')
  }
}

internal fun MainActivity.widgetProviderAppLabel(providerInfo: AppWidgetProviderInfo): String {
  val packageName = providerInfo.provider.packageName
  return runCatching {
    val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
    packageManager.getApplicationLabel(applicationInfo).toString()
  }.getOrDefault(packageName)
}

internal fun MainActivity.widgetRemovalRow(
  label: String,
  subtitle: String,
  placement: WidgetPlacement,
  providerInfo: android.appwidget.AppWidgetProviderInfo?,
  ownerDialog: Dialog,
): View {
  val row = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(0, dp(10), 0, dp(10))
  }

  val header = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
  }
  header.addView(settingsTextColumn(label, subtitle, destructive = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
  header.addView(primaryButton("Remove") {
    showConfirmDialog(
      title = "Remove Widget",
      message = "Remove $label from your home surface?",
      confirmText = "Remove",
    ) {
      removeWidget(placement.appWidgetId)
      ownerDialog.dismiss()
    }
  })
  row.addView(header)

  if (providerInfo != null) {
    val preview = runCatching {
      widgetHost.createView(this, placement.appWidgetId, providerInfo)
    }.getOrNull()
    if (preview != null) {
      preview.isEnabled = false
      preview.isClickable = false
      val maxPreviewWidth = resources.displayMetrics.widthPixels - dp(64)
      val scaledWidth = placement.width.coerceAtMost(maxPreviewWidth).coerceAtLeast(dp(120))
      val scaledHeight = ((scaledWidth.toFloat() / placement.width.coerceAtLeast(1)) * placement.height)
        .roundToInt()
        .coerceIn(dp(64), dp(220))
      row.addView(preview, LinearLayout.LayoutParams(scaledWidth, scaledHeight).apply {
        topMargin = dp(8)
      })
    }
  }

  return row
}
