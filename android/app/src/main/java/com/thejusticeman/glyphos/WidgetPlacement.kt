package com.thejusticeman.glyphos

data class WidgetPlacement(
  val appWidgetId: Int,
  val provider: String,
  val x: Int,
  val y: Int,
  val width: Int,
  val height: Int,
  val spanX: Int = 1,
  val spanY: Int = 1,
  val minWidth: Int = 48,
  val minHeight: Int = 48,
  val configured: Boolean = false,
  val lastUpdated: Long = System.currentTimeMillis(),
)
