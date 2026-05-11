# GlyphOS

GlyphOS is now a native Android Kotlin gesture launcher. Draw a straight line to open the app list by default, or draw another gesture to assign or launch an app; long press the screen to manage saved gestures and settings.

Special functions are assignable from the same picker as normal apps. The first built-in special function is `Open app list`, and the native code keeps it in a small registry so more functions can be added later without changing the gesture matcher.

## Native Android Build

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

Run the native gesture math tests with:

```bash
ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk} \
ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-$HOME/Android/Sdk} \
PATH=$HOME/Android/Sdk/platform-tools:$PATH \
./android/gradlew -p android :app:testDebugUnitTest
```

Home screen icon positioning is documented in [docs/home-icon-layout.md](docs/home-icon-layout.md).

## Legacy React Native App

The previous Expo/React Native implementation has been moved to:

```txt
legacy/react-native/
```

That folder contains the old `App.tsx`, `src/`, Expo config, assets, patches, plugins, package files, and the original README.
