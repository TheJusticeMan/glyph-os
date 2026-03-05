/**
 * GestureStorage.ts
 *
 * Persists and retrieves saved gestures using AsyncStorage.  Stored data is
 * wrapped in a versioned envelope so future schema changes can be migrated
 * without data loss.
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { SavedGesture } from './GestureMatcher';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** AsyncStorage key under which all gesture data is stored. */
const STORAGE_KEY = 'glyph_os_gestures';

/** Current schema version.  Bump this when the stored shape changes. */
export const STORAGE_SCHEMA_VERSION = 1;

// ---------------------------------------------------------------------------
// Internal types
// ---------------------------------------------------------------------------

interface GestureStore {
  version: number;
  gestures: SavedGesture[];
}

// ---------------------------------------------------------------------------
// Validation helpers
// ---------------------------------------------------------------------------

/** Returns true when `item` has the minimum required fields of a `SavedGesture`. */
function isValidGesture(item: unknown): item is SavedGesture {
  if (typeof item !== 'object' || item === null) return false;
  const g = item as Record<string, unknown>;
  return (
    typeof g.label === 'string' &&
    Array.isArray(g.signature) &&
    (g.signature as unknown[]).every((n) => typeof n === 'number')
  );
}

/** Filters an array value down to valid `SavedGesture` elements only. */
function toValidGestures(arr: unknown[]): SavedGesture[] {
  return arr.filter(isValidGesture);
}

// ---------------------------------------------------------------------------
// Migration
// ---------------------------------------------------------------------------

/**
 * Accepts any parsed JSON value and returns a normalised `GestureStore`.
 * If the value is a plain `SavedGesture[]` (old format, pre-versioning), it is
 * wrapped in the current schema envelope.  Invalid or unrecognised shapes fall
 * back to an empty store.
 */
function migrate(raw: unknown): GestureStore {
  if (Array.isArray(raw)) {
    return { version: STORAGE_SCHEMA_VERSION, gestures: toValidGestures(raw) };
  }

  if (typeof raw === 'object' && raw !== null) {
    const obj = raw as Record<string, unknown>;
    if (typeof obj.version === 'number' && Array.isArray(obj.gestures)) {
      return {
        version: obj.version,
        gestures: toValidGestures(obj.gestures as unknown[]),
      };
    }
  }

  return { version: STORAGE_SCHEMA_VERSION, gestures: [] };
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Loads all saved gestures from AsyncStorage.
 * Returns an empty array when no data is stored or an error occurs.
 */
export async function loadGestures(): Promise<SavedGesture[]> {
  try {
    const json = await AsyncStorage.getItem(STORAGE_KEY);
    if (json === null) return [];

    const store = migrate(JSON.parse(json));
    return store.gestures;
  } catch {
    return [];
  }
}

/**
 * Persists the full gesture array to AsyncStorage, replacing any existing data.
 */
export async function saveGestures(gestures: SavedGesture[]): Promise<void> {
  const store: GestureStore = { version: STORAGE_SCHEMA_VERSION, gestures };
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(store));
}

/**
 * Removes all gesture data from AsyncStorage.
 */
export async function clearGestures(): Promise<void> {
  await AsyncStorage.removeItem(STORAGE_KEY);
}
