# GlyphOS

Android launcher utilizing a 40-segment normalized gesture recognition system for app launching.

## Overview

GlyphOS replaces your traditional icon-grid launcher with a gesture canvas.
Draw a shape → the matched app opens. No icons, no distractions.

## Features

- **Gesture-only launcher** – Draw any shape on the screen to launch an app.
- **40-point normalization** – Strokes are resampled to 40 equidistant points and matched using both Euclidean distance and Cosine Similarity for robust recognition.
- **Persistent gesture library** – Gestures are saved to device storage (AsyncStorage, schema v1) and survive app restarts.
- **Gesture → App binding** – The first time an unrecognised gesture is drawn, an app picker appears so you can immediately assign the gesture to a launchable app.
- **Installed-app index** – Sorted list of all launchable apps, with search/filter, fetched via `react-native-launcher-kit`.
- **Gesture Manager** – Long-press anywhere on the canvas to open the settings screen where you can rename, reassign, delete, or clear gestures.
- **First-run onboarding** – Step-by-step walkthrough explaining the gesture flow, including how to set GlyphOS as the default launcher.
- **Feedback toasts** – Brief on-screen notifications for launch, new gesture saved, and binding events.

## Getting Started

### Prerequisites

- Node.js ≥ 16
- Android device or emulator (this app targets Android only)
- Expo CLI: `npm install -g expo-cli`

### Install & run

```bash
npm install
npx expo run:android
```

### Set as default launcher

1. Open the **Gesture Manager** (long-press on the canvas).
2. Tap **Set as Default Launcher** to open Android's launcher-selection settings.
3. Choose **GlyphOS** from the list.

After that, pressing the Home button on your device will bring up GlyphOS.

## Usage

| Action | Result |
|--------|--------|
| Draw a gesture | Launches the bound app, or opens App Picker for a new shape |
| Long-press (hold ~0.6 s) | Opens Gesture Manager |
| Back button | Blocked — a launcher must not close |

## Architecture

```
App.tsx                     – root; screen routing, gesture store, toast
  ├── GestureCanvas          – PanResponder stroke capture + recognition
  ├── AppPicker              – full-screen app search + selection
  ├── GestureManager         – settings / gesture CRUD
  ├── Onboarding             – first-run walkthrough
  └── FeedbackToast          – ephemeral status overlay

src/hooks/
  ├── useGestureStore        – AsyncStorage-backed gesture persistence
  └── useInstalledApps       – installed-app index via react-native-launcher-kit

src/utils/
  ├── GestureNormalizer      – resample raw points → 40 equidistant points
  └── GestureMatcher         – turning-angle signature + Euclidean/Cosine matching
```

## Testing

```bash
npx jest --no-coverage
```
