# GlyphOS Codebase Guide

This guide maps the tracked source code, tests, Android resources, build configuration, and archived React Native implementation. The active launcher is the native Android application under `android/`; `legacy/react-native/` is a historical implementation and is not part of the current Android runtime.

For deeper algorithm explanations, see [gesture-math-and-native-android.md](gesture-math-and-native-android.md) and [home-icon-layout.md](home-icon-layout.md).

## Runtime architecture

The native app is a single Android `Activity` with two full-screen custom views layered over a transparent home surface:

- `MainActivity` initializes persistence and app discovery, builds the view hierarchy, and wires callbacks between the canvas, widgets, dialogs, and stores.
- `GestureCanvasView` receives touch input and draws the gesture trail, home icons, and edit overlays. It calls `MainActivity` through callbacks rather than owning persistence or app-launch behavior.
- `WidgetLayerView` hosts and positions Android `AppWidgetHostView` instances above the canvas.
- Small Kotlin extension files group feature logic around `MainActivity` (gesture handling, icon layout, app lists, edit controls, and widget management).
- Stores use Android `SharedPreferences` and JSON for gestures, anchors, widgets, settings, launch counts, and installed-app metadata.

### Main interaction flows

**Gesture recognition:** raw touch points in `GestureCanvasView` are normalized by `GestureMath.normalizeTo40Points()`. `GestureExt.handleGestureComplete()` matches the path against persisted bindings. A recognized app or special function runs; an unmatched path starts the assignment flow. Successful launches adapt the saved path and save it. The matching, blending, ranking, and normalization algorithms are detailed in the gesture math document.

