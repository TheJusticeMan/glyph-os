/**
 * Expo config plugin for react-native-launcher-kit.
 *
 * react-native-launcher-kit does not ship its own app.plugin.js, so this
 * local plugin handles the one Android setup step the library requires:
 * adding the QUERY_ALL_PACKAGES permission to AndroidManifest.xml (needed
 * on Android 11+ to enumerate installed apps).
 */
const { withAndroidManifest } = require('@expo/config-plugins');

/**
 * @param {import('@expo/config-plugins').ExpoConfig} config
 * @returns {import('@expo/config-plugins').ExpoConfig}
 */
function withLauncherKit(config) {
  return withAndroidManifest(config, (mod) => {
    const androidManifest = mod.modResults;
    const mainManifest = androidManifest.manifest;

    if (!mainManifest['uses-permission']) {
      mainManifest['uses-permission'] = [];
    }

    const PERMISSION = 'android.permission.QUERY_ALL_PACKAGES';
    const alreadyAdded = mainManifest['uses-permission'].some(
      (p) => p.$?.['android:name'] === PERMISSION,
    );

    if (!alreadyAdded) {
      mainManifest['uses-permission'].push({
        $: { 'android:name': PERMISSION },
      });
    }

    return mod;
  });
}

module.exports = withLauncherKit;
