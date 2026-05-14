package com.thejusticeman.glyphos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val HOME_ICON_ANCHOR_STORAGE_NAME = "glyph_os_native_store"
private const val HOME_ICON_ANCHOR_STORAGE_KEY = "glyph_os_home_icon_anchors"
private const val HOME_ICON_ANCHOR_SCHEMA_VERSION = 1

class HomeIconAnchorStore(context: Context) {
  private val preferences = context.getSharedPreferences(HOME_ICON_ANCHOR_STORAGE_NAME, Context.MODE_PRIVATE)

  fun loadAnchors(): Map<String, Point> {
    val json = preferences.getString(HOME_ICON_ANCHOR_STORAGE_KEY, null) ?: return emptyMap()
    return try {
      val root = JSONObject(json)
      parseAnchorArray(root.optJSONArray("anchors") ?: JSONArray())
    } catch (_: Exception) {
      emptyMap()
    }
  }

  fun saveAnchors(anchors: Map<String, Point>) {
    val root = JSONObject()
      .put("version", HOME_ICON_ANCHOR_SCHEMA_VERSION)
      .put("anchors", anchorsToJson(anchors))
    preferences.edit().putString(HOME_ICON_ANCHOR_STORAGE_KEY, root.toString()).apply()
  }

  private fun parseAnchorArray(array: JSONArray): Map<String, Point> {
    val anchors = mutableMapOf<String, Point>()
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val packageName = item.optString("packageName", "").takeIf { it.isNotBlank() } ?: continue
      if (!item.has("x") || !item.has("y")) continue
      anchors[packageName] = Point(
        x = item.optDouble("x"),
        y = item.optDouble("y"),
      )
    }
    return anchors
  }

  private fun anchorsToJson(anchors: Map<String, Point>): JSONArray {
    val array = JSONArray()
    anchors.toSortedMap().forEach { (packageName, point) ->
      array.put(
        JSONObject()
          .put("packageName", packageName)
          .put("x", point.x)
          .put("y", point.y),
      )
    }
    return array
  }
}