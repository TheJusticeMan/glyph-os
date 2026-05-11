package com.thejusticeman.glyphos

import android.content.Context

private const val LAUNCH_USAGE_PREFS = "glyph_os_launch_usage"

class LaunchUsageStore(context: Context) {
  private val preferences = context.getSharedPreferences(LAUNCH_USAGE_PREFS, Context.MODE_PRIVATE)

  fun getLaunchCounts(): Map<String, Int> {
    return preferences.all.mapNotNull { (packageName, value) ->
      val count = when (value) {
        is Int -> value
        is Long -> value.toInt()
        else -> null
      }
      count?.let { packageName to it.coerceAtLeast(0) }
    }.toMap()
  }

  fun increment(packageName: String): Int {
    val nextCount = preferences.getInt(packageName, 0) + 1
    preferences.edit().putInt(packageName, nextCount).apply()
    return nextCount
  }
}
