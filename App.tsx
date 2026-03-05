/**
 * App.tsx
 *
 * Root component of GlyphOS – an Android launcher driven by gesture recognition.
 *
 * Responsibilities:
 *  - Intercepts the hardware Back button so the launcher cannot be "backed out of".
 *  - Manages persistent gesture library (AsyncStorage via useGestureStore).
 *  - Manages the installed-app index (via useInstalledApps).
 *  - Controls the active screen:
 *      'launcher'   – full-screen GestureCanvas (default)
 *      'onboarding' – first-run walkthrough
 *      'bind'       – AppPicker for assigning a gesture to an app
 *      'management' – GestureManager settings screen
 *  - Shows ephemeral FeedbackToast notifications for launch / new-gesture events.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { BackHandler, SafeAreaView, StyleSheet } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import AsyncStorage from '@react-native-async-storage/async-storage';

import GestureCanvas from './src/components/GestureCanvas';
import { AppPicker } from './src/components/AppPicker';
import { GestureManager } from './src/components/GestureManager';
import { Onboarding } from './src/components/Onboarding';
import { FeedbackToast } from './src/components/FeedbackToast';
import { useGestureStore } from './src/hooks/useGestureStore';
import { useInstalledApps } from './src/hooks/useInstalledApps';
import type { SavedGesture } from './src/utils/GestureMatcher';
import type { AppInfo } from './src/hooks/useInstalledApps';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const ONBOARDING_KEY = '@glyphos:onboarding_done';

// ---------------------------------------------------------------------------
// Screen type
// ---------------------------------------------------------------------------

type Screen = 'launcher' | 'onboarding' | 'bind' | 'management';

// ---------------------------------------------------------------------------
// Toast state
// ---------------------------------------------------------------------------

interface Toast {
  id: number;
  message: string;
  kind: 'success' | 'info' | 'warning' | 'error';
}

// ---------------------------------------------------------------------------
// App
// ---------------------------------------------------------------------------

export default function App() {
  const gestureStore = useGestureStore();
  const appsStore = useInstalledApps();

  const [screen, setScreen] = useState<Screen>('launcher');
  const [pendingGesture, setPendingGesture] = useState<SavedGesture | null>(null);
  const [toast, setToast] = useState<Toast | null>(null);
  const toastIdRef = useRef(0);

  // -------------------------------------------------------------------------
  // Onboarding check
  // -------------------------------------------------------------------------
  useEffect(() => {
    AsyncStorage.getItem(ONBOARDING_KEY).then((done) => {
      if (!done) setScreen('onboarding');
    });
  }, []);

  const handleOnboardingComplete = useCallback(() => {
    AsyncStorage.setItem(ONBOARDING_KEY, '1').catch((err) => {
      console.warn('[App] Failed to save onboarding completion:', err);
    });
    setScreen('launcher');
  }, []);

  // -------------------------------------------------------------------------
  // Intercept hardware Back button – a launcher must never close
  // -------------------------------------------------------------------------
  useEffect(() => {
    const onBackPress = (): boolean => {
      if (screen === 'management' || screen === 'bind') {
        setScreen('launcher');
        setPendingGesture(null);
        return true;
      }
      // On launcher / onboarding screens, block the Back button entirely
      return true;
    };

    BackHandler.addEventListener('hardwareBackPress', onBackPress);
    return () => BackHandler.removeEventListener('hardwareBackPress', onBackPress);
  }, [screen]);

  // -------------------------------------------------------------------------
  // Toast helper
  // -------------------------------------------------------------------------
  const showToast = useCallback((message: string, kind: Toast['kind'] = 'info') => {
    toastIdRef.current += 1;
    setToast({ id: toastIdRef.current, message, kind });
  }, []);

  // -------------------------------------------------------------------------
  // Gesture binding flow
  // -------------------------------------------------------------------------
  const handleBindGesture = useCallback(
    (gesture: SavedGesture) => {
      setPendingGesture(gesture);
      setScreen('bind');
    },
    []
  );

  const handlePickApp = useCallback(
    (app: AppInfo | null) => {
      if (app && pendingGesture) {
        const bound: SavedGesture = {
          ...pendingGesture,
          label: app.label,
          packageName: app.packageName,
        };
        gestureStore.addGesture(bound);
        showToast(`✓ "${app.label}" bound to gesture`, 'success');
      }
      // If the user cancelled, discard the pending gesture (do not save an unbound shape).
      setPendingGesture(null);
      setScreen('launcher');
    },
    [pendingGesture, gestureStore, showToast]
  );

  // -------------------------------------------------------------------------
  // Launch feedback
  // -------------------------------------------------------------------------
  const handleLaunchApp = useCallback(
    (label: string) => {
      showToast(`Launching ${label}…`, 'success');
    },
    [showToast]
  );

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------

  const renderScreen = () => {
    if (!gestureStore.isLoading && screen === 'onboarding') {
      return <Onboarding onComplete={handleOnboardingComplete} />;
    }

    if (screen === 'management') {
      return (
        <GestureManager
          gestureStore={gestureStore}
          appsStore={appsStore}
          onClose={() => setScreen('launcher')}
        />
      );
    }

    if (screen === 'bind') {
      return (
        <AppPicker
          apps={appsStore.apps}
          isLoading={appsStore.isLoading}
          onPick={handlePickApp}
        />
      );
    }

    // Default: launcher
    return (
      <GestureCanvas
        savedGestures={gestureStore.gestures}
        onBindGesture={handleBindGesture}
        onLaunchApp={handleLaunchApp}
        onOpenSettings={() => setScreen('management')}
      />
    );
  };

  return (
    <SafeAreaView style={styles.root}>
      <StatusBar style="light" hidden />
      {renderScreen()}
      {toast ? (
        <FeedbackToast
          key={toast.id}
          message={toast.message}
          kind={toast.kind}
          onDismiss={() => setToast(null)}
        />
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#000000',
  },
});

