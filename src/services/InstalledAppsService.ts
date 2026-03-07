/**
 * InstalledAppsService.ts
 *
 * Wraps react-native-launcher-kit's InstalledApps API with a module-level
 * cache so that repeated reads do not trigger redundant native calls.
 * Provides filtering utilities used by search/drawer components.
 */

type LauncherKitModule = {
  InstalledApps: {
    getSortedApps: () => Promise<AppDetail[]>;
  };
};

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** Details for a single installed application, mirroring react-native-launcher-kit. */
export interface AppDetail {
  label: string;
  packageName: string;
  /**
   * App icon – may be a raw base64 string, a full `data:image/...;base64,` URI,
   * or absent for some system apps.  Use `getIconUri()` to normalise.
   */
  icon?: string;
  version?: string;
  accentColor?: string;
}

// ---------------------------------------------------------------------------
// Module-level cache
// ---------------------------------------------------------------------------

let cachedApps: AppDetail[] | null = null;
let launcherKitModule: LauncherKitModule | null | undefined;

function getLauncherKitModule(): LauncherKitModule | null {
  if (launcherKitModule !== undefined) {
    return launcherKitModule;
  }

  try {
    const mod = require('react-native-launcher-kit') as Partial<LauncherKitModule>;
    launcherKitModule = mod?.InstalledApps?.getSortedApps ? (mod as LauncherKitModule) : null;
  } catch {
    launcherKitModule = null;
  }

  return launcherKitModule;
}

function buildUnavailableMessage(): string {
  return [
    'Installed apps are unavailable in the current runtime.',
    'Use a native Android build instead of Expo Go:',
    '1. npm run android',
    '2. npx expo start --dev-client',
  ].join('\n');
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Returns the list of installed apps, sorted by label.
 *
 * Uses a module-level cache to avoid redundant native calls.  Pass
 * `forceRefresh = true` to bypass the cache and fetch a fresh list.
 */
export async function getInstalledApps(forceRefresh?: boolean): Promise<AppDetail[]> {
  if (cachedApps !== null && !forceRefresh) {
    return cachedApps;
  }

  const launcherKit = getLauncherKitModule();
  if (!launcherKit) {
    throw new Error(buildUnavailableMessage());
  }

  try {
    const apps = await launcherKit.InstalledApps.getSortedApps();
    cachedApps = apps;
    return cachedApps;
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    throw new Error(`Failed to load installed apps. ${message}`);
  }
}

/**
 * Filters `apps` by `query` against both `label` and `packageName` fields
 * (case-insensitive).  Returns the full list when `query` is empty.
 */
export function filterApps(apps: AppDetail[], query: string): AppDetail[] {
  if (query.trim() === '') return apps;

  const lower = query.toLowerCase();
  return apps.filter(
    (app) =>
      app.label.toLowerCase().includes(lower) ||
      app.packageName.toLowerCase().includes(lower),
  );
}

/**
 * Converts an app's raw icon value into a URI suitable for `<Image source={{ uri }}>`.
 *
 * Handles three cases from react-native-launcher-kit:
 *  - Already a data URI (`data:image/...;base64,...`) → returned as-is
 *  - Raw base64 string → prefixed with `data:image/png;base64,`
 *  - Absent / empty → returns `null` (caller should show a placeholder)
 */
export function getIconUri(icon: string | undefined | null): string | null {
  if (!icon) return null;
  if (icon.startsWith('data:')) return icon;
  return `data:image/png;base64,${icon}`;
}

/**
 * Forces a cache refresh and returns the updated list of installed apps.
 * Convenience wrapper around `getInstalledApps(true)`.
 */
export async function refreshInstalledApps(): Promise<AppDetail[]> {
  return getInstalledApps(true);
}
