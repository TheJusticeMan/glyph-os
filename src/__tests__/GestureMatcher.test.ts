/**
 * GestureMatcher.test.ts
 *
 * Unit tests for the gesture matching algorithm:
 *  - calculateDifference (new angular-difference matcher)
 *  - matchGesture (now uses calculateDifference with normalizedPath)
 *  - Legacy utilities: buildSignature, euclideanDistance, cosineSimilarity
 */

import {
  buildSignature,
  euclideanDistance,
  cosineSimilarity,
  calculateDifference,
  matchGesture,
  SavedGesture,
  EUCLIDEAN_THRESHOLD,
  COSINE_THRESHOLD,
  ANGULAR_THRESHOLD,
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
// calculateDifference
// ---------------------------------------------------------------------------

describe('calculateDifference', () => {
  it('returns 0 for identical paths', () => {
    const path = straightLinePoints();
    expect(calculateDifference(path, path)).toBeCloseTo(0, 10);
  });

  it('returns Infinity when either path has fewer than 2 points', () => {
    const path = straightLinePoints();
    expect(calculateDifference([], path)).toBe(Infinity);
    expect(calculateDifference(path, [{ x: 0, y: 0 }])).toBe(Infinity);
  });

  it('returns a value close to π/2 for a 90-degree rotation', () => {
    // horizontal line vs vertical line
    const horiz = straightLinePoints(100);
    const vert = Array.from({ length: NUM_POINTS }, (_, i) => ({
      x: 0,
      y: (i / (NUM_POINTS - 1)) * 100,
    }));
    const diff = calculateDifference(horiz, vert);
    expect(diff).toBeCloseTo(Math.PI / 2, 5);
  });

  it('returns a small value for very similar (slightly perturbed) paths', () => {
    const path = straightLinePoints(100);
    const perturbed = path.map((p, i) => ({ x: p.x, y: i % 2 === 0 ? 0 : 0.5 }));
    const diff = calculateDifference(path, perturbed);
    expect(diff).toBeLessThan(ANGULAR_THRESHOLD);
  });

  it('returns a large value for very different paths (horizontal vs vertical)', () => {
    const straight = straightLinePoints(100);
    const vertical = Array.from({ length: NUM_POINTS }, (_, i) => ({
      x: 0,
      y: (i / (NUM_POINTS - 1)) * 100,
    }));
    const diff = calculateDifference(straight, vertical);
    expect(diff).toBeGreaterThan(ANGULAR_THRESHOLD);
  });
});

// ---------------------------------------------------------------------------
// buildSignature (legacy)
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
// euclideanDistance (legacy)
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
// cosineSimilarity (legacy)
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

// Verify that legacy threshold constants are still exported.
test('EUCLIDEAN_THRESHOLD and COSINE_THRESHOLD are exported', () => {
  expect(typeof EUCLIDEAN_THRESHOLD).toBe('number');
  expect(typeof COSINE_THRESHOLD).toBe('number');
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
    const points = normalizeTo40Points(horizontalLine(100, 200))!;

    const saved: SavedGesture = {
      label: 'straight-line',
      packageName: 'com.example.app',
      normalizedPath: points,
    };

    const result = matchGesture(points, [saved]);
    expect(result).not.toBeNull();
    expect(result!.gesture.label).toBe('straight-line');
    expect(result!.angularDifference).toBeCloseTo(0, 5);
  });

  it('returns null when gesture has no normalizedPath', () => {
    const points = normalizeTo40Points(horizontalLine(100, 200))!;
    const saved = { label: 'legacy', signature: [0, 1, 2] } as unknown as SavedGesture;
    expect(matchGesture(points, [saved])).toBeNull();
  });

  it('returns null when gesture normalizedPath is too short', () => {
    const points = normalizeTo40Points(horizontalLine(100, 200))!;
    const saved: SavedGesture = {
      label: 'bad-path',
      normalizedPath: [{ x: 0, y: 0 }],
    };
    expect(matchGesture(points, [saved])).toBeNull();
  });

  it('returns null when no gesture is within the threshold', () => {
    const straightPoints = normalizeTo40Points(horizontalLine(100, 200))!;

    // A vertical line is 90 degrees away from horizontal → angularDiff ≈ π/2 ≫ 0.5
    const verticalRaw: Point[] = Array.from({ length: 100 }, (_, i) => ({
      x: 0,
      y: i * 2,
    }));
    const verticalPoints = normalizeTo40Points(verticalRaw)!;

    const saved: SavedGesture = {
      label: 'vertical',
      normalizedPath: verticalPoints,
    };

    expect(matchGesture(straightPoints, [saved])).toBeNull();
  });

  it('picks the closest gesture when multiple candidates exist', () => {
    const points = normalizeTo40Points(horizontalLine(100, 200))!;

    // Identical path → perfect match
    const savedA: SavedGesture = { label: 'a', normalizedPath: points };
    // Slightly perturbed path
    const perturbedPath = points.map((p, i) => ({ x: p.x, y: i % 2 === 0 ? 0 : 0.5 }));
    const savedB: SavedGesture = { label: 'b', normalizedPath: perturbedPath };

    const result = matchGesture(points, [savedA, savedB]);
    expect(result).not.toBeNull();
    expect(result!.gesture.label).toBe('a');
  });
});
