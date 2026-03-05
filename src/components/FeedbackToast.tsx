/**
 * FeedbackToast.tsx
 *
 * Ephemeral full-width overlay that slides in from the top of the screen,
 * displays a short message, then automatically fades out.
 */

import React, { useEffect, useRef } from 'react';
import { Animated, StyleSheet, Text } from 'react-native';
export type ToastKind = 'success' | 'info' | 'warning' | 'error';

interface Props {
  message: string;
  kind?: ToastKind;
  /** Duration in ms before the toast dismisses itself.  Default: 1800. */
  duration?: number;
  onDismiss?: () => void;
}

const KIND_COLORS: Record<ToastKind, string> = {
  success: '#00C896',
  info: '#00AAFF',
  warning: '#FFAA00',
  error: '#FF4455',
};

export const FeedbackToast: React.FC<Props> = ({
  message,
  kind = 'info',
  duration = 1800,
  onDismiss,
}) => {
  const opacity = useRef(new Animated.Value(0)).current;
  // Store callback in a ref so animation sequence doesn't re-start if the
  // parent re-renders and passes a new callback identity.
  const onDismissRef = useRef(onDismiss);
  onDismissRef.current = onDismiss;

  useEffect(() => {
    Animated.sequence([
      Animated.timing(opacity, { toValue: 1, duration: 150, useNativeDriver: true }),
      Animated.delay(duration),
      Animated.timing(opacity, { toValue: 0, duration: 300, useNativeDriver: true }),
    ]).start(() => onDismissRef.current?.());
  }, [opacity, duration]);

  return (
    <Animated.View style={[styles.container, { opacity, borderColor: KIND_COLORS[kind] }]}>
      <Text style={[styles.text, { color: KIND_COLORS[kind] }]}>{message}</Text>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 48,
    left: 24,
    right: 24,
    backgroundColor: 'rgba(0,0,0,0.85)',
    borderWidth: 1,
    borderRadius: 10,
    paddingVertical: 10,
    paddingHorizontal: 16,
    zIndex: 100,
  },
  text: {
    fontSize: 15,
    fontWeight: '600',
    textAlign: 'center',
  },
});
