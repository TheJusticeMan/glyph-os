package com.thejusticeman.glyphos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

private const val INSTALLED_APPS_CACHE_PREFS = "glyph_os_installed_apps_cache"
private const val INSTALLED_APPS_CACHE_KEY = "app_metadata"
private const val APP_METADATA_SCHEMA_VERSION = 1

class InstalledApps(context: Context) {
  private val appContext = context.applicationContext
  private val cacheLock = Any()
  private val cachePrefs = appContext.getSharedPreferences(INSTALLED_APPS_CACHE_PREFS, Context.MODE_PRIVATE)

  @Volatile
  private var cachedApps: List<AppDetail>? = null
  private var cachedByPackage: MutableMap<String, AppDetail> = linkedMapOf()
  private val pendingPackageDeltas = linkedSetOf<String>()
  private var packageReceiver: BroadcastReceiver? = null
  private var packageDeltaListener: (() -> Unit)? = null

  fun getCachedInstalledApps(): List<AppDetail>? = cachedApps

  fun getInstalledApps(forceRefresh: Boolean = false): List<AppDetail> {
    val packageManager = appContext.packageManager

    if (!forceRefresh) {
      synchronized(cacheLock) {
        cachedApps?.let { cached ->
          if (pendingPackageDeltas.isEmpty()) {
            return cached
          }

          val deltas = pendingPackageDeltas.toList()
          pendingPackageDeltas.clear()
          deltas.forEach { packageName ->
            val previous = cachedByPackage[packageName]
            val updated = loadLauncherSummaryForPackage(packageManager, packageName)
            if (updated == null) {
              cachedByPackage.remove(packageName)
            } else {
              cachedByPackage[packageName] = updated.copy(icon = previous?.icon)
            }
          }

          val rebuilt = cachedByPackage.values.sortedBy { it.label.lowercase() }
          cachedApps = rebuilt
          saveMetadataCache(rebuilt)
          return rebuilt
        }
      }

      val diskCached = loadMetadataCache()
      if (diskCached.isNotEmpty()) {
        synchronized(cacheLock) {
          if (cachedApps == null) {
            cachedApps = diskCached
            cachedByPackage = diskCached.associateByTo(linkedMapOf()) { it.packageName }
          }
          return cachedApps.orEmpty()
        }
      }
    }

    return loadFreshAppList(packageManager)
  }

  fun loadMissingIcons(targetPackages: Set<String>? = null, maxToLoad: Int = Int.MAX_VALUE): List<AppDetail>? {
    val packageManager = appContext.packageManager
    val packagesToLoad = synchronized(cacheLock) {
      cachedByPackage.values
        .asSequence()
        .filter { it.icon == null }
        .filter { targetPackages == null || it.packageName in targetPackages }
        .map { it.packageName }
        .take(maxToLoad.coerceAtLeast(0))
        .toList()
    }

    if (packagesToLoad.isEmpty()) return null

    var loadedCount = 0
    packagesToLoad.forEach { packageName ->
      val icon = loadLauncherIconForPackage(packageManager, packageName) ?: return@forEach
      synchronized(cacheLock) {
        val current = cachedByPackage[packageName] ?: return@synchronized
        if (current.icon != null) return@synchronized
        cachedByPackage[packageName] = current.copy(icon = icon)
        loadedCount += 1
      }
    }

    if (loadedCount == 0) return null

    val rebuilt = synchronized(cacheLock) {
      val list = cachedByPackage.values.sortedBy { it.label.lowercase() }
      cachedApps = list
      list
    }
    return rebuilt
  }

  fun startPackageMonitor(onDeltaChanged: () -> Unit) {
    synchronized(cacheLock) {
      packageDeltaListener = onDeltaChanged
      if (packageReceiver != null) return

      val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          val action = intent?.action ?: return
          if (action !in PACKAGE_CHANGED_ACTIONS) return
          val packageName = intent.data?.schemeSpecificPart ?: return

          synchronized(cacheLock) {
            pendingPackageDeltas += packageName
          }
          packageDeltaListener?.invoke()
        }
      }

