/**
 * App.tsx
 *
 * Root component of GlyphOS – an Android launcher driven by gesture recognition.
 *
 * Responsibilities:
 *  - Intercepts the hardware Back button so the launcher cannot be "backed out of".
 *  - Loads and persists the saved gesture library via GestureStorage.
 *  - Manages onboarding, gesture management, app-assignment, and feedback UI.
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BackHandler, StyleSheet } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { RNLauncherKitHelper } from 'react-native-launcher-kit';

import GestureCanvas from './src/components/GestureCanvas';
import GestureManagementScreen from './src/components/GestureManagementScreen';
import AssignAppModal from './src/components/AssignAppModal';
import OnboardingScreen from './src/components/OnboardingScreen';
import FeedbackOverlay, { FeedbackType } from './src/components/FeedbackOverlay';
import MergeGestureDialog, { MergeDecision } from './src/components/MergeGestureDialog';
import {
  SavedGesture,
  blendNormalizedPaths,
  calculateBestDirectionDifference,
  findBlendBoundsForDualMatch,
  rankSimilarApps,
} from './src/utils/GestureMatcher';
import { loadGestures, saveGestures, clearGestures } from './src/utils/GestureStorage';
import { AppDetail } from './src/services/InstalledAppsService';
import { Point } from './src/utils/GestureNormalizer';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const ONBOARDING_KEY = 'glyph_os_onboarding_done';
const TRAIL_EFFECT_KEY = 'glyph_os_trail_effect';
const LAUNCH_ON_CREATE_SHORTCUT_KEY = 'glyph_os_launch_on_create_shortcut';
const ALLOW_BACKWARD_GESTURES_KEY = 'glyph_os_allow_backward_gestures';
const FEEDBACK_DURATION_MS = 2000;

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export default function App() {
  // -------------------------------------------------------------------------
  // Onboarding
  // -------------------------------------------------------------------------
  const [hasSeenOnboarding, setHasSeenOnboarding] = useState<boolean | null>(null);

  useEffect(() => {
    AsyncStorage.getItem(ONBOARDING_KEY).then((value) => {
      setHasSeenOnboarding(value === 'true');
    });
  }, []);

  const handleOnboardingDone = useCallback(async () => {
    await AsyncStorage.setItem(ONBOARDING_KEY, 'true');
    setHasSeenOnboarding(true);
  }, []);

  // -------------------------------------------------------------------------
  // Trail effect setting – loaded from storage, persisted on toggle
  // -------------------------------------------------------------------------
  const [trailEffect, setTrailEffect] = useState(false);

  useEffect(() => {
    AsyncStorage.getItem(TRAIL_EFFECT_KEY).then((value) => {
      if (value === 'true') setTrailEffect(true);
    });
  }, []);

  const handleToggleTrailEffect = useCallback(async () => {
    setTrailEffect((prev) => {
      const next = !prev;
      AsyncStorage.setItem(TRAIL_EFFECT_KEY, String(next));
      return next;
    });
  }, []);

  // -------------------------------------------------------------------------
  // Launch-on-create setting – loaded from storage, persisted on toggle
  // -------------------------------------------------------------------------
  const [launchOnCreateShortcut, setLaunchOnCreateShortcut] = useState(true);

  useEffect(() => {
    AsyncStorage.getItem(LAUNCH_ON_CREATE_SHORTCUT_KEY).then((value) => {
      if (value === null) return;
      setLaunchOnCreateShortcut(value === 'true');
    });
  }, []);

  const handleToggleLaunchOnCreateShortcut = useCallback(async () => {
    setLaunchOnCreateShortcut((prev) => {
      const next = !prev;
      AsyncStorage.setItem(LAUNCH_ON_CREATE_SHORTCUT_KEY, String(next));
      return next;
    });
  }, []);

  // -------------------------------------------------------------------------
  // Backward-gesture setting – treat reverse draw direction as a match
  // -------------------------------------------------------------------------
  const [allowBackwardGestures, setAllowBackwardGestures] = useState(false);

  useEffect(() => {
    AsyncStorage.getItem(ALLOW_BACKWARD_GESTURES_KEY).then((value) => {
      if (value === null) return;
      setAllowBackwardGestures(value === 'true');
    });
  }, []);

  const handleToggleAllowBackwardGestures = useCallback(async () => {
    setAllowBackwardGestures((prev) => {
      const next = !prev;
      AsyncStorage.setItem(ALLOW_BACKWARD_GESTURES_KEY, String(next));
      return next;
    });
  }, []);

  // -------------------------------------------------------------------------
  // Saved gesture library – loaded from storage on mount, persisted on change
  // -------------------------------------------------------------------------
  const [savedGestures, setSavedGestures] = useState<SavedGesture[]>([]);
  const isLoadedRef = useRef(false);

  useEffect(() => {
    loadGestures().then((gestures) => {
      setSavedGestures(gestures);
      isLoadedRef.current = true;
    });
  }, []);

  useEffect(() => {
    if (!isLoadedRef.current) return;
    saveGestures(savedGestures);
  }, [savedGestures]);

  // -------------------------------------------------------------------------
  // Gesture management screen
  // -------------------------------------------------------------------------
  const [showManagement, setShowManagement] = useState(false);

  const handleUpdateGesture = useCallback((label: string, updates: Partial<SavedGesture>) => {
    setSavedGestures((prev) =>
      prev.map((g) => (g.label === label ? { ...g, ...updates } : g)),
    );
  }, []);

  const handleDeleteGesture = useCallback((label: string) => {
    setSavedGestures((prev) => prev.filter((g) => g.label !== label));
  }, []);

  const handleClearAll = useCallback(async () => {
    await clearGestures();
    setSavedGestures([]);
  }, []);

  // -------------------------------------------------------------------------
  // Feedback overlay
  // -------------------------------------------------------------------------
  const [feedback, setFeedback] = useState<{ message: string; type: FeedbackType } | null>(null);
  const feedbackTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showFeedback = useCallback((message: string, type: FeedbackType) => {
    if (feedbackTimerRef.current !== null) {
      clearTimeout(feedbackTimerRef.current);
    }
    setFeedback({ message, type });
    feedbackTimerRef.current = setTimeout(() => {
      setFeedback(null);
      feedbackTimerRef.current = null;
    }, FEEDBACK_DURATION_MS);
  }, []);

  // -------------------------------------------------------------------------
  // Assign-app modal flow
  // -------------------------------------------------------------------------
  const [pendingGesture, setPendingGesture] = useState<{
    label: string;
    normalizedPath: Point[];
  } | null>(null);

  const [pendingMerge, setPendingMerge] = useState<{
    app: AppDetail;
    targetGestureLabel: string;
    targetPath: Point[];
    incomingPath: Point[];
    minT: number;
    maxT: number;
  } | null>(null);

  const handleRequestAssignApp = useCallback((label: string, normalizedPath: Point[]) => {
    setPendingGesture({ label, normalizedPath });
    setPendingMerge(null);
  }, []);

  const maybeLaunchAfterAssign = useCallback(
    (app: AppDetail) => {
      if (!launchOnCreateShortcut) return;
      try {
        RNLauncherKitHelper.launchApplication(app.packageName);
      } catch {
        showFeedback('Assigned, but launch failed', 'error');
      }
    },
    [launchOnCreateShortcut, showFeedback],
  );

  const appendBinding = useCallback(
    (app: AppDetail, gestureToAppend: { label: string; normalizedPath: Point[] }) => {
      setSavedGestures((prev) => [
        ...prev,
        {
          label: gestureToAppend.label,
          packageName: app.packageName,
          normalizedPath: gestureToAppend.normalizedPath,
        },
      ]);
      showFeedback('App assigned!', 'saved');
      maybeLaunchAfterAssign(app);
      setPendingGesture(null);
      setPendingMerge(null);
    },
    [maybeLaunchAfterAssign, showFeedback],
  );

  const handleAssignApp = useCallback(
    (app: AppDetail) => {
      if (!pendingGesture) return;

      const existingForApp = savedGestures.filter(
        (gesture) =>
          gesture.packageName === app.packageName &&
          Array.isArray(gesture.normalizedPath) &&
          gesture.normalizedPath.length >= 2,
      );

      if (existingForApp.length === 0) {
        appendBinding(app, pendingGesture);
        return;
      }

      const mergeTarget = existingForApp.reduce((best, current) => {
        const currentDiff = calculateBestDirectionDifference(
          pendingGesture.normalizedPath,
          current.normalizedPath,
          allowBackwardGestures,
        );
        const bestDiff = calculateBestDirectionDifference(
          pendingGesture.normalizedPath,
          best.normalizedPath,
          allowBackwardGestures,
        );
        return currentDiff < bestDiff ? current : best;
      });

      const bounds = findBlendBoundsForDualMatch(
        mergeTarget.normalizedPath,
        pendingGesture.normalizedPath,
      );

      if (!bounds.canMerge) {
        appendBinding(app, pendingGesture);
        return;
      }

      setPendingMerge({
        app,
        targetGestureLabel: mergeTarget.label,
        targetPath: mergeTarget.normalizedPath,
        incomingPath: pendingGesture.normalizedPath,
        minT: bounds.minT,
        maxT: bounds.maxT,
      });
    },
    [allowBackwardGestures, appendBinding, pendingGesture, savedGestures],
  );

  const handleConfirmMerge = useCallback(
    (decision: MergeDecision, t: number) => {
      if (!pendingGesture || !pendingMerge) return;

      if (decision === 'create') {
        appendBinding(pendingMerge.app, pendingGesture);
        return;
      }

      const blendedPath = blendNormalizedPaths(pendingMerge.targetPath, pendingMerge.incomingPath, t);
      if (blendedPath.length < 2) {
        appendBinding(pendingMerge.app, pendingGesture);
        return;
      }

      setSavedGestures((prev) =>
        prev.map((gesture) =>
          gesture.label === pendingMerge.targetGestureLabel
            ? { ...gesture, normalizedPath: blendedPath }
            : gesture,
        ),
      );
      showFeedback('Gestures merged!', 'saved');
      maybeLaunchAfterAssign(pendingMerge.app);
      setPendingMerge(null);
      setPendingGesture(null);
    },
    [appendBinding, maybeLaunchAfterAssign, pendingGesture, pendingMerge, showFeedback],
  );

  const handleCancelMerge = useCallback(() => {
    setPendingMerge(null);
  }, []);

  const handleCancelAssign = useCallback(() => {
    setPendingGesture(null);
    setPendingMerge(null);
  }, []);

  const prioritizedPackageNames = useMemo(() => {
    if (!pendingGesture) return [];
    return rankSimilarApps(
      pendingGesture.normalizedPath,
      savedGestures,
      5,
      { allowBackward: allowBackwardGestures },
    ).map((entry) => entry.packageName);
  }, [allowBackwardGestures, pendingGesture, savedGestures]);

  // -------------------------------------------------------------------------
  // Intercept hardware Back button – a launcher must never close
  // -------------------------------------------------------------------------
  useEffect(() => {
    const onBackPress = (): boolean => {
      // Returning true tells Android we have handled the event,
      // preventing the default "exit app" behaviour.
      return true;
    };

    const subscription = BackHandler.addEventListener('hardwareBackPress', onBackPress);
    return () => {
      subscription.remove();
    };
  }, []);

  // -------------------------------------------------------------------------
  // Render – onboarding gate
  // -------------------------------------------------------------------------
  if (hasSeenOnboarding === false) {
    return <OnboardingScreen onDone={handleOnboardingDone} />;
  }

  // -------------------------------------------------------------------------
  // Render – main launcher UI
  // -------------------------------------------------------------------------
  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.root}>
        <StatusBar style="light" hidden />

        <GestureCanvas
          savedGestures={savedGestures}
          onOpenManagement={() => setShowManagement(true)}
          onRequestAssignApp={handleRequestAssignApp}
          onFeedback={showFeedback}
          trailEffect={trailEffect}
          allowBackwardGestures={allowBackwardGestures}
        />

        {showManagement && (
          <GestureManagementScreen
            gestures={savedGestures}
            onUpdateGesture={handleUpdateGesture}
            onDeleteGesture={handleDeleteGesture}
            onClearAll={handleClearAll}
            onClose={() => setShowManagement(false)}
            trailEffect={trailEffect}
            onToggleTrailEffect={handleToggleTrailEffect}
            launchOnCreateShortcut={launchOnCreateShortcut}
            onToggleLaunchOnCreateShortcut={handleToggleLaunchOnCreateShortcut}
            allowBackwardGestures={allowBackwardGestures}
            onToggleAllowBackwardGestures={handleToggleAllowBackwardGestures}
          />
        )}

        <AssignAppModal
          visible={pendingGesture !== null && pendingMerge === null}
          onAssign={handleAssignApp}
          onCancel={handleCancelAssign}
          prioritizedPackageNames={prioritizedPackageNames}
        />

        <MergeGestureDialog
          visible={pendingMerge !== null}
          appLabel={pendingMerge?.app.label ?? pendingMerge?.app.packageName ?? 'App'}
          oldPath={pendingMerge?.targetPath ?? []}
          newPath={pendingMerge?.incomingPath ?? []}
          minT={pendingMerge?.minT ?? 0.5}
          maxT={pendingMerge?.maxT ?? 0.5}
          initialT={((pendingMerge?.minT ?? 0.5) + (pendingMerge?.maxT ?? 0.5)) / 2}
          onCancel={handleCancelMerge}
          onConfirm={handleConfirmMerge}
        />

        <FeedbackOverlay
          message={feedback?.message ?? null}
          type={feedback?.type ?? 'saved'}
        />

      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#000000',
  },
});
