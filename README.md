# GlyphOS

A gesture-only Android home screen replacement launcher — draw any shape to launch an app.

## Overview

GlyphOS is a gesture-based Android home screen replacement launcher. There are no icons and no grids — instead, you draw a shape on the screen and it launches the app you've assigned to that gesture.

## Features

- Gesture-based app launching (no icons, no grids)
- Draw a gesture → assign an app → launch it every time
- Persistent gesture library (survives restarts)
- Searchable app picker when assigning
- Gesture management screen (reassign, delete, clear all)
- First-run onboarding
- Visual feedback for all actions

## How to Run

### Prerequisites

- Node.js 18+
- Android device or emulator with USB debugging enabled
- Java 17 (for Android build)

### Setup

```bash
git clone https://github.com/TheJusticeMan/glyph-os.git
cd glyph-os
npm install
```

### Development

```bash
npm run android    # Build and run on connected Android device/emulator
npm start          # Start Expo dev server only
```

### Tests

```bash
npx jest --no-coverage
```

## How to Set as Default Launcher (Android)

1. Install the app on your device.
2. Press the home button.
3. Android will ask you to choose a launcher — select **GlyphOS** and tap "Always".
4. If not prompted: go to **Settings → Apps → Default apps → Home app** and select GlyphOS.

> **Note:** You may need a signed release APK for this on some devices. Expo's `expo run:android` installs a debug build that works on most devices.

## How to Create and Manage Gestures

### Creating a Gesture

1. On the main screen, draw any shape with your finger (e.g., a circle, a zigzag, a straight line).
2. If the shape is unrecognized, an **Assign App** panel appears.
3. Search for the app you want to launch and tap it.
4. The gesture is now saved. Draw the same shape again to launch the app.

### Managing Gestures

- **Long press** (hold for ~0.8 seconds) anywhere on the main screen to open the **Gesture Library**.
- From there you can:
  - **Reassign** a gesture to a different app.
  - **Delete** a gesture.
  - **Clear all** gestures (with confirmation).

## Architecture

| File | Responsibility |
|------|---------------|
| `App.tsx` | Root: state management, persistence, navigation between screens |
| `src/components/GestureCanvas.tsx` | Touch capture, stroke rendering, gesture recognition |
| `src/components/AssignAppModal.tsx` | App picker modal with search |
| `src/components/GestureManagementScreen.tsx` | Gesture library CRUD |
| `src/components/OnboardingScreen.tsx` | First-run tutorial |
| `src/components/FeedbackOverlay.tsx` | Toast-style feedback |
| `src/utils/GestureNormalizer.ts` | Resamples raw touch points to 40 equidistant points |
| `src/utils/GestureMatcher.ts` | Turning-angle signature + Euclidean/cosine matching |
| `src/utils/GestureStorage.ts` | AsyncStorage persistence with schema versioning |
| `src/services/InstalledAppsService.ts` | Fetches & caches installed apps list |
| `src/hooks/useInstalledApps.ts` | React hook wrapping the apps service |

## Known Limitations

- **Android only.** GlyphOS uses Android-specific APIs (`react-native-launcher-kit`) and cannot run as a launcher on iOS.
- **Gestures are sensitive to drawing style.** You need to draw the gesture in roughly the same direction and shape each time. The 40-point normalization helps, but very loose drawings may not match.
- **No rotation/scale invariance.** The turning-angle algorithm is scale-agnostic but not fully rotation-agnostic; try to draw gestures in a consistent orientation.
- **App icons are base64-encoded** and loaded from the OS; on some devices icon loading may be slow.
- **Setting as default launcher** may require a release build signed with your keystore on strict Android versions.
