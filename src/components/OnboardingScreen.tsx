/**
 * OnboardingScreen.tsx
 *
 * First-run onboarding experience for GlyphOS.  Walks the user through three
 * pages explaining gesture drawing, gesture creation, and gesture management,
 * before handing off to the main launcher via the `onDone` callback.
 */

import React, { useState } from 'react';
import {
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface OnboardingStep {
  icon: string;
  title: string;
  body: string;
}

interface OnboardingScreenProps {
  onDone: () => void;
}

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

const STEPS: OnboardingStep[] = [
  {
    icon: '✋',
    title: 'Welcome to GlyphOS',
    body: 'Draw gestures on the screen to launch apps. Your launcher, your shapes.',
  },
  {
    icon: '✏️',
    title: 'Create a Gesture',
    body: "Draw any shape. When no match is found, you'll be prompted to assign an app to it.",
  },
  {
    icon: '⚙️',
    title: 'Manage Gestures',
    body: 'Long press anywhere on the screen to open the gesture manager. Reassign apps or delete gestures there.',
  },
];

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

const OnboardingScreen: React.FC<OnboardingScreenProps> = ({ onDone }) => {
  const [currentIndex, setCurrentIndex] = useState<number>(0);

  const isLast = currentIndex === STEPS.length - 1;
  const step = STEPS[currentIndex];

  const handleNext = () => {
    if (isLast) {
      onDone();
    } else {
      setCurrentIndex((prev) => prev + 1);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.icon}>{step.icon}</Text>
        <Text style={styles.title}>{step.title}</Text>
        <Text style={styles.body}>{step.body}</Text>
      </View>

      <View style={styles.footer}>
        <View style={styles.dotsRow}>
          {STEPS.map((_, index) => (
            <View
              key={index}
              style={[styles.dot, index === currentIndex && styles.dotActive]}
            />
          ))}
        </View>

        <TouchableOpacity style={styles.button} onPress={handleNext} activeOpacity={0.8}>
          <Text style={styles.buttonText}>{isLast ? 'Get Started' : 'Next'}</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000000',
    paddingHorizontal: 32,
    paddingBottom: 48,
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: {
    fontSize: 80,
    marginBottom: 32,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#00FFCC',
    textAlign: 'center',
    marginBottom: 20,
  },
  body: {
    fontSize: 16,
    color: '#CCCCCC',
    textAlign: 'center',
    lineHeight: 24,
  },
  footer: {
    alignItems: 'center',
    gap: 28,
  },
  dotsRow: {
    flexDirection: 'row',
    gap: 8,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#333333',
  },
  dotActive: {
    backgroundColor: '#00FFCC',
    width: 20,
  },
  button: {
    width: '100%',
    paddingVertical: 16,
    borderRadius: 12,
    backgroundColor: '#00FFCC',
    alignItems: 'center',
  },
  buttonText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#000000',
  },
});

export default OnboardingScreen;