      val filter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_CHANGED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addDataScheme("package")
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        @Suppress("DEPRECATION")
        appContext.registerReceiver(receiver, filter)
      }
      packageReceiver = receiver
    }
  }

  fun stopPackageMonitor() {
    synchronized(cacheLock) {
      val receiver = packageReceiver ?: return
      runCatching { appContext.unregisterReceiver(receiver) }
      packageReceiver = null
      packageDeltaListener = null
    }
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
    val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(launchIntent)
    return true
  }

  private fun loadFreshAppList(packageManager: android.content.pm.PackageManager): List<AppDetail> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = packageManager.queryIntentActivities(intent, 0)

    val previousIcons = synchronized(cacheLock) {
      cachedByPackage.mapValues { (_, app) -> app.icon }
    }
    val firstLauncherByPackage = linkedMapOf<String, android.content.pm.ResolveInfo>()
    resolveInfos.forEach { resolveInfo ->
      firstLauncherByPackage.putIfAbsent(resolveInfo.activityInfo.packageName, resolveInfo)
    }
    val unsortedApps = firstLauncherByPackage.values.map { resolveInfo ->
      val packageName = resolveInfo.activityInfo.packageName
      AppDetail(
        label = resolveInfo.loadLabel(packageManager).toString(),
        packageName = packageName,
        icon = previousIcons[packageName],
      )
    }

    val apps = unsortedApps.sortedBy { it.label.lowercase() }

    synchronized(cacheLock) {
      cachedApps = apps
      cachedByPackage = apps.associateByTo(linkedMapOf()) { it.packageName }
    }
    saveMetadataCache(apps)
    return apps
  }

  private fun loadLauncherSummaryForPackage(
    packageManager: android.content.pm.PackageManager,
    packageName: String,
  ): AppDetail? {
    val intent = Intent(Intent.ACTION_MAIN)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .setPackage(packageName)
    val resolveInfo = packageManager.queryIntentActivities(intent, 0).firstOrNull() ?: return null
    return AppDetail(
      label = resolveInfo.loadLabel(packageManager).toString(),
      packageName = packageName,
      icon = null,
    )
  }

  private fun loadLauncherIconForPackage(
    packageManager: android.content.pm.PackageManager,
    packageName: String,
  ): Drawable? {
    val intent = Intent(Intent.ACTION_MAIN)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .setPackage(packageName)
    val resolveInfo = packageManager.queryIntentActivities(intent, 0).firstOrNull() ?: return null
    return resolveInfo.loadIcon(packageManager)
  }

  private fun saveMetadataCache(apps: List<AppDetail>) {
    val payload = JSONObject()
      .put("version", APP_METADATA_SCHEMA_VERSION)
      .put(
        "apps",
        JSONArray().apply {
          apps.forEach { app ->
            put(
              JSONObject()
                .put("packageName", app.packageName)
                .put("label", app.label),
            )
          }
        },
      )
    cachePrefs.edit().putString(INSTALLED_APPS_CACHE_KEY, payload.toString()).apply()
  }

  private fun loadMetadataCache(): List<AppDetail> {
    val json = cachePrefs.getString(INSTALLED_APPS_CACHE_KEY, null) ?: return emptyList()
    return try {
      val root = JSONObject(json)
      val version = root.optInt("version", -1)
      if (version != APP_METADATA_SCHEMA_VERSION) return emptyList()

      val apps = mutableListOf<AppDetail>()
      val appArray = root.optJSONArray("apps") ?: JSONArray()
      for (index in 0 until appArray.length()) {
        val item = appArray.optJSONObject(index) ?: continue
        val packageName = item.optString("packageName", "").takeIf { it.isNotBlank() } ?: continue
        val label = item.optString("label", packageName)
        apps += AppDetail(
          label = label,
          packageName = packageName,
          icon = null,
        )
      }
      apps.sortedBy { it.label.lowercase() }
    } catch (_: Exception) {
      emptyList()
    }
  }

  private companion object {
    val PACKAGE_CHANGED_ACTIONS = setOf(
      Intent.ACTION_PACKAGE_ADDED,
      Intent.ACTION_PACKAGE_CHANGED,
      Intent.ACTION_PACKAGE_REMOVED,
      Intent.ACTION_PACKAGE_REPLACED,
    )
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
