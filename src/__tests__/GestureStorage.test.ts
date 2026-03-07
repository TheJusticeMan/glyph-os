/**
 * GestureStorage.test.ts
 *
 * Unit tests for loadGestures, saveGestures, and clearGestures.
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  loadGestures,
  saveGestures,
  clearGestures,
  STORAGE_SCHEMA_VERSION,
} from '../utils/GestureStorage';
import { SavedGesture } from '../utils/GestureMatcher';
import { NUM_POINTS, Point } from '../utils/GestureNormalizer';

jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock')
);

const STORAGE_KEY = 'glyph_os_gestures';

/** Generates a minimal valid 40-point horizontal path. */
function makePath(length = 100): Point[] {
  return Array.from({ length: NUM_POINTS }, (_, i) => ({
    x: (i / (NUM_POINTS - 1)) * length,
    y: 0,
  }));
}

const sampleGestures: SavedGesture[] = [
  { label: 'swipe-right', packageName: 'com.example.app', normalizedPath: makePath(100) },
  { label: 'circle', normalizedPath: makePath(50) },
];

// ---------------------------------------------------------------------------
// loadGestures
// ---------------------------------------------------------------------------

describe('loadGestures', () => {
  beforeEach(() => AsyncStorage.clear());

  it('returns an empty array when no data is stored', async () => {
    const result = await loadGestures();
    expect(result).toEqual([]);
  });

  it('returns gestures stored in the versioned envelope format', async () => {
    const store = { version: STORAGE_SCHEMA_VERSION, gestures: sampleGestures };
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(store));

    const result = await loadGestures();
    expect(result).toEqual(sampleGestures);
  });

  it('migrates old plain-array format to the versioned schema', async () => {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(sampleGestures));

    const result = await loadGestures();
    expect(result).toEqual(sampleGestures);
  });

  it('returns an empty array when stored JSON is malformed', async () => {
    await AsyncStorage.setItem(STORAGE_KEY, 'not-valid-json{{');

    const result = await loadGestures();
    expect(result).toEqual([]);
  });

  it('returns an empty array when stored value is an unrecognised shape', async () => {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify({ foo: 'bar' }));

    const result = await loadGestures();
    expect(result).toEqual([]);
  });

  it('filters out invalid entries from a stored array', async () => {
    const mixed = [
      sampleGestures[0],
      { label: 123, normalizedPath: [{ x: 0, y: 0 }, { x: 1, y: 1 }] },  // invalid label type
      { normalizedPath: [{ x: 0, y: 0 }, { x: 1, y: 1 }] },               // missing label
      { label: 'old-style', signature: [0.1, 0.2] },                       // legacy signature only
      { label: 'short-path', normalizedPath: [{ x: 0, y: 0 }] },          // path too short
    ];
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(mixed));

    const result = await loadGestures();
    expect(result).toHaveLength(1);
    expect(result[0].label).toBe('swipe-right');
  });
});

// ---------------------------------------------------------------------------
// saveGestures
// ---------------------------------------------------------------------------

describe('saveGestures', () => {
  beforeEach(() => AsyncStorage.clear());

  it('persists gestures wrapped in the versioned envelope', async () => {
    await saveGestures(sampleGestures);

    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    expect(raw).not.toBeNull();

    const stored = JSON.parse(raw!);
    expect(stored.version).toBe(STORAGE_SCHEMA_VERSION);
    expect(stored.gestures).toEqual(sampleGestures);
  });

  it('overwrites previously stored gestures', async () => {
    await saveGestures(sampleGestures);
    await saveGestures([sampleGestures[0]]);

    const result = await loadGestures();
    expect(result).toHaveLength(1);
    expect(result[0].label).toBe('swipe-right');
  });

  it('persists an empty array correctly', async () => {
    await saveGestures([]);
    const result = await loadGestures();
    expect(result).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// clearGestures
// ---------------------------------------------------------------------------

describe('clearGestures', () => {
  beforeEach(() => AsyncStorage.clear());

  it('removes all gestures so that loadGestures returns []', async () => {
    await saveGestures(sampleGestures);
    await clearGestures();

    const result = await loadGestures();
    expect(result).toEqual([]);
  });

  it('does not throw when there is nothing to clear', async () => {
    await expect(clearGestures()).resolves.toBeUndefined();
  });
});
