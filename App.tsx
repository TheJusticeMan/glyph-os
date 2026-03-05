/**
 * App.tsx
 *
 * Root component of GlyphOS – an Android launcher driven by gesture recognition.
 *
 * Responsibilities:
 *  - Intercepts the hardware Back button so the launcher cannot be "backed out of".
 *  - Manages the in-memory library of saved gestures.
 *  - Renders the full-screen GestureCanvas.
 */

import React, { useEffect, useState } from 'react';
import { BackHandler, SafeAreaView, StyleSheet } from 'react-native';
import { StatusBar } from 'expo-status-bar';

import GestureCanvas from './src/components/GestureCanvas';
import { SavedGesture } from './src/utils/GestureMatcher';

export default function App() {
  // -------------------------------------------------------------------------
  // Saved gesture library (in-memory; replace with AsyncStorage for persistence)
  // -------------------------------------------------------------------------
  const [savedGestures, setSavedGestures] = useState<SavedGesture[]>([]);

  const handleSaveGesture = (gesture: SavedGesture) => {
    setSavedGestures((prev) => [...prev, gesture]);
  };

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
  // Render
  // -------------------------------------------------------------------------
  return (
    <SafeAreaView style={styles.root}>
      <StatusBar style="light" hidden />
      <GestureCanvas
        savedGestures={savedGestures}
        onSaveGesture={handleSaveGesture}
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