**Gesture obstacle and home icons:** the canvas reports a moving gesture ghost while drawing. `HomeIconExt.gestureGhostNodes()` includes that point in a layout solve, so icons can temporarily avoid it. The current implementation also seeds solves from prior displayed positions, while the ghost itself is not a persisted anchor. The restoration behavior is tracked in [issue #6](https://github.com/TheJusticeMan/glyph-os/issues/6): icons should return to their pre-stroke layout after the temporary obstacle disappears.

**Home icon layout:** `HomeIconExt.updateLauncherIcons()` selects the visible apps and calls `LauncherIconLayout.build()` with launch counts, previous positions, persisted user anchors, active drag pins, and temporary gesture/widget obstacles. The solver applies separation, anchor pull, and edge clamping. `GestureCanvasView` animates the resulting target nodes. Dragging an icon updates temporary drag state; releasing it stores its user anchor unless dropped on the reset target. See [home-icon-layout.md](home-icon-layout.md) for positioning inputs and solver order.

**Widgets:** the widget picker starts the Android bind/configure flow. `WidgetExt` creates placements and host views, `WidgetLayerView` handles display and edit interactions, and `WidgetStore` persists placement geometry. `MainActivity` owns the `AppWidgetHost` lifecycle and restores widgets when the activity starts.

## Native Android source files

All paths below are relative to `android/app/src/main/java/com/thejusticeman/glyphos/`.

| File                     | Responsibility                                                                                                                                                                                                                                       |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MainActivity.kt`        | Launcher activity and composition root. Loads stores and app metadata, builds the canvas/widget/control layers, wires feature callbacks, handles widget activity results and host lifecycle, and schedules widget restoration.                       |
| `MainApplication.kt`     | Android `Application` subclass declared as the app process entry point; it currently contains no additional initialization logic.                                                                                                                    |
| `GestureCanvasView.kt`   | Custom home canvas: touch-state machine for taps, strokes, long press, pinch and icon dragging; icon and label drawing; edit visuals; gesture ghost reporting; icon-target interpolation; optional fading trail particles.                           |
| `GestureMath.kt`         | Gesture data types and native algorithms: resampling to 40 points, angular comparison, forward/backward matching, match ranking, similar-target ranking, defaults, blending/adaptation, threshold checks, and merge bounds.                          |
| `GestureExt.kt`          | Activity-level gesture orchestration: recognize or assign a stroke, bind a target, execute apps/special actions, adapt matched gestures, show merge/launch dialogs, persist and seed default gestures, and synchronize gesture paths shown on icons. |
| `GestureStore.kt`        | Versioned JSON serialization and persistence for saved gestures; accepts the supported legacy array form while loading.                                                                                                                              |
| `GesturePreviewView.kt`  | Small native `View` that scales and draws a normalized path inside a preview box.                                                                                                                                                                    |
| `HomeIconExt.kt`         | Selects visible home apps; computes icon layout inputs; manages drag anchors, ghost nodes, icon launch-count reset, sequential reset-layout animation, and anchor persistence.                                                                       |
| `LauncherIconLayout.kt`  | Deterministic home-icon sizing and position solver. Creates automatic golden-angle anchors, applies fixed pins and soft anchors, resolves collisions against icon/ghost nodes, clamps to canvas, and computes reset slots.                           |
| `HomeIconAnchorStore.kt` | Versioned persistence for per-package home-icon coordinates.                                                                                                                                                                                         |
| `LaunchUsageStore.kt`    | Reads, increments, and resets per-package launch counts used for icon sizing and visibility priority.                                                                                                                                                |
| `InstalledApps.kt`       | Queries and caches launchable apps, loads icons lazily, monitors package changes, filters app searches, and starts an app by package name. Defines `AppDetail` and search result data.                                                               |
| `AppDataExt.kt`          | Activity helpers around the installed-app cache: refreshes it, builds app/special-function picker targets, filters and renders the app picker and gesture library, and routes a tapped launcher icon.                                                |
| `AppListAdapter.kt`      | `BaseAdapter` for native app-picker rows, including icon, label, optional package-name display, and recycled row views.                                                                                                                              |
| `AppSettings.kt`         | Typed accessors and defaults for onboarding, trail rendering, shortcut behavior, reverse gesture matching, picker behavior, and home-icon scale.                                                                                                     |
| `SpecialFunctions.kt`    | Registry and conversion of internal launcher actions into picker-compatible `AppDetail` targets. The current built-in action is opening the app list.                                                                                                |
| `EditModeExt.kt`         | Edit-mode controls, bottom settings bar, trash target modes/highlighting/hit-testing, onboarding and feedback dialogs, wallpaper chooser, and system home-app settings entry.                                                                        |
| `ThemeHelpers.kt`        | Theme-aware color and styling helpers used by native UI components.                                                                                                                                                                                  |
| `UiKitExt.kt`            | Reusable Android view-building and UI extension helpers (layout, dimensions, text, and selectable styling).                                                                                                                                          |
| `WidgetExt.kt`           | Activity helpers for widget discovery and picker UI, allocation/binding/configuration, placement creation, host-view refresh, option bundles, provider labels/previews, persistence coordination, and widget ID cleanup.                             |
| `WidgetLayerView.kt`     | Full-canvas widget container. Attaches host views, lays them out, supports edit-mode focus/drag/resize and snapping, and reports placement changes or commits.                                                                                       |
| `WidgetPlacement.kt`     | Data model for a widget's app-widget ID, provider, canvas position and size, minimum dimensions, and configuration status.                                                                                                                           |
| `WidgetStore.kt`         | Versioned JSON persistence and validation for widget placements.                                                                                                                                                                                     |

## Native Android tests

Paths are relative to `android/app/src/`.

| File                                                                            | What it verifies                                                                                                                                                                                      |
| ------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `test/java/com/thejusticeman/glyphos/GestureMathTest.kt`                        | Invalid and minimum-length normalization, angle scoring and strict threshold behavior, optional reverse matching, path blending/adaptation, default gestures, and similar-target ranking.             |
| `test/java/com/thejusticeman/glyphos/LauncherIconLayoutTest.kt`                 | Icon size curve, deterministic ordering/layout, automatic slots, previous-position seeding, user and fixed anchors, ghost repulsion, reset behavior, canvas bounds, and practical overlap separation. |
| `androidTest/java/com/thejusticeman/glyphos/WidgetStoreRoundTripTest.kt`        | Persistence round-trip for widget placements.                                                                                                                                                         |
| `androidTest/java/com/thejusticeman/glyphos/MainActivityWidgetLifecycleTest.kt` | Launcher canvas/widget layers remain available across the tested activity lifecycle transitions.                                                                                                      |

The existing native test suite does not currently assert that a gesture ghost's displacement is undone after the ghost clears; see [issue #6](https://github.com/TheJusticeMan/glyph-os/issues/6).

## Archived React Native implementation

All paths below are relative to `legacy/react-native/`. This directory is retained for historical reference; the root README identifies `android/` as the active app.

### Runtime and feature code

| File                                         | Responsibility                                                                                                                                                                                         |
| -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `App.tsx`                                    | Former Expo app root: loads settings and saved gestures, controls onboarding/management screens, assigns and merges gestures, handles persistence and feedback, and orders likely apps for assignment. |
| `index.ts`                                   | React Native registration entry point.                                                                                                                                                                 |
| `src/utils/GestureNormalizer.ts`             | Defines 2D points and arc-length resampling to the canonical 40-point gesture path.                                                                                                                    |
| `src/utils/GestureMatcher.ts`                | Gesture model and angular matcher; supports reverse direction, match/ranking, path blending, threshold checks, and merge-bound search.                                                                 |
| `src/utils/GestureStorage.ts`                | AsyncStorage adapter for versioned gesture data, validation, migration, save, load, and clear.                                                                                                         |
| `src/services/InstalledAppsService.ts`       | Wrapper around `react-native-launcher-kit` for installed-app retrieval, cache refresh, text filtering, and icon URI conversion.                                                                        |
| `src/hooks/useInstalledApps.ts`              | React hook exposing installed apps, loading/error state, and refresh operation.                                                                                                                        |
| `src/components/GestureCanvas.tsx`           | Former React Native gesture capture/drawing surface; normalizes and matches strokes and calls the root assignment/launch callbacks.                                                                    |
| `src/components/GestureManagementScreen.tsx` | Former gesture library and settings UI, including previews, reassignment, deletion, and clearing gestures.                                                                                             |
| `src/components/AssignAppModal.tsx`          | Searchable app assignment modal with prioritized matches and delayed search-field focus.                                                                                                               |
| `src/components/MergeGestureDialog.tsx`      | Merge-versus-create decision UI with bounded interpolation slider and path previews.                                                                                                                   |
| `src/components/FeedbackOverlay.tsx`         | Animated feedback pill for launch, no-match, saved, and error states.                                                                                                                                  |
| `src/components/OnboardingScreen.tsx`        | Multi-step onboarding presentation and completion callback.                                                                                                                                            |

### Archived React Native tests

| File                                      | What it verifies                                                                                                      |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `src/__tests__/GestureNormalizer.test.ts` | Resampling validity, point count, spacing, and non-linear paths.                                                      |
| `src/__tests__/GestureMatcher.test.ts`    | Angular match behavior, thresholds, direction handling, ranking, blending, and merge bounds.                          |
| `src/__tests__/GestureStorage.test.ts`    | AsyncStorage load/save, schema migration, validation, and clearing.                                                   |
| `src/__tests__/AppMergeFlow.test.tsx`     | App-level merge flow: accepting a merge updates the existing binding and does not leave a duplicate incoming binding. |

### Archived build, plugin, and CI files

- `package.json` and `package-lock.json`: archived npm scripts and pinned dependency graph.
- `app.json`, `eas.json`, `tsconfig.json`, `babel.config.js`, and `react-native.config.js`: Expo/build, TypeScript, Babel, and native-module configuration.
- `plugins/withLauncherKit.js`: Expo config plugin that amends the Android manifest for the launcher-kit integration.
- `.github/workflows/ci.yml`: archived CI workflow for the React Native project.
- `.github/workflows/release.yml`: archived release/build workflow.
- `patches/react-native-launcher-kit+2.1.1.patch`: package patch applied to the archived native module dependency.
- `README.md`: legacy-specific setup and usage notes.
- `assets/`: four archived PNG app/splash/icon images; these are binary resources, not source code.

## Android build and resources

### Build and platform configuration

- `android/build.gradle`, `android/settings.gradle`, and `android/gradle.properties`: root Gradle plugin/dependency repositories, project inclusion, and build JVM/Android options.
- `android/app/build.gradle`: Android application plugin, namespace/application identity, SDK versions, Kotlin/Java target, and test/build dependencies.
- `android/app/proguard-rules.pro`: app-specific code-shrinker keep rules.
- `android/gradle/wrapper/gradle-wrapper.properties`: pinned Gradle distribution. `android/gradlew` and `android/gradlew.bat` are the Unix/Windows wrapper launch scripts; `gradle-wrapper.jar` is the binary wrapper bootstrap.
- `android/gradle.properties`: Android Gradle build options.
- `android/app/src/main/AndroidManifest.xml`: package declarations, launcher activity/home intent, app metadata, and Android component configuration.
- `.gitignore` and `android/.gitignore`: ignored build/local files at the repository and Android-project levels.

### Android XML resources

- `res/values/strings.xml`: app name and user-visible string resources.
- `res/values/styles.xml`: application theme/style declarations.
- `res/values/colors.xml` and `res/values-night/colors.xml`: light/default and night-mode colors.
- `res/drawable/ic_chevron_right_24.xml` and `ic_delete_24.xml`: navigation and delete vector icons.
- `res/drawable/ic_lucide_image.xml`, `ic_lucide_layout_grid.xml`, `ic_lucide_pen_tool.xml`, and `ic_lucide_settings.xml`: vector controls used by the launcher UI.
- `res/drawable/ic_launcher_background.xml`: adaptive launcher background drawable.
- `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`: adaptive launcher icon declarations.
- `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/`: raster launcher icon and foreground variants for Android density buckets. These WebP files are binary artwork.

## Reading and testing the project

Start with the root [README.md](../README.md), then the two algorithm/layout notes linked above. Native app code and tests live under `android/app/src/`; archived Expo code and its separate tests live under `legacy/react-native/`.

The root README documents the Android SDK/JDK requirements and canonical commands for debug assembly, installation, and native unit tests. The archived React Native dependency files are not prerequisites for building the current Android launcher.
