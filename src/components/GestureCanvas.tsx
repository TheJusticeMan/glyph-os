/**
 * GestureCanvas.tsx
 *
 * Full-screen drawing surface that:
 *  1. Captures raw touch points via PanResponder.
 *  2. Renders the stroke in real-time with react-native-svg.
 *  3. On finger-release: normalises the stroke, attempts to match a saved
 *     gesture, and either launches the associated app or triggers the
 *     bind-gesture flow for unrecognised / unbound gestures.
 *  4. Detects a long-press (finger held for ≥600 ms) as an escape hatch to
 *     open the Gesture Manager.
 */

import React, { useCallback, useRef, useState } from 'react';
import { PanResponder, PanResponderGestureState, StyleSheet, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { RNLauncherKitHelper } from 'react-native-launcher-kit';

import { Point, normalizeTo40Points } from '../utils/GestureNormalizer';
import { SavedGesture, buildSignature, matchGesture } from '../utils/GestureMatcher';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** How long (ms) the user must hold a finger still to trigger the escape hatch. */
const LONG_PRESS_DURATION = 600;

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface GestureCanvasProps {
  /** Persisted gesture library shared with the parent (App). */
  savedGestures: SavedGesture[];
  /**
   * Called when a gesture is drawn that is either unrecognised or recognised
   * but not yet bound to an app.  The parent should show the bind-gesture UI.
   */
  onBindGesture: (gesture: SavedGesture) => void;
  /** Called when an app is successfully launched. */
  onLaunchApp?: (label: string) => void;
  /** Called when a gesture is drawn but no match is found (and it is recorded). */
  onNewGesture?: () => void;
  /** Called to open the gesture management / settings screen. */
  onOpenSettings: () => void;
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
  onBindGesture,
  onLaunchApp,
  onNewGesture,
  onOpenSettings,
}) => {
  const rawPointsRef = useRef<Point[]>([]);
  const [pathD, setPathD] = useState<string>('');

  // Long-press timer ref – cleared on move or release
  const longPressTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearLongPress = () => {
    if (longPressTimerRef.current !== null) {
      clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  };

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
          RNLauncherKitHelper.launchApplication(gesture.packageName);
          onLaunchApp?.(gesture.label);
        } else {
          // Recognised gesture but no app bound yet – re-prompt for binding
          onBindGesture({ ...gesture });
        }
      } else {
        // Unrecognised gesture – create a pending gesture and open bind flow
        const signature = buildSignature(normalizedPoints);
        const newGesture: SavedGesture = {
          label: `gesture_${Date.now()}`,
          signature,
        };
        onNewGesture?.();
        onBindGesture(newGesture);
      }
    },
    [savedGestures, onBindGesture, onLaunchApp, onNewGesture]
  );

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

        // Start long-press timer for escape hatch
        longPressTimerRef.current = setTimeout(() => {
          rawPointsRef.current = [];
          setPathD('');
          onOpenSettings();
        }, LONG_PRESS_DURATION);
      },

      onPanResponderMove: (evt, _gestureState: PanResponderGestureState) => {
        // Any movement cancels the long-press intent
        clearLongPress();
        const { locationX, locationY } = evt.nativeEvent;
        rawPointsRef.current.push({ x: locationX, y: locationY });
        setPathD(buildPathD(rawPointsRef.current));
      },

      onPanResponderRelease: (_evt, _gestureState: PanResponderGestureState) => {
        clearLongPress();
        handleGestureComplete(rawPointsRef.current);
        // Clear the canvas after a short delay so the user can see the path
        setTimeout(() => {
          rawPointsRef.current = [];
          setPathD('');
        }, 400);
      },

      onPanResponderTerminate: () => {
        clearLongPress();
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
