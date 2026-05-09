# GlyphOS

GlyphOS is now a native Android Kotlin gesture launcher. Draw a gesture anywhere on the home screen to assign or launch an app; long press the screen to manage saved gestures and settings.

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

## Legacy React Native App

The previous Expo/React Native implementation has been moved to:

```txt
legacy/react-native/
```

That folder contains the old `App.tsx`, `src/`, Expo config, assets, patches, plugins, package files, and the original README.
