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

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { BackHandler, SafeAreaView, StyleSheet } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import AsyncStorage from '@react-native-async-storage/async-storage';

import GestureCanvas from './src/components/GestureCanvas';
import GestureManagementScreen from './src/components/GestureManagementScreen';
import AssignAppModal from './src/components/AssignAppModal';
import OnboardingScreen from './src/components/OnboardingScreen';
import FeedbackOverlay, { FeedbackType } from './src/components/FeedbackOverlay';
import { SavedGesture } from './src/utils/GestureMatcher';
import { loadGestures, saveGestures, clearGestures } from './src/utils/GestureStorage';
import { AppDetail } from './src/services/InstalledAppsService';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const ONBOARDING_KEY = 'glyph_os_onboarding_done';
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
    signature: number[];
  } | null>(null);

  const handleRequestAssignApp = useCallback((label: string, signature: number[]) => {
    setPendingGesture({ label, signature });
  }, []);

  const handleAssignApp = useCallback(
    (app: AppDetail) => {
      if (!pendingGesture) return;
      setSavedGestures((prev) => [
        ...prev,
        {
          label: pendingGesture.label,
          packageName: app.packageName,
          signature: pendingGesture.signature,
        },
      ]);
      showFeedback('App assigned!', 'saved');
      setPendingGesture(null);
    },
    [pendingGesture, showFeedback],
  );

  const handleCancelAssign = useCallback(() => {
    setPendingGesture(null);
  }, []);

  // -------------------------------------------------------------------------
  // Intercept hardware Back button – a launcher must never close
  // -------------------------------------------------------------------------
  useEffect(() => {
    const onBackPress = (): boolean => {
      // Returning true tells Android we have handled the event,
      // preventing the default "exit app" behaviour.
      return true;
    };

    BackHandler.addEventListener('hardwareBackPress', onBackPress);
    return () => BackHandler.removeEventListener('hardwareBackPress', onBackPress);
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
    <SafeAreaView style={styles.root}>
      <StatusBar style="light" hidden />

      <GestureCanvas
        savedGestures={savedGestures}
        onOpenManagement={() => setShowManagement(true)}
        onRequestAssignApp={handleRequestAssignApp}
        onFeedback={showFeedback}
      />

      {showManagement && (
        <GestureManagementScreen
          gestures={savedGestures}
          onUpdateGesture={handleUpdateGesture}
          onDeleteGesture={handleDeleteGesture}
          onClearAll={handleClearAll}
          onClose={() => setShowManagement(false)}
        />
      )}

      <AssignAppModal
        visible={pendingGesture !== null}
        onAssign={handleAssignApp}
        onCancel={handleCancelAssign}
      />

      <FeedbackOverlay
        message={feedback?.message ?? null}
        type={feedback?.type ?? 'saved'}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#000000',
  },
});
