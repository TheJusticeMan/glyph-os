package com.thejusticeman.glyphos

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

class InstalledApps(private val context: Context) {
  private var cachedApps: List<AppDetail>? = null

  fun getInstalledApps(forceRefresh: Boolean = false): List<AppDetail> {
    cachedApps?.takeIf { !forceRefresh }?.let { return it }

    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = packageManager.queryIntentActivities(intent, 0)
      .map { resolveInfo ->
        AppDetail(
          label = resolveInfo.loadLabel(packageManager).toString(),
          packageName = resolveInfo.activityInfo.packageName,
          icon = resolveInfo.loadIcon(packageManager),
        )
      }
      .distinctBy { it.packageName }
      .sortedBy { it.label.lowercase() }

    cachedApps = apps
    return apps
  }

  fun filterApps(apps: List<AppDetail>, query: String): List<AppDetail> {
    if (query.trim().isEmpty()) return apps
    val lower = query.lowercase()
    return apps.filter { app ->
      app.label.lowercase().contains(lower) || app.packageName.lowercase().contains(lower)
    }
  }

  fun launchApp(packageName: String): Boolean {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launchIntent)
    return true
  }
}

data class AppDetail(
  val label: String,
  val packageName: String,
  val icon: Drawable?,
)
