# GlyphOS

GlyphOS is a native Android Kotlin launcher built around gestures and a dynamic home canvas. Draw gestures to open apps, swipe up to open the app list, draw a sideways line to open Google, pinch to hide or reveal home icons, and long-press the home screen to edit the launcher surface.

The active app lives in `android/`. The older Expo/React Native version has been archived in `legacy/react-native/`.

## Features

- Gesture launcher: draw a saved gesture to open an app or special function.
- Adaptive gestures: each successful gesture use shifts the saved path 5% toward the stroke you just drew.
- Default app list gesture: swipe up to open the app picker by default.
- Default Google gesture: draw a sideways line to open the Google app.
- Assignable special functions: internal actions such as `Open app list` are assigned through the same picker as normal apps.
- Native app picker: full-screen searchable app list with keyboard support and package-name fallback search.
- Dynamic home icons: launch frequency grows app icons, and the layout uses repulsion so icons spread around each other.
- Pinch visibility: two-finger pinch scales home icons down to fully hidden and persists that scale.
- Edit mode: long-press the home screen, app icons, or widgets to edit the launcher surface.
- Edit-mode settings buttons: open Settings, the Gesture Library, or the widget picker while edit mode is active.
- Widget resize handles: focused widgets show corner handles in edit mode and can resize from any corner.
- Trash drop target: dragging shows a trash target; drop widgets there to remove them, or app icons there to reset their launch count.
- Widget picker: add widgets from an in-app picker grouped by app, with previews when available.
- Native Android implementation: Kotlin Activity and custom Views, with no React Native runtime in the active app.

## Requirements

- Linux, macOS, or Windows with Android tooling installed.
- Android SDK installed, usually at `$HOME/Android/Sdk`.
- JDK 17.
- A connected Android device or emulator with USB debugging enabled.

The current Android configuration uses:

- Android Gradle Plugin `8.13.0`
- Kotlin `2.1.20`
- Compile SDK `36`
- Target SDK `36`
- Minimum SDK `24`
- Java/Kotlin target `17`

## Build

From the repository root:

```bash
ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk} \
ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-$HOME/Android/Sdk} \
PATH=$HOME/Android/Sdk/platform-tools:$PATH \
./android/gradlew -p android :app:assembleDebug
```

The debug APK is written to:

```txt
android/app/build/outputs/apk/debug/app-debug.apk
```

## Install And Launch

```bash
ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk} \
ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-$HOME/Android/Sdk} \
PATH=$HOME/Android/Sdk/platform-tools:$PATH \
./android/gradlew -p android :app:installDebug && \
adb shell am start -n com.thejusticeman.glyphos/.MainActivity
```

To use GlyphOS as the actual launcher, choose it as the default Home app in Android settings.

## Test

```bash
ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk} \
ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-$HOME/Android/Sdk} \
PATH=$HOME/Android/Sdk/platform-tools:$PATH \
./android/gradlew -p android :app:testDebugUnitTest
```

Current unit tests cover gesture math and the home icon layout engine.

## How To Use

Swipe up on the home screen to open the app list.

Draw a sideways line on the home screen to open the Google app.

Draw any other gesture to assign it to an app or special function. Once assigned, drawing the gesture again launches that target.

Tap a home icon to open that app. Launching an app through GlyphOS increments its launch count, which affects icon size on the home canvas.

Pinch with two fingers to scale home icons. A strong inward pinch can hide them completely; pinch outward to bring them back.

Long-press the home screen to enter edit mode. Long-pressing an app icon enters edit mode and starts dragging it; the dragged icon updates its anchor when released, while the other icons float around and settle through the repulsion layout.

In edit mode, use the bottom buttons to open Settings, the Gesture Library, or the widget picker. Settings contains global launcher controls and a `Gestures` row for the same gesture management screen.

Tap or long-press a widget in edit mode to focus it. Focused widgets show corner handles; drag any corner handle to resize the widget from that corner.

While dragging an app icon or widget, a trash target appears above the edit buttons. Dropping a widget on it removes that widget. Dropping an app icon on it resets that app's launch count, which lets the icon shrink back toward the default size.

## Project Layout

```txt
android/                         Native Android app
android/app/src/main/java/com/thejusticeman/glyphos/
  MainActivity.kt                Launcher activity, dialogs, settings flow, launch routing
  GestureCanvasView.kt           Full-screen home canvas, gesture input, icon drawing, pinch/edit handling
  GestureMath.kt                 Gesture normalization, matching, blending, ranking
  GestureStore.kt                Saved gesture persistence
  AppSettings.kt                 User settings persistence
  InstalledApps.kt               Installed-app discovery, filtering, and launching
  LauncherIconLayout.kt          Home icon sizing, anchors, repulsion, reset slots
  LaunchUsageStore.kt            Per-package launch counts
  SpecialFunctions.kt            Registry for internal assignable actions
  AppListAdapter.kt              Native app picker rows
docs/                            Technical notes and math docs
legacy/react-native/             Archived Expo/React Native implementation
```

## Documentation

- [docs/gesture-math-and-native-android.md](docs/gesture-math-and-native-android.md) records the exact gesture normalization and matching math.
- [docs/home-icon-layout.md](docs/home-icon-layout.md) explains home icon sizing, anchors, repulsion, edit mode, reset behavior, and tuning constants.

## Notes

- The active app does not require `npm install` or Expo commands.
- `node_modules/` may still exist from the archived React Native history, but it is not part of the native Android build path.
- The debug build may use `android/app/debug.keystore` when present; otherwise Gradle uses its normal debug signing behavior.
