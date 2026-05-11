package com.thejusticeman.glyphos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val STORAGE_NAME = "glyph_os_native_store"
private const val STORAGE_KEY = "glyph_os_gestures"
private const val STORAGE_SCHEMA_VERSION = 2

class GestureStore(context: Context) {
  private val preferences = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)

  fun loadGestures(): List<SavedGesture> {
    val json = preferences.getString(STORAGE_KEY, null) ?: return emptyList()
    return try {
      val trimmed = json.trim()
      if (trimmed.startsWith("[")) {
        parseGestureArray(JSONArray(trimmed))
      } else {
        val root = JSONObject(trimmed)
        parseGestureArray(root.optJSONArray("gestures") ?: JSONArray())
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun saveGestures(gestures: List<SavedGesture>) {
    val root = JSONObject()
      .put("version", STORAGE_SCHEMA_VERSION)
      .put("gestures", gesturesToJson(gestures))
    preferences.edit().putString(STORAGE_KEY, root.toString()).apply()
  }

  fun clearGestures() {
    preferences.edit().remove(STORAGE_KEY).apply()
  }

  private fun parseGestureArray(array: JSONArray): List<SavedGesture> {
    val gestures = mutableListOf<SavedGesture>()
    for (index in 0 until array.length()) {
      val gesture = parseGesture(array.optJSONObject(index) ?: continue) ?: continue
      gestures += gesture
    }
    return gestures
  }

  private fun parseGesture(json: JSONObject): SavedGesture? {
    val label = json.optString("label", "").takeIf { it.isNotBlank() } ?: return null
    val pathJson = json.optJSONArray("normalizedPath") ?: return null
    if (pathJson.length() < 2) return null

    val path = mutableListOf<Point>()
    for (index in 0 until pathJson.length()) {
      val pointJson = pathJson.optJSONObject(index) ?: return null
      if (!pointJson.has("x") || !pointJson.has("y")) return null
      path += Point(pointJson.optDouble("x"), pointJson.optDouble("y"))
    }

    val packageName = json.optString("packageName", "").takeIf { it.isNotBlank() }
    val specialActionId = json.optString("specialActionId", "").takeIf { it.isNotBlank() }
    return SavedGesture(
      label = label,
      packageName = packageName,
      specialActionId = specialActionId,
      normalizedPath = path,
    )
  }

  private fun gesturesToJson(gestures: List<SavedGesture>): JSONArray {
    val array = JSONArray()
    gestures.forEach { gesture ->
      val json = JSONObject()
        .put("label", gesture.label)
        .put("normalizedPath", pointsToJson(gesture.normalizedPath))
      gesture.packageName?.let { json.put("packageName", it) }
      gesture.specialActionId?.let { json.put("specialActionId", it) }
      array.put(json)
    }
    return array
  }

  private fun pointsToJson(points: List<Point>): JSONArray {
    val array = JSONArray()
    points.forEach { point ->
      array.put(JSONObject().put("x", point.x).put("y", point.y))
    }
    return array
  }
}
