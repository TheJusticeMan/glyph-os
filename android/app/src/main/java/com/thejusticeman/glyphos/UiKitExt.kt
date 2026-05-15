package com.thejusticeman.glyphos

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

// Pure UI factory methods: dialog scaffolding, typography, form rows, keyboard, colors.
// No business logic — these only build or style views.

internal fun MainActivity.screenDialogRoot(title: String): LinearLayout {
  return LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(28), dp(16), dp(12))
    setBackgroundColor(dialogBackgroundColor())
    addView(titleText(title, 22f), LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
      bottomMargin = dp(8)
    })
  }
}

internal fun MainActivity.compactDialogRoot(title: String): LinearLayout {
  return LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(24), dp(20), dp(24), dp(16))
    setBackgroundColor(dialogBackgroundColor())
    addView(titleText(title, 20f), LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
      bottomMargin = dp(8)
    })
  }
}

internal fun MainActivity.dialogActions(): LinearLayout {
  return LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.END
    setPadding(0, dp(8), 0, 0)
  }
}

internal fun MainActivity.actionButtonParams(): LinearLayout.LayoutParams {
  return LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
    leftMargin = dp(8)
  }
}

internal fun MainActivity.expandDialog(dialog: Dialog, showKeyboard: Boolean = false) {
  dialog.window?.apply {
    setBackgroundDrawable(ColorDrawable(dialogBackgroundColor()))
    val softInputMode = if (showKeyboard) {
      WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
    } else {
      WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }
    setSoftInputMode(softInputMode)
    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
  }
}

internal fun MainActivity.fullScreenDialog(): Dialog {
  return Dialog(this, R.style.FullscreenDialogTheme)
}

internal fun MainActivity.fitCompactDialog(dialog: Dialog) {
  dialog.window?.apply {
    setBackgroundDrawable(ColorDrawable(dialogBackgroundColor()))
    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
  }
}

internal fun MainActivity.trackDialog(dialog: Dialog, onDismiss: (() -> Unit)? = null) {
  activeDialogs += dialog
  dialog.setOnDismissListener {
    activeDialogs -= dialog
    onDismiss?.invoke()
  }
}

internal fun MainActivity.dismissActiveDialogs() {
  activeDialogs.toList().forEach { dialog ->
    if (dialog.isShowing) dialog.dismiss()
  }
}

internal fun MainActivity.showConfirmDialog(
  title: String,
  message: String,
  confirmText: String,
  onConfirm: () -> Unit,
) {
  val dialog = Dialog(this)
  val root = compactDialogRoot(title)
  root.addView(bodyText(message))

  val actions = dialogActions()
  actions.addView(primaryButton(confirmText) {
    onConfirm()
    dialog.dismiss()
  }, actionButtonParams())
  root.addView(actions)

  dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  dialog.setContentView(root)
  trackDialog(dialog)
  dialog.show()
  fitCompactDialog(dialog)
}

internal fun MainActivity.settingsSwitchRow(
  label: String,
  summary: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
): View {
  lateinit var switch: Switch
  return LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = dp(72)
    setPadding(0, dp(8), 0, dp(8))
    applySelectableItemBackground()
    addView(settingsTextColumn(label, summary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    switch = Switch(this@settingsSwitchRow).apply {
      isChecked = checked
      setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean -> onChange(isChecked) }
    }
    addView(switch)
    setOnClickListener {
      switch.isChecked = !switch.isChecked
    }
  }
}

internal fun MainActivity.settingsActionRow(
  label: String,
  summary: String,
  destructive: Boolean = false,
  onClick: () -> Unit,
): View {
  return LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = dp(64)
    setPadding(0, dp(8), 0, dp(8))
    applySelectableItemBackground()
    addView(settingsTextColumn(label, summary, destructive), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    addView(ImageView(this@settingsActionRow).apply {
      setImageResource(R.drawable.ic_chevron_right_24)
      setColorFilter(secondaryTextColor())
      contentDescription = null
    }, LinearLayout.LayoutParams(dp(32), dp(32)))
    setOnClickListener { onClick() }
  }
}

internal fun MainActivity.settingsTextColumn(label: String, summary: String, destructive: Boolean = false): View {
  return LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    addView(TextView(this@settingsTextColumn).apply {
      text = label
      setTextColor(if (destructive) Color.rgb(186, 26, 26) else primaryTextColor())
      textSize = 16f
      maxLines = 1
    })
    addView(TextView(this@settingsTextColumn).apply {
      text = summary
      setTextColor(secondaryTextColor())
      textSize = 13f
      maxLines = 2
    })
  }
}

internal fun MainActivity.settingDivider(): View {
  return View(this).apply {
    setBackgroundColor(themeColor(android.R.attr.textColorHint, Color.LTGRAY))
    alpha = 0.35f
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
  }
}

internal fun MainActivity.titleText(text: String, size: Float): TextView {
  return TextView(this).apply {
    this.text = text
    setTextColor(primaryTextColor())
    textSize = size
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.START
    setPadding(0, dp(4), 0, dp(4))
  }
}

internal fun MainActivity.bodyText(text: String): TextView {
  return TextView(this).apply {
    this.text = text
    setTextColor(secondaryTextColor())
    textSize = 14f
    setPadding(0, dp(4), 0, dp(4))
  }
}

internal fun MainActivity.primaryButton(text: String, onClick: () -> Unit): Button {
  return Button(this).apply {
    this.text = text
    setOnClickListener { onClick() }
  }
}

internal fun MainActivity.weightedParams(): LinearLayout.LayoutParams {
  return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
    leftMargin = dp(4)
    rightMargin = dp(4)
  }
}

internal fun MainActivity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun MainActivity.showKeyboard(view: View) {
  view.isFocusableInTouchMode = true
  view.requestFocus()
  view.post {
    view.requestFocus()
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
  }
  view.postDelayed({
    view.requestFocus()
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
  }, 250)
}

internal fun MainActivity.hideKeyboard(view: View) {
  val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
  view.clearFocus()
}

internal fun MainActivity.primaryTextColor(): Int = themeColor(android.R.attr.textColorPrimary, Color.BLACK)

internal fun MainActivity.secondaryTextColor(): Int = themeColor(android.R.attr.textColorSecondary, Color.DKGRAY)

internal fun MainActivity.dialogBackgroundColor(): Int {
  val themedColor = themeColor(android.R.attr.colorBackground, Color.TRANSPARENT)
  if (Color.alpha(themedColor) > 0) return themedColor

  val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
  return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
    Color.rgb(18, 18, 18)
  } else {
    Color.WHITE
  }
}
