/**
 * GestureMatcher.test.ts
 *
 * Unit tests for buildSignature, euclideanDistance, cosineSimilarity, and
 * matchGesture.
 */

import {
  buildSignature,
  euclideanDistance,
  cosineSimilarity,
  matchGesture,
  SavedGesture,
  EUCLIDEAN_THRESHOLD,
  COSINE_THRESHOLD,
} from '../utils/GestureMatcher';
import { normalizeTo40Points, NUM_POINTS, Point } from '../utils/GestureNormalizer';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function horizontalLine(n: number, length: number): Point[] {
  return Array.from({ length: n }, (_, i) => ({
    x: (i / (n - 1)) * length,
    y: 0,
  }));
}

/** Generate 40 already-equidistant points on a straight line. */
function straightLinePoints(length = 100): Point[] {
  return Array.from({ length: NUM_POINTS }, (_, i) => ({
    x: (i / (NUM_POINTS - 1)) * length,
    y: 0,
  }));
}

// ---------------------------------------------------------------------------
// buildSignature
// ---------------------------------------------------------------------------

describe('buildSignature', () => {
  it(`produces an array of length ${NUM_POINTS - 2}`, () => {
    const points = straightLinePoints();
    const sig = buildSignature(points);
    expect(sig).toHaveLength(NUM_POINTS - 2);
  });

  it('produces near-zero turning angles for a straight line', () => {
    const points = straightLinePoints();
    const sig = buildSignature(points);
    sig.forEach((angle) => expect(Math.abs(angle)).toBeCloseTo(0, 10));
  });

  it('throws if fewer than NUM_POINTS points are provided', () => {
    expect(() => buildSignature(straightLinePoints().slice(0, 5))).toThrow();
  });
});

// ---------------------------------------------------------------------------
// euclideanDistance
// ---------------------------------------------------------------------------

describe('euclideanDistance', () => {
  it('returns 0 for identical vectors', () => {
    const v = [1, 2, 3, 4];
    expect(euclideanDistance(v, v)).toBe(0);
  });

  it('computes the correct distance for known vectors', () => {
    const a = [0, 0];
    const b = [3, 4];
    expect(euclideanDistance(a, b)).toBeCloseTo(5);
  });
});

// ---------------------------------------------------------------------------
// cosineSimilarity
// ---------------------------------------------------------------------------

describe('cosineSimilarity', () => {
  it('returns 1 for identical non-zero vectors', () => {
    const v = [1, 2, 3];
    expect(cosineSimilarity(v, v)).toBeCloseTo(1);
  });

  it('returns 0 for perpendicular vectors', () => {
    expect(cosineSimilarity([1, 0], [0, 1])).toBeCloseTo(0);
  });

  it('returns -1 for opposite vectors', () => {
    expect(cosineSimilarity([1, 0], [-1, 0])).toBeCloseTo(-1);
  });

  it('returns 0 for zero vectors when only one is zero', () => {
    expect(cosineSimilarity([1, 0], [0, 0])).toBe(0);
  });

  it('returns 1 for two all-zero vectors (identical degenerate case)', () => {
    expect(cosineSimilarity([0, 0], [0, 0])).toBe(1);
  });
});

// ---------------------------------------------------------------------------
// matchGesture
// ---------------------------------------------------------------------------

describe('matchGesture', () => {
  it('returns null when savedGestures is empty', () => {
    const points = normalizeTo40Points(horizontalLine(100, 200))!;
    expect(matchGesture(points, [])).toBeNull();
  });

  it('matches an identical gesture', () => {
    const raw = horizontalLine(100, 200);
    const points = normalizeTo40Points(raw)!;
    const sig = buildSignature(points);

    const saved: SavedGesture = {
      label: 'straight-line',
      packageName: 'com.example.app',
      signature: sig,
    };

    const result = matchGesture(points, [saved]);
    expect(result).not.toBeNull();
    expect(result!.gesture.label).toBe('straight-line');
    expect(result!.euclideanDistance).toBeCloseTo(0, 5);
    expect(result!.cosineSimilarity).toBeCloseTo(1, 5);
  });

  it('returns null when only gesture has wrong signature length', () => {
    const points = normalizeTo40Points(horizontalLine(100, 200))!;
    const saved: SavedGesture = {
      label: 'bad-sig',
      signature: [0, 1, 2], // wrong length
    };
    expect(matchGesture(points, [saved])).toBeNull();
  });

  it('returns null when no gesture is within thresholds', () => {
    // Create two very different gestures: a straight line vs a jagged path
    const straightRaw = horizontalLine(100, 200);
    const straightPoints = normalizeTo40Points(straightRaw)!;

    // Build a zigzag that will have large turning angles
    const zigzagRaw: Point[] = Array.from({ length: 80 }, (_, i) => ({
      x: i * 5,
      y: i % 2 === 0 ? 0 : 50,
    }));
    const zigzagPoints = normalizeTo40Points(zigzagRaw)!;
    const zigzagSig = buildSignature(zigzagPoints);

    const saved: SavedGesture = {
      label: 'zigzag',
      signature: zigzagSig,
    };

    // A straight line should NOT match a zigzag
    expect(matchGesture(straightPoints, [saved])).toBeNull();
  });

  it('picks the closest gesture when multiple candidates exist', () => {
    const rawA = horizontalLine(100, 200);
    const pointsA = normalizeTo40Points(rawA)!;
    const sigA = buildSignature(pointsA);

    // Slightly perturb A to create gesture B
    const sigB = sigA.map((v) => v + 0.01);

    const savedA: SavedGesture = { label: 'a', signature: sigA };
    const savedB: SavedGesture = { label: 'b', signature: sigB };

    const result = matchGesture(pointsA, [savedA, savedB]);
    expect(result).not.toBeNull();
    expect(result!.gesture.label).toBe('a');
  });
});
