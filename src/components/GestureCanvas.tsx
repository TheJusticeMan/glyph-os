/**
 * GestureCanvas.tsx
 *
 * Full-screen drawing surface that:
 *  1. Captures raw touch points via PanResponder.
 *  2. Renders the stroke in real-time with react-native-svg.
 *     When `trailEffect` is enabled the stroke is drawn as 5 overlapping
 *     segments that fade and widen toward the tail (comet-trail style).
 *  3. On finger-release: normalises the stroke, attempts to match a saved
 *     gesture, and either launches the associated app or requests app assignment.
 *  4. Long-press (800 ms without movement) opens the management screen.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { PanResponder, PanResponderGestureState, StyleSheet, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { RNLauncherKitHelper } from 'react-native-launcher-kit';

import { Point, normalizeTo40Points } from '../utils/GestureNormalizer';
import { SavedGesture, matchGesture } from '../utils/GestureMatcher';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface GestureCanvasProps {
  /** Persisted gesture library shared with the parent (App). */
  savedGestures: SavedGesture[];
  /** Called when user triggers the escape hatch (long press) to open management screen. */
  onOpenManagement: () => void;
  /** Called when a new gesture needs an app assigned. gestureLabel is the temp label. */
  onRequestAssignApp: (gestureLabel: string, normalizedPath: Point[]) => void;
  /** Called with feedback message and type for the FeedbackOverlay. */
  onFeedback: (message: string, type: 'launching' | 'no_match' | 'saved' | 'error') => void;
  /**
   * When true the drawn line is rendered as a comet-trail: the tail fades out
   * and widens while the tip stays bright and thin.  Disable on older devices
   * to preserve performance (default: false).
   */
  trailEffect?: boolean;
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
// Trail rendering
// ---------------------------------------------------------------------------

/** Number of overlapping segments used to draw the comet-trail stroke. */
const TRAIL_SEGMENTS = 5;

/** Opacity of the oldest (tail) trail segment. */
const TRAIL_MIN_OPACITY = 0.15;
/** Opacity range from oldest to newest trail segment. */
const TRAIL_OPACITY_RANGE = 0.85;
/** Stroke-width of the oldest (tail) trail segment in pixels. */
const TRAIL_MAX_WIDTH = 7.5;
/** How many pixels narrower the tip is compared with the tail. */
const TRAIL_WIDTH_RANGE = 5;

interface TrailSegment {
  d: string;
  opacity: number;
  strokeWidth: number;
}

/**
 * Splits `points` into `TRAIL_SEGMENTS` overlapping slices ordered from
 * oldest (index 0) to newest (last index).  Each slice is assigned a
 * decreasing opacity and increasing stroke-width so the tail looks faded
 * and wide while the tip is sharp and thin.
 *
 * Returns an empty array when fewer than 2 points are available.
 */
function buildTrailSegments(points: Point[]): TrailSegment[] {
  if (points.length < 2) return [];

  const n = points.length;
  const result: TrailSegment[] = [];

  for (let seg = 0; seg < TRAIL_SEGMENTS; seg++) {
    // seg 0 = oldest portion, seg TRAIL_SEGMENTS-1 = newest portion
    const startIdx = Math.floor((seg * (n - 1)) / TRAIL_SEGMENTS);
    const endIdx = Math.min(n - 1, Math.floor(((seg + 1) * (n - 1)) / TRAIL_SEGMENTS));
    if (endIdx <= startIdx) continue;

    const slice = points.slice(startIdx, endIdx + 1);
    if (slice.length < 2) continue;

    const t = seg / (TRAIL_SEGMENTS - 1); // 0 (oldest) → 1 (newest)
    result.push({
      d: buildPathD(slice),
      opacity: TRAIL_MIN_OPACITY + t * TRAIL_OPACITY_RANGE,
      strokeWidth: TRAIL_MAX_WIDTH - t * TRAIL_WIDTH_RANGE,
    });
  }

  return result;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

const GestureCanvas: React.FC<GestureCanvasProps> = ({
  savedGestures,
  onOpenManagement,
  onRequestAssignApp,
  onFeedback,
  trailEffect = false,
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
          onRequestAssignApp(gesture.label, gesture.normalizedPath);
          onFeedback('Assign an app to this gesture', 'no_match');
        }
      } else {
        // No match – create a new gesture entry and ask user to assign an app
        const label = `gesture_${Date.now()}`;
        console.log(`New gesture detected: ${label}`);
        onRequestAssignApp(label, normalizedPoints);
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
          const totalMovement = Math.hypot(gestureState.dx, gestureState.dy);
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

  // When the trail effect is on we derive the visual from rawPointsRef
  // (already updated before setPathD fires) so each re-render reflects the
  // latest stroke.
  const trailSegments = trailEffect ? buildTrailSegments(rawPointsRef.current) : null;

  // Build the SVG stroke content once so the JSX below stays readable.
  let strokeContent: React.ReactNode = null;
  if (trailSegments) {
    strokeContent = trailSegments.map((seg, i) => (
      <Path
        key={i}
        d={seg.d}
        stroke="#00FFCC"
        strokeWidth={seg.strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
        opacity={seg.opacity}
      />
    ));
  } else if (pathD) {
    strokeContent = (
      <Path
        d={pathD}
        stroke="#00FFCC"
        strokeWidth={3}
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
    );
  }

  return (
    <View style={styles.container} {...panResponder.panHandlers}>
      <Svg style={StyleSheet.absoluteFill}>{strokeContent}</Svg>
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
