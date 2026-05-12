package com.thejusticeman.glyphos

import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityWidgetLifecycleTest {
  @Test
  fun widgetLayerPersistsAcrossLifecycleTransitions() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertHasLauncherLayers(activity)
      }

      scenario.moveToState(Lifecycle.State.CREATED)
      scenario.moveToState(Lifecycle.State.RESUMED)

      scenario.recreate()

      scenario.onActivity { activity ->
        assertHasLauncherLayers(activity)
      }
    }
  }

  private fun assertHasLauncherLayers(activity: MainActivity) {
    val content = activity.findViewById<ViewGroup>(android.R.id.content)
    val root = content.getChildAt(0) as ViewGroup

    var hasCanvas = false
    var hasWidgetLayer = false
    for (index in 0 until root.childCount) {
      when (root.getChildAt(index)) {
        is GestureCanvasView -> hasCanvas = true
        is WidgetLayerView -> hasWidgetLayer = true
      }
    }

    assertTrue("Expected GestureCanvasView layer", hasCanvas)
    assertTrue("Expected WidgetLayerView layer", hasWidgetLayer)
  }
}
