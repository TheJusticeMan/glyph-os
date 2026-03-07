/**
 * GestureMatcher.test.ts
 *
 * Unit tests for the gesture matching algorithm:
 *  - calculateDifference (new angular-difference matcher)
 *  - matchGesture (now uses calculateDifference with normalizedPath)
 */

import {
  calculateDifference,
  calculateBestDirectionDifference,
  matchGesture,
  rankSimilarApps,
  blendNormalizedPaths,
  isWithinThreshold,
  findBlendBoundsForDualMatch,
  SavedGesture,
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

  it('can optionally use reversed reference direction', () => {
    const path = straightLinePoints(100);
    const reversed = [...path].reverse();

    const forwardOnly = calculateBestDirectionDifference(path, reversed, false);
    const allowBackward = calculateBestDirectionDifference(path, reversed, true);

    expect(forwardOnly).toBeGreaterThan(ANGULAR_THRESHOLD);
    expect(allowBackward).toBeCloseTo(0, 6);
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

  it('matches reversed drawing direction only when enabled', () => {
    const points = straightLinePoints(100);
    const reversed = [...points].reverse();

    const saved: SavedGesture = {
      label: 'forward-line',
      packageName: 'com.example.line',
      normalizedPath: points,
    };

    expect(matchGesture(reversed, [saved])).toBeNull();
    const withBackward = matchGesture(reversed, [saved], { allowBackward: true });
    expect(withBackward).not.toBeNull();
    expect(withBackward?.gesture.label).toBe('forward-line');
  });
});

describe('rankSimilarApps', () => {
  it('returns best app matches globally with one best score per package', () => {
    const candidate = straightLinePoints(100);
    const close = candidate.map((p, i) => ({ x: p.x, y: i % 2 === 0 ? 0 : 0.25 }));
    const farther = candidate.map((p, i) => ({ x: p.x, y: i % 2 === 0 ? 0 : 1.2 }));
    const vertical = Array.from({ length: NUM_POINTS }, (_, i) => ({
      x: 0,
      y: (i / (NUM_POINTS - 1)) * 100,
    }));

    const saved: SavedGesture[] = [
      { label: 'a1', packageName: 'com.example.a', normalizedPath: farther },
      { label: 'a2', packageName: 'com.example.a', normalizedPath: close },
      { label: 'b1', packageName: 'com.example.b', normalizedPath: vertical },
    ];

    const ranked = rankSimilarApps(candidate, saved, 5);
    expect(ranked).toHaveLength(2);
    expect(ranked[0].packageName).toBe('com.example.a');
    expect(ranked[1].packageName).toBe('com.example.b');
    expect(ranked[0].angularDifference).toBeLessThan(ranked[1].angularDifference);
  });

  it('can rank reversed matches when backward option is enabled', () => {
    const base = straightLinePoints(100);
    const reversed = [...base].reverse();
    const vertical = Array.from({ length: NUM_POINTS }, (_, i) => ({
      x: 0,
      y: (i / (NUM_POINTS - 1)) * 100,
    }));

    const saved: SavedGesture[] = [
      { label: 'line', packageName: 'com.example.line', normalizedPath: base },
      { label: 'vert', packageName: 'com.example.vert', normalizedPath: vertical },
    ];

    const withoutBackward = rankSimilarApps(reversed, saved, 1);
    expect(withoutBackward[0].packageName).toBe('com.example.vert');

    const withBackward = rankSimilarApps(reversed, saved, 1, { allowBackward: true });
    expect(withBackward[0].packageName).toBe('com.example.line');
  });
});

describe('blendNormalizedPaths', () => {
  it('returns first path at t=0 and second path at t=1', () => {
    const a = straightLinePoints(100);
    const b = a.map((p) => ({ x: p.x, y: 20 }));
    expect(blendNormalizedPaths(a, b, 0)).toEqual(a);
    expect(blendNormalizedPaths(a, b, 1)).toEqual(b);
  });

  it('returns midpoint coordinates at t=0.5', () => {
    const a = straightLinePoints(100);
    const b = a.map((p) => ({ x: p.x + 10, y: 20 }));
    const mid = blendNormalizedPaths(a, b, 0.5);
    expect(mid[0].x).toBeCloseTo((a[0].x + b[0].x) / 2, 10);
    expect(mid[0].y).toBeCloseTo((a[0].y + b[0].y) / 2, 10);
  });
});

describe('isWithinThreshold', () => {
  it('is true for identical paths', () => {
    const path = straightLinePoints(100);
    expect(isWithinThreshold(path, path)).toBe(true);
  });

  it('is false for very different paths', () => {
    const horiz = straightLinePoints(100);
    const vert = Array.from({ length: NUM_POINTS }, (_, i) => ({
      x: 0,
      y: (i / (NUM_POINTS - 1)) * 100,
    }));
    expect(isWithinThreshold(horiz, vert)).toBe(false);
  });
});

describe('findBlendBoundsForDualMatch', () => {
  it('returns merge-able bounds for highly similar paths', () => {
    const oldPath = straightLinePoints(100);
    const newPath = oldPath.map((p, i) => ({ x: p.x, y: i % 2 === 0 ? 0 : 0.3 }));

    const result = findBlendBoundsForDualMatch(oldPath, newPath);
    expect(result.canMerge).toBe(true);
    expect(result.minT).toBeLessThanOrEqual(0.5);
    expect(result.maxT).toBeGreaterThanOrEqual(0.5);
  });

  it('returns not merge-able when midpoint cannot match both paths', () => {
    const oldPath = straightLinePoints(100);
    const newPath = Array.from({ length: NUM_POINTS }, (_, i) => ({
      x: 0,
      y: (i / (NUM_POINTS - 1)) * 100,
    }));

    const result = findBlendBoundsForDualMatch(oldPath, newPath);
    expect(result.canMerge).toBe(false);
    expect(result.minT).toBeCloseTo(0.5, 10);
    expect(result.maxT).toBeCloseTo(0.5, 10);
  });
});
