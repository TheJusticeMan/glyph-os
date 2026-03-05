/**
 * GestureCanvas.tsx
 *
 * Full-screen drawing surface that:
 *  1. Captures raw touch points via PanResponder.
 *  2. Renders the stroke in real-time with react-native-svg.
 *  3. On finger-release: normalises the stroke, attempts to match a saved
 *     gesture, and either launches the associated app or requests app assignment.
 *  4. Long-press (800 ms without movement) opens the management screen.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { PanResponder, PanResponderGestureState, StyleSheet, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { RNLauncherKitHelper } from 'react-native-launcher-kit';

import { Point, normalizeTo40Points } from '../utils/GestureNormalizer';
import { SavedGesture, buildSignature, matchGesture } from '../utils/GestureMatcher';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface GestureCanvasProps {
  /** Persisted gesture library shared with the parent (App). */
  savedGestures: SavedGesture[];
  /** Called when user triggers the escape hatch (long press) to open management screen. */
  onOpenManagement: () => void;
  /** Called when a new gesture needs an app assigned. gestureLabel is the temp label. */
  onRequestAssignApp: (gestureLabel: string, signature: number[]) => void;
  /** Called with feedback message and type for the FeedbackOverlay. */
  onFeedback: (message: string, type: 'launching' | 'no_match' | 'saved' | 'error') => void;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Converts a flat point array into an SVG path `d` attribute string. */
function buildPathD(points: Point[]): string {
  if (points.length === 0) return '';
  const [first, ...rest] = points;
  const move = `M ${first.x} ${first.y}`;
  const lines = rest.map((p) => `L ${p.x} ${p.y}`).join(' ');
  return `${move} ${lines}`;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

const GestureCanvas: React.FC<GestureCanvasProps> = ({
  savedGestures,
  onOpenManagement,
  onRequestAssignApp,
  onFeedback,
}) => {
  const rawPointsRef = useRef<Point[]>([]);
  const [pathD, setPathD] = useState<string>('');
  const longPressTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // -------------------------------------------------------------------------
  // Gesture completion handler
  // -------------------------------------------------------------------------
  const handleGestureComplete = useCallback(
    (rawPoints: Point[]) => {
      const normalizedPoints = normalizeTo40Points(rawPoints);

      if (!normalizedPoints) {
        // Gesture was too short (tap, not a stroke)
        return;
      }

      const match = matchGesture(normalizedPoints, savedGestures);

      if (match) {
        const { gesture } = match;
        if (gesture.packageName) {
          try {
            console.log(`Launching app: ${gesture.packageName}`);
            RNLauncherKitHelper.launchApplication(gesture.packageName);
            onFeedback(`🚀 Launching ${gesture.label}`, 'launching');
          } catch {
            onFeedback('Launch failed', 'error');
          }
        } else {
          // Gesture matched but no app mapped yet – ask user to assign one
          console.log(`Gesture "${gesture.label}" has no app mapped. Requesting assignment.`);
          onRequestAssignApp(gesture.label, gesture.signature);
          onFeedback('Assign an app to this gesture', 'no_match');
        }
      } else {
        // No match – create a new gesture entry and ask user to assign an app
        const signature = buildSignature(normalizedPoints);
        const label = `gesture_${Date.now()}`;
        console.log(`New gesture detected: ${label}`);
        onRequestAssignApp(label, signature);
        onFeedback('New gesture! Assign an app.', 'no_match');
      }
    },
    [savedGestures, onRequestAssignApp, onFeedback]
  );

  // Keep a stable ref so the PanResponder (created once) always calls the
  // latest version of the handler without needing to be recreated.
  const handleGestureCompleteRef = useRef(handleGestureComplete);
  useEffect(() => {
    handleGestureCompleteRef.current = handleGestureComplete;
  }, [handleGestureComplete]);

  const onOpenManagementRef = useRef(onOpenManagement);
  useEffect(() => {
    onOpenManagementRef.current = onOpenManagement;
  }, [onOpenManagement]);

  // -------------------------------------------------------------------------
  // PanResponder
  // -------------------------------------------------------------------------
  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,

      onPanResponderGrant: (evt) => {
        const { locationX, locationY } = evt.nativeEvent;
        rawPointsRef.current = [{ x: locationX, y: locationY }];
        setPathD(buildPathD(rawPointsRef.current));

        // Start long-press timer
        longPressTimerRef.current = setTimeout(() => {
          longPressTimerRef.current = null;
          rawPointsRef.current = [];
          setPathD('');
          onOpenManagementRef.current();
        }, 800);
      },

      onPanResponderMove: (evt, gestureState: PanResponderGestureState) => {
        // Cancel long-press if the finger has moved more than 10px
        if (longPressTimerRef.current !== null) {
          const totalMovement = Math.sqrt(
            gestureState.dx * gestureState.dx + gestureState.dy * gestureState.dy
          );
          if (totalMovement > 10) {
            clearTimeout(longPressTimerRef.current);
            longPressTimerRef.current = null;
          }
        }

        const { locationX, locationY } = evt.nativeEvent;
        rawPointsRef.current.push({ x: locationX, y: locationY });
        setPathD(buildPathD(rawPointsRef.current));
      },

      onPanResponderRelease: (_evt, _gestureState: PanResponderGestureState) => {
        if (longPressTimerRef.current !== null) {
          clearTimeout(longPressTimerRef.current);
          longPressTimerRef.current = null;
        }

        handleGestureCompleteRef.current(rawPointsRef.current);
        // Clear the canvas after a short delay so the user can see the path
        setTimeout(() => {
          rawPointsRef.current = [];
          setPathD('');
        }, 400);
      },

      onPanResponderTerminate: () => {
        if (longPressTimerRef.current !== null) {
          clearTimeout(longPressTimerRef.current);
          longPressTimerRef.current = null;
        }
        rawPointsRef.current = [];
        setPathD('');
      },
    })
  ).current;

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------
  return (
    <View style={styles.container} {...panResponder.panHandlers}>
      <Svg style={StyleSheet.absoluteFill}>
        {pathD ? (
          <Path
            d={pathD}
            stroke="#00FFCC"
            strokeWidth={3}
            strokeLinecap="round"
            strokeLinejoin="round"
            fill="none"
          />
        ) : null}
      </Svg>
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
  },
});

export default GestureCanvas;
