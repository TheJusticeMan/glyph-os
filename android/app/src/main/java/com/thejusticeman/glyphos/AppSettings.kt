package com.thejusticeman.glyphos

import android.content.Context

private const val SETTINGS_NAME = "glyph_os_settings"
private const val ONBOARDING_KEY = "glyph_os_onboarding_done"
private const val TRAIL_EFFECT_KEY = "glyph_os_trail_effect"
private const val LAUNCH_ON_CREATE_SHORTCUT_KEY = "glyph_os_launch_on_create_shortcut"
private const val ALLOW_BACKWARD_GESTURES_KEY = "glyph_os_allow_backward_gestures"

class AppSettings(context: Context) {
  private val preferences = context.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)

  var onboardingDone: Boolean
    get() = preferences.getBoolean(ONBOARDING_KEY, false)
    set(value) = preferences.edit().putBoolean(ONBOARDING_KEY, value).apply()

  var trailEffect: Boolean
    get() = preferences.getBoolean(TRAIL_EFFECT_KEY, false)
    set(value) = preferences.edit().putBoolean(TRAIL_EFFECT_KEY, value).apply()

  var launchOnCreateShortcut: Boolean
    get() = preferences.getBoolean(LAUNCH_ON_CREATE_SHORTCUT_KEY, true)
    set(value) = preferences.edit().putBoolean(LAUNCH_ON_CREATE_SHORTCUT_KEY, value).apply()

  var allowBackwardGestures: Boolean
    get() = preferences.getBoolean(ALLOW_BACKWARD_GESTURES_KEY, true)
    set(value) = preferences.edit().putBoolean(ALLOW_BACKWARD_GESTURES_KEY, value).apply()
}
