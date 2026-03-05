/**
 * useInstalledApps.ts
 *
 * React hook that loads and caches the list of launchable apps installed on
 * the device, using react-native-launcher-kit's InstalledApps API.
 *
 * The list is refreshed on mount and whenever `refresh()` is called.
 */

import { useCallback, useEffect, useState } from 'react';
import { InstalledApps } from 'react-native-launcher-kit';

export interface AppInfo {
  label: string;
  packageName: string;
  /** Base-64 encoded icon (may be an empty string on some devices). */
  icon: string;
}

export interface InstalledAppsStore {
  apps: AppInfo[];
  isLoading: boolean;
  /** Trigger a fresh fetch from the OS. */
  refresh: () => void;
}

export function useInstalledApps(): InstalledAppsStore {
  const [apps, setApps] = useState<AppInfo[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const refresh = useCallback(() => {
    setIsLoading(true);
    InstalledApps.getSortedApps()
      .then((raw) => {
        setApps(
          raw.map((a) => ({
            label: a.label,
            packageName: a.packageName,
            icon: a.icon ?? '',
          }))
        );
      })
      .catch((err) => {
        console.warn('[InstalledApps] Failed to load installed apps:', err);
        // Keep the previous list on error
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { apps, isLoading, refresh };
}
