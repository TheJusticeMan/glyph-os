/**
 * Onboarding.tsx
 *
 * First-run onboarding screen that walks the user through the core concepts:
 *  1. Draw a gesture.
 *  2. Assign it to an app.
 *  3. Draw it again to launch the app.
 *
 * Also guides them through setting GlyphOS as the default launcher.
 */

import React, { useState } from 'react';
import {
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { RNLauncherKitHelper } from 'react-native-launcher-kit';

// ---------------------------------------------------------------------------
// Step data
// ---------------------------------------------------------------------------

const STEPS = [
  {
    emoji: '👋',
    title: 'Welcome to GlyphOS',
    body:
      'GlyphOS is a gesture-only Android launcher.\n\n' +
      'There are no app icons or grids. Instead you draw a gesture on the screen and the matching app opens instantly.',
  },
  {
    emoji: '✏️',
    title: 'Draw a Gesture',
    body:
      'Swipe your finger across the screen to draw a shape.\n\n' +
      'Any shape works — a letter, a symbol, a curve. When you lift your finger, GlyphOS records the shape.',
  },
  {
    emoji: '📱',
    title: 'Bind to an App',
    body:
      'The first time you draw a new gesture, a list of your installed apps appears.\n\n' +
      'Pick the app you want that gesture to launch. The mapping is saved instantly.',
  },
  {
    emoji: '🚀',
    title: 'Launch Away',
    body:
      'Draw the same gesture again — GlyphOS recognises it and opens the app directly.\n\n' +
      'You can save as many gesture→app bindings as you like.',
  },
  {
    emoji: '⚙️',
    title: 'Manage Your Gestures',
    body:
      'Long-press anywhere on the canvas to open the Gesture Manager.\n\n' +
      'There you can rename, reassign, or delete individual gestures, or clear everything and start fresh.',
  },
  {
    emoji: '🏠',
    title: 'Set as Default Launcher',
    body:
      'For the best experience, set GlyphOS as your default launcher so it opens when you press the Home button.\n\n' +
      "Tap the button below and select GlyphOS from Android's list of available launchers.",
  },
];

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface Props {
  /** Called when the user finishes onboarding. */
  onComplete: () => void;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export const Onboarding: React.FC<Props> = ({ onComplete }) => {
  const [step, setStep] = useState(0);
  const current = STEPS[step];
  const isLast = step === STEPS.length - 1;

  const next = () => {
    if (isLast) {
      onComplete();
    } else {
      setStep((s) => s + 1);
    }
  };

  const handleSetDefault = () => {
    RNLauncherKitHelper.openSetDefaultLauncher().catch((err) => {
      console.warn('[Onboarding] Could not open default launcher settings:', err);
    });
  };

  return (
    <View style={styles.root}>
      {/* Progress dots */}
      <View style={styles.dots}>
        {STEPS.map((_, i) => (
          <View key={i} style={[styles.dot, i === step && styles.dotActive]} />
        ))}
      </View>

      {/* Content */}
      <ScrollView contentContainerStyle={styles.content} bounces={false}>
        <Text style={styles.emoji}>{current.emoji}</Text>
        <Text style={styles.title}>{current.title}</Text>
        <Text style={styles.body}>{current.body}</Text>
      </ScrollView>

      {/* Footer */}
      <View style={styles.footer}>
        {isLast ? (
          <TouchableOpacity style={styles.secondaryBtn} onPress={handleSetDefault}>
            <Text style={styles.secondaryBtnText}>Open Default Launcher Settings</Text>
          </TouchableOpacity>
        ) : null}

        <TouchableOpacity style={styles.primaryBtn} onPress={next}>
          <Text style={styles.primaryBtnText}>{isLast ? 'Get Started' : 'Next'}</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#000000',
  },
  dots: {
    flexDirection: 'row',
    justifyContent: 'center',
    paddingTop: 24,
    gap: 8,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#333',
  },
  dotActive: {
    backgroundColor: '#00FFCC',
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    paddingVertical: 40,
  },
  emoji: {
    fontSize: 72,
    marginBottom: 28,
  },
  title: {
    color: '#FFFFFF',
    fontSize: 26,
    fontWeight: '700',
    textAlign: 'center',
    marginBottom: 20,
  },
  body: {
    color: '#AAAAAA',
    fontSize: 16,
    lineHeight: 26,
    textAlign: 'center',
  },
  footer: {
    padding: 24,
    gap: 12,
  },
  primaryBtn: {
    backgroundColor: '#00FFCC',
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
  },
  primaryBtnText: {
    color: '#000000',
    fontSize: 17,
    fontWeight: '700',
  },
  secondaryBtn: {
    borderRadius: 14,
    paddingVertical: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#00FFCC',
  },
  secondaryBtnText: {
    color: '#00FFCC',
    fontSize: 15,
    fontWeight: '600',
  },
});
