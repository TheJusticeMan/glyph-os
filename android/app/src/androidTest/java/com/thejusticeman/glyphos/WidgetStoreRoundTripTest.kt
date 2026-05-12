package com.thejusticeman.glyphos

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetStoreRoundTripTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    context.getSharedPreferences("glyph_os_native_store", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @After
  fun tearDown() {
    context.getSharedPreferences("glyph_os_native_store", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun saveAndLoadPlacements_roundTrips() {
    val store = WidgetStore(context)
    val placements = listOf(
      WidgetPlacement(
        appWidgetId = 101,
        provider = "com.example.widgets/.WeatherWidget",
        x = 24,
        y = 80,
        width = 420,
        height = 168,
        spanX = 2,
        spanY = 1,
        minWidth = 360,
        minHeight = 120,
        configured = true,
        lastUpdated = 123456789L,
      ),
      WidgetPlacement(
        appWidgetId = 202,
        provider = "com.example.widgets/.ClockWidget",
        x = 18,
        y = 320,
        width = 260,
        height = 260,
        spanX = 1,
        spanY = 1,
        minWidth = 240,
        minHeight = 240,
        configured = false,
        lastUpdated = 123456999L,
      ),
    )

    store.savePlacements(placements)

    assertEquals(placements, store.loadPlacements())
  }
}
