package com.thejusticeman.glyphos

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

class InstalledApps(private val context: Context) {
  @Volatile
  private var cachedApps: List<AppDetail>? = null

  fun getCachedInstalledApps(): List<AppDetail>? = cachedApps

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

  fun filterApps(apps: List<AppDetail>, query: String): AppSearchResult {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) {
      return AppSearchResult(apps, isPackageSearch = false)
    }

    val labelMatches = apps.filter { app ->
      app.label.lowercase().contains(normalizedQuery)
    }
    if (labelMatches.isNotEmpty()) {
      return AppSearchResult(labelMatches, isPackageSearch = false)
    }

    return AppSearchResult(
      apps = apps.filter { app ->
        !app.isSpecialFunction() && app.packageName.lowercase().contains(normalizedQuery)
      },
      isPackageSearch = true,
    )
  }

  fun launchApp(packageName: String): Boolean {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launchIntent)
    return true
  }
}

data class AppSearchResult(
  val apps: List<AppDetail>,
  val isPackageSearch: Boolean,
)

data class AppDetail(
  val label: String,
  val packageName: String,
  val icon: Drawable?,
  val specialActionId: String? = null,
  val subtitle: String? = null,
) {
  fun targetKey(): String {
    specialActionId?.let { return "special:$it" }
    return "package:$packageName"
  }

  fun isSpecialFunction(): Boolean = specialActionId != null
}
