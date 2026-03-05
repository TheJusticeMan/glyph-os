/**
 * GestureCanvas.tsx
 *
 * Full-screen drawing surface that:
 *  1. Captures raw touch points via PanResponder.
 *  2. Renders the stroke in real-time with react-native-svg.
 *  3. On finger-release: normalises the stroke, attempts to match a saved
 *     gesture, and either launches the associated app or saves the new gesture.
 */

import React, { useCallback, useRef, useState } from 'react';
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
  /** Called when a new, unrecognised gesture should be persisted. */
  onSaveGesture: (gesture: SavedGesture) => void;
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

const GestureCanvas: React.FC<GestureCanvasProps> = ({ savedGestures, onSaveGesture }) => {
  const rawPointsRef = useRef<Point[]>([]);
  const [pathD, setPathD] = useState<string>('');

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
          console.log(`Launching app: ${gesture.packageName}`);
          RNLauncherKitHelper.launchApplication(gesture.packageName);
        } else {
          // Gesture label-only match: no app is mapped yet, treat as unrecognised
          console.log(`Gesture "${gesture.label}" has no app mapped. Saving as new gesture.`);
          const signature = buildSignature(normalizedPoints);
          const newGesture: SavedGesture = {
            label: `gesture_${Date.now()}`,
            signature,
          };
          onSaveGesture(newGesture);
        }
      } else {
        // No match – persist the new gesture signature
        const signature = buildSignature(normalizedPoints);
        const newGesture: SavedGesture = {
          label: `gesture_${Date.now()}`,
          signature,
        };
        console.log(`Saving new gesture: ${newGesture.label}`);
        onSaveGesture(newGesture);
      }
    },
    [savedGestures, onSaveGesture]
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
      },

      onPanResponderMove: (evt, _gestureState: PanResponderGestureState) => {
        const { locationX, locationY } = evt.nativeEvent;
        rawPointsRef.current.push({ x: locationX, y: locationY });
        setPathD(buildPathD(rawPointsRef.current));
      },

      onPanResponderRelease: (_evt, _gestureState: PanResponderGestureState) => {
        handleGestureComplete(rawPointsRef.current);
        // Clear the canvas after a short delay so the user can see the path
        setTimeout(() => {
          rawPointsRef.current = [];
          setPathD('');
        }, 400);
      },

      onPanResponderTerminate: () => {
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
