/**
 * FeedbackOverlay.tsx
 *
 * Toast-like overlay that surfaces brief contextual feedback to the user
 * (e.g. app launching, no gesture match, gesture saved, error).  The
 * component fades in when a message is provided and renders nothing when
 * `message` is null.  `pointerEvents="none"` ensures it never blocks touches.
 */

import React, { useEffect, useRef } from 'react';
import { Animated, StyleSheet, Text } from 'react-native';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type FeedbackType = 'launching' | 'no_match' | 'saved' | 'error';

interface FeedbackOverlayProps {
  /** The message to display.  Pass null to hide the overlay. */
  message: string | null;
  type: FeedbackType;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const ICON_MAP: Record<FeedbackType, string> = {
  launching: '🚀',
  no_match: '✗',
  saved: '✓',
  error: '⚠',
};

interface PillStyle {
  backgroundColor: string;
  borderColor: string;
}

const PILL_STYLE_MAP: Record<FeedbackType, PillStyle> = {
  launching: { backgroundColor: '#003333', borderColor: '#00FFCC' },
  no_match: { backgroundColor: '#222222', borderColor: '#666666' },
  saved: { backgroundColor: '#003300', borderColor: '#00CC44' },
  error: { backgroundColor: '#330000', borderColor: '#CC0000' },
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

const FeedbackOverlay: React.FC<FeedbackOverlayProps> = ({ message, type }) => {
  const opacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (message !== null) {
      opacity.setValue(0);
      Animated.timing(opacity, {
        toValue: 1,
        duration: 200,
        useNativeDriver: true,
      }).start();
    }
  }, [message, opacity]);

  if (message === null) {
    return null;
  }

  const pillStyle = PILL_STYLE_MAP[type];
  const icon = ICON_MAP[type];

  return (
    <Animated.View
      pointerEvents="none"
      style={[styles.container, { opacity }]}
    >
      <Animated.View
        style={[
          styles.pill,
          {
            backgroundColor: pillStyle.backgroundColor,
            borderColor: pillStyle.borderColor,
          },
        ]}
      >
        <Text style={styles.icon}>{icon}</Text>
        <Text style={styles.text}>{message}</Text>
      </Animated.View>
    </Animated.View>
  );
};

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    bottom: 60,
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 32,
    borderWidth: 1,
    maxWidth: '80%',
  },
  icon: {
    fontSize: 14,
    marginRight: 8,
  },
  text: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
  },
});

export default FeedbackOverlay;
