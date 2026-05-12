package com.thejusticeman.glyphos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val WIDGET_STORAGE_NAME = "glyph_os_native_store"
private const val WIDGET_STORAGE_KEY = "glyph_os_widgets"
private const val WIDGET_STORAGE_SCHEMA_VERSION = 1

class WidgetStore(context: Context) {
  private val preferences = context.getSharedPreferences(WIDGET_STORAGE_NAME, Context.MODE_PRIVATE)

  fun loadPlacements(): List<WidgetPlacement> {
    val json = preferences.getString(WIDGET_STORAGE_KEY, null) ?: return emptyList()
    return try {
      val root = JSONObject(json)
      parseWidgetArray(root.optJSONArray("placements") ?: JSONArray())
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun savePlacements(placements: List<WidgetPlacement>) {
    val root = JSONObject()
      .put("version", WIDGET_STORAGE_SCHEMA_VERSION)
      .put("placements", placementsToJson(placements))
    preferences.edit().putString(WIDGET_STORAGE_KEY, root.toString()).apply()
  }

  private fun parseWidgetArray(array: JSONArray): List<WidgetPlacement> {
    val placements = mutableListOf<WidgetPlacement>()
    for (index in 0 until array.length()) {
      val placement = parsePlacement(array.optJSONObject(index) ?: continue) ?: continue
      placements += placement
    }
    return placements
  }

  private fun parsePlacement(json: JSONObject): WidgetPlacement? {
    if (!json.has("appWidgetId") || !json.has("provider")) return null

    val appWidgetId = json.optInt("appWidgetId", -1)
    val provider = json.optString("provider", "").takeIf { it.isNotBlank() } ?: return null
    if (appWidgetId <= 0) return null

    return WidgetPlacement(
      appWidgetId = appWidgetId,
      provider = provider,
      x = json.optInt("x", 0),
      y = json.optInt("y", 0),
      width = json.optInt("width", 0),
      height = json.optInt("height", 0),
      spanX = json.optInt("spanX", 1).coerceAtLeast(1),
      spanY = json.optInt("spanY", 1).coerceAtLeast(1),
      minWidth = json.optInt("minWidth", 48).coerceAtLeast(1),
      minHeight = json.optInt("minHeight", 48).coerceAtLeast(1),
      configured = json.optBoolean("configured", false),
      lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis()),
    )
  }

  private fun placementsToJson(placements: List<WidgetPlacement>): JSONArray {
    val array = JSONArray()
    placements.forEach { placement ->
      array.put(
        JSONObject()
          .put("appWidgetId", placement.appWidgetId)
          .put("provider", placement.provider)
          .put("x", placement.x)
          .put("y", placement.y)
          .put("width", placement.width)
          .put("height", placement.height)
          .put("spanX", placement.spanX)
          .put("spanY", placement.spanY)
          .put("minWidth", placement.minWidth)
          .put("minHeight", placement.minHeight)
          .put("configured", placement.configured)
          .put("lastUpdated", placement.lastUpdated),
      )
    }
    return array
  }
}
