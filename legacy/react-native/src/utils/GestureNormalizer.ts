/**
 * GestureNormalizer.ts
 *
 * Takes an array of raw {x, y} touch coordinates, calculates the total path
 * length, and resamples the line into exactly NUM_POINTS equidistant points.
 */

export interface Point {
  x: number;
  y: number;
}

/** Number of equidistant points used to represent a normalized gesture. */
export const NUM_POINTS = 40;

/**
 * Calculates the Euclidean distance between two points.
 */
function distance(a: Point, b: Point): number {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  return Math.sqrt(dx * dx + dy * dy);
}

/**
 * Calculates the total arc-length of a polyline defined by `points`.
 */
export function totalPathLength(points: Point[]): number {
  let length = 0;
  for (let i = 1; i < points.length; i++) {
    length += distance(points[i - 1], points[i]);
  }
  return length;
}

/**
 * Resamples `rawPoints` into exactly `NUM_POINTS` equidistant points along the
 * same polyline.  Returns `null` if the input contains fewer than 2 points or
 * has zero total length (i.e. the user never moved their finger).
 */
export function normalizeTo40Points(rawPoints: Point[]): Point[] | null {
  if (rawPoints.length < 2) {
    return null;
  }

  const total = totalPathLength(rawPoints);
  if (total === 0) {
    return null;
  }

  const interval = total / (NUM_POINTS - 1);
  const resampled: Point[] = [{ ...rawPoints[0] }];

  // Track position within the original array using an index + a "carry-over"
  // distance from the start of the current segment – no array allocations.
  let accumulated = 0;
  let prev: Point = rawPoints[0];

  for (let i = 1; i < rawPoints.length && resampled.length < NUM_POINTS - 1; ) {
    const curr = rawPoints[i];
    const segLen = distance(prev, curr);
    const remaining = interval - accumulated;

    if (segLen >= remaining) {
      // Interpolate a new resampled point on this segment
      const t = remaining / segLen;
      const newPoint: Point = {
        x: prev.x + t * (curr.x - prev.x),
        y: prev.y + t * (curr.y - prev.y),
      };
      resampled.push(newPoint);
      // The new point is the new "previous"; stay on the same segment
      prev = newPoint;
      accumulated = 0;
      // Do NOT advance i – we may need more resampled points from this segment
    } else {
      accumulated += segLen;
      prev = curr;
      i++;
    }
  }

  // Always include the last raw point to close the path exactly
  resampled.push({ ...rawPoints[rawPoints.length - 1] });

  return resampled;
}
