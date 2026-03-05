/**
 * GestureMatcher.ts
 *
 * Takes 40 normalized points, builds a turning-angle (bend) signature, and
 * compares it against a list of saved gestures using both Euclidean distance
 * and Cosine Similarity so callers can choose the metric that suits them.
 */

import { Point, NUM_POINTS } from './GestureNormalizer';

/** The number of turning angles derived from NUM_POINTS points is NUM_POINTS - 2. */
const SIGNATURE_LENGTH = NUM_POINTS - 2;

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface SavedGesture {
  /** Human-readable label, e.g. "circle" or the package name of the target app. */
  label: string;
  /** Optional Android package name to launch when this gesture is recognised. */
  packageName?: string;
  /** The turning-angle signature (length === SIGNATURE_LENGTH). */
  signature: number[];
}

// ---------------------------------------------------------------------------
// Signature computation
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Similarity metrics
// ---------------------------------------------------------------------------

/**
 * Euclidean distance between two equal-length vectors.
 * Lower is more similar.
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

// ---------------------------------------------------------------------------
// Matching
// ---------------------------------------------------------------------------

/** Maximum Euclidean distance that is still considered a match. */
export const EUCLIDEAN_THRESHOLD = 3.0;

/** Minimum Cosine Similarity that is still considered a match. */
export const COSINE_THRESHOLD = 0.92;

export interface MatchResult {
  gesture: SavedGesture;
  euclideanDistance: number;
  cosineSimilarity: number;
}

/**
 * Finds the closest saved gesture for the given 40-point normalised input.
 *
 * Uses both Euclidean distance and Cosine Similarity; a match is accepted only
 * when **both** thresholds are satisfied.  Returns `null` if no gesture is
 * close enough or the list is empty.
 */
export function matchGesture(
  normalizedPoints: Point[],
  savedGestures: SavedGesture[]
): MatchResult | null {
  if (savedGestures.length === 0) return null;

  const inputSig = buildSignature(normalizedPoints);

  let best: MatchResult | null = null;
  let bestEuclidean = Infinity;

  for (const gesture of savedGestures) {
    if (gesture.signature.length !== SIGNATURE_LENGTH) continue;

    const ed = euclideanDistance(inputSig, gesture.signature);
    const cs = cosineSimilarity(inputSig, gesture.signature);

    if (ed < bestEuclidean && ed <= EUCLIDEAN_THRESHOLD && cs >= COSINE_THRESHOLD) {
      bestEuclidean = ed;
      best = { gesture, euclideanDistance: ed, cosineSimilarity: cs };
    }
  }

  return best;
}
