/**
 * GestureMatcher.ts
 *
 * Matches a 40-point normalized gesture path against saved gestures using the
 * segment-angle / directional-difference algorithm ported from the Obsidian
 * Mobile Plugin reference implementation (gesture-handler.ts).
 *
 * Algorithm overview:
 *  1. For each corresponding segment pair, compute the direction angle with
 *     Math.atan2 and take the absolute wrapped angular difference.
 *  2. Average the differences across all segments → `calculateDifference`.
 *  3. Accept the best match when its score is below ANGULAR_THRESHOLD (0.5 rad).
 */

import { Point, NUM_POINTS } from './GestureNormalizer';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface SavedGesture {
  /** Human-readable label, e.g. "circle" or the package name of the target app. */
  label: string;
  /** Optional Android package name to launch when this gesture is recognised. */
  packageName?: string;
  /** The normalized 40-point gesture path used for angular-difference matching. */
  normalizedPath: Point[];
  /**
   * @deprecated Legacy turning-angle signature kept for backward-compatibility
   * when loading old persisted data; not used for matching.
   */
  signature?: number[];
}

export interface MatchResult {
  gesture: SavedGesture;
  /** Average angular difference (radians) between corresponding segments. Lower is better. */
  angularDifference: number;
}

// ---------------------------------------------------------------------------
// Angular-difference matching (ported from gesture-handler.ts)
// ---------------------------------------------------------------------------

/**
 * Maximum average angular difference (in radians) that is still considered a
 * match.  Mirrors the `minDiff < 0.5` threshold in the reference implementation.
 */
export const ANGULAR_THRESHOLD = 0.5;

/**
 * Computes the average angular difference between corresponding segments of two
 * normalized paths.  Each segment's direction is compared using atan2; the
 * absolute angular difference is wrapped to [0, π] and averaged across all
 * N-1 segments.
 *
 * Lower return values indicate more similar gestures; returns Infinity when
 * either path has fewer than 2 points.
 */
export function calculateDifference(line1: Point[], line2: Point[]): number {
  const n = Math.min(line1.length, line2.length);
  if (n < 2) return Infinity;

  let totalDiff = 0;
  for (let i = 0; i < n - 1; i++) {
    const v1x = line1[i + 1].x - line1[i].x;
    const v1y = line1[i + 1].y - line1[i].y;
    const v2x = line2[i + 1].x - line2[i].x;
    const v2y = line2[i + 1].y - line2[i].y;

    const angle1 = Math.atan2(v1y, v1x);
    const angle2 = Math.atan2(v2y, v2x);

    let diff = Math.abs(angle1 - angle2);
    if (diff > Math.PI) {
      diff = 2 * Math.PI - diff;
    }
    totalDiff += diff;
  }
  return totalDiff / (n - 1);
}

/**
 * Finds the closest saved gesture for the given 40-point normalised input.
 *
 * Compares the input path against each saved gesture's `normalizedPath` using
 * average angular difference.  Returns the best match if its score is below
 * ANGULAR_THRESHOLD, otherwise null.  Gestures without a valid `normalizedPath`
 * are skipped (backward-compatibility with legacy signature-only entries).
 */
export function matchGesture(
  normalizedPoints: Point[],
  savedGestures: SavedGesture[]
): MatchResult | null {
  if (savedGestures.length === 0) return null;

  let best: MatchResult | null = null;
  let minDiff = Infinity;

  for (const gesture of savedGestures) {
    if (!gesture.normalizedPath || gesture.normalizedPath.length < 2) continue;

    const diff = calculateDifference(normalizedPoints, gesture.normalizedPath);
    if (diff < minDiff) {
      minDiff = diff;
      best = { gesture, angularDifference: diff };
    }
  }

  if (best && minDiff < ANGULAR_THRESHOLD) {
    return best;
  }
  return null;
}

// ---------------------------------------------------------------------------
// Legacy / utility functions retained for backward-compatibility
// ---------------------------------------------------------------------------

/** The number of turning angles derived from NUM_POINTS points is NUM_POINTS - 2. */
export const SIGNATURE_LENGTH = NUM_POINTS - 2;

/** @deprecated Use ANGULAR_THRESHOLD / calculateDifference with normalizedPath instead. */
export const EUCLIDEAN_THRESHOLD = 3.0;

/** @deprecated Use ANGULAR_THRESHOLD / calculateDifference with normalizedPath instead. */
export const COSINE_THRESHOLD = 0.92;

/**
 * Returns the signed turning angle (in radians) at point B of the triplet A→B→C.
 * Positive = left turn, Negative = right turn.
 */
function turningAngle(a: Point, b: Point, c: Point): number {
  const v1x = b.x - a.x;
  const v1y = b.y - a.y;
  const v2x = c.x - b.x;
  const v2y = c.y - b.y;

  const dot = v1x * v2x + v1y * v2y;
  const cross = v1x * v2y - v1y * v2x;

  return Math.atan2(cross, dot);
}

/**
 * Builds the turning-angle signature from 40 normalised points.
 * Returns an array of `SIGNATURE_LENGTH` (38) angles in radians.
 * @deprecated New gestures use normalizedPath with calculateDifference.
 */
export function buildSignature(points: Point[]): number[] {
  if (points.length !== NUM_POINTS) {
    throw new Error(
      `buildSignature expects exactly ${NUM_POINTS} points, got ${points.length}`
    );
  }

  const angles: number[] = [];
  for (let i = 0; i < points.length - 2; i++) {
    angles.push(turningAngle(points[i], points[i + 1], points[i + 2]));
  }
  return angles;
}

/**
 * Euclidean distance between two equal-length vectors.
 * @deprecated New gestures use calculateDifference on normalizedPath.
 */
export function euclideanDistance(a: number[], b: number[]): number {
  let sum = 0;
  for (let i = 0; i < a.length; i++) {
    const diff = a[i] - b[i];
    sum += diff * diff;
  }
  return Math.sqrt(sum);
}

/**
 * Cosine similarity between two equal-length vectors.
 * Range [-1, 1]; 1 means identical direction.
 * @deprecated New gestures use calculateDifference on normalizedPath.
 */
export function cosineSimilarity(a: number[], b: number[]): number {
  let dot = 0;
  let normA = 0;
  let normB = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    normA += a[i] * a[i];
    normB += b[i] * b[i];
  }
  // Both vectors are zero-magnitude – they are identical (e.g. a perfectly straight line)
  if (normA === 0 && normB === 0) return 1;
  const denom = Math.sqrt(normA) * Math.sqrt(normB);
  if (denom === 0) return 0;
  return dot / denom;
}
