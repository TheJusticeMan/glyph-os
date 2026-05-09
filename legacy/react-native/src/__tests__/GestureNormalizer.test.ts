/**
 * GestureNormalizer.test.ts
 *
 * Unit tests for the normalizeTo40Points resampling function.
 */

import { normalizeTo40Points, totalPathLength, NUM_POINTS, Point } from '../utils/GestureNormalizer';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Generates a straight horizontal line of `n` points from x=0 to x=length. */
function horizontalLine(n: number, length: number): Point[] {
  return Array.from({ length: n }, (_, i) => ({
    x: (i / (n - 1)) * length,
    y: 0,
  }));
}

function dist(a: Point, b: Point) {
  return Math.sqrt((b.x - a.x) ** 2 + (b.y - a.y) ** 2);
}

// ---------------------------------------------------------------------------
// totalPathLength
// ---------------------------------------------------------------------------

describe('totalPathLength', () => {
  it('returns 0 for a single point', () => {
    expect(totalPathLength([{ x: 0, y: 0 }])).toBe(0);
  });

  it('returns the correct length for a straight horizontal line', () => {
    const points = horizontalLine(5, 100);
    expect(totalPathLength(points)).toBeCloseTo(100);
  });

  it('returns the correct length for a right-angle path', () => {
    const points: Point[] = [
      { x: 0, y: 0 },
      { x: 3, y: 0 },
      { x: 3, y: 4 },
    ];
    expect(totalPathLength(points)).toBeCloseTo(7);
  });
});

// ---------------------------------------------------------------------------
// normalizeTo40Points
// ---------------------------------------------------------------------------

describe('normalizeTo40Points', () => {
  it('returns null for fewer than 2 points', () => {
    expect(normalizeTo40Points([])).toBeNull();
    expect(normalizeTo40Points([{ x: 0, y: 0 }])).toBeNull();
  });

  it('returns null when all points are identical (zero length)', () => {
    const points = Array.from({ length: 10 }, () => ({ x: 5, y: 5 }));
    expect(normalizeTo40Points(points)).toBeNull();
  });

  it(`produces exactly ${NUM_POINTS} points`, () => {
    const points = horizontalLine(100, 200);
    const result = normalizeTo40Points(points);
    expect(result).not.toBeNull();
    expect(result!.length).toBe(NUM_POINTS);
  });

  it('first and last points lie on the original path endpoints', () => {
    const points = horizontalLine(50, 100);
    const result = normalizeTo40Points(points)!;
    expect(result[0].x).toBeCloseTo(0);
    expect(result[NUM_POINTS - 1].x).toBeCloseTo(100);
  });

  it('produces equidistant points along a straight line', () => {
    const points = horizontalLine(200, 390);
    const result = normalizeTo40Points(points)!;
    const expectedInterval = 390 / (NUM_POINTS - 1);

    for (let i = 1; i < result.length; i++) {
      expect(dist(result[i - 1], result[i])).toBeCloseTo(expectedInterval, 0);
    }
  });

  it('works with a curved (L-shaped) path', () => {
    // Build an L-shaped path: right then down
    const horizontal: Point[] = Array.from({ length: 50 }, (_, i) => ({ x: i * 2, y: 0 }));
    const vertical: Point[] = Array.from({ length: 50 }, (_, i) => ({ x: 98, y: i * 2 + 2 }));
    const lPath = [...horizontal, ...vertical];

    const result = normalizeTo40Points(lPath);
    expect(result).not.toBeNull();
    expect(result!.length).toBe(NUM_POINTS);
  });
});
