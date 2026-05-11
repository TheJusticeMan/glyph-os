package com.thejusticeman.glyphos

import android.content.Context
import android.util.TypedValue
import android.view.View

internal fun Context.themeColor(attr: Int, fallback: Int): Int {
  val typedArray = obtainStyledAttributes(intArrayOf(attr))
  return try {
    typedArray.getColor(0, fallback)
  } finally {
    typedArray.recycle()
  }
}

internal fun View.applySelectableItemBackground() {
  val typedValue = TypedValue()
  if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)) {
    setBackgroundResource(typedValue.resourceId)
  }
}