/**
 * useInstalledApps.ts
 *
 * React hook that exposes the installed-apps list managed by
 * InstalledAppsService.  Handles loading state, error capture, and
 * on-demand refresh so components stay free of data-fetching logic.
 */

import { useCallback, useEffect, useState } from 'react';
import type { AppDetail } from '../services/InstalledAppsService';
import { getInstalledApps, refreshInstalledApps } from '../services/InstalledAppsService';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface UseInstalledAppsResult {
  apps: AppDetail[];
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * Loads the device's installed apps on mount and provides a `refresh`
 * callback that forces a cache-busting reload.
 *
 * @returns `{ apps, loading, error, refresh }`
 */
function useInstalledApps(): UseInstalledAppsResult {
  const [apps, setApps] = useState<AppDetail[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const loadApps = useCallback(async (forceRefresh: boolean) => {
    setLoading(true);
    setError(null);
    try {
      const result = forceRefresh
        ? await refreshInstalledApps()
        : await getInstalledApps();
      setApps(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadApps(false);
  }, [loadApps]);

  const refresh = useCallback(() => {
    loadApps(true);
  }, [loadApps]);

  return { apps, loading, error, refresh };
}

export default useInstalledApps;
