/**
 * InstalledAppsService.ts
 *
 * Wraps react-native-launcher-kit's InstalledApps API with a module-level
 * cache so that repeated reads do not trigger redundant native calls.
 * Provides filtering utilities used by search/drawer components.
 */

import { InstalledApps } from 'react-native-launcher-kit';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** Details for a single installed application, mirroring react-native-launcher-kit. */
export interface AppDetail {
  label: string;
  packageName: string;
  /** Base64-encoded app icon. */
  icon: string;
  version?: string;
  accentColor?: string;
}

// ---------------------------------------------------------------------------
// Module-level cache
// ---------------------------------------------------------------------------

let cachedApps: AppDetail[] | null = null;

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

  try {
    const apps = await InstalledApps.getSortedApps();
    cachedApps = apps;
    return cachedApps;
  } catch (err) {
    // Native call can fail on first boot or when permissions are not yet
    // granted; return an empty list so the UI degrades gracefully.
    console.warn('[InstalledAppsService] getSortedApps failed:', err);
    return [];
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
 * Forces a cache refresh and returns the updated list of installed apps.
 * Convenience wrapper around `getInstalledApps(true)`.
 */
export async function refreshInstalledApps(): Promise<AppDetail[]> {
  return getInstalledApps(true);
}
