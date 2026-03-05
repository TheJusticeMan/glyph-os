/**
 * useGestureStore.ts
 *
 * React hook that provides a persistent gesture library backed by
 * AsyncStorage.  All mutations are automatically flushed to storage.
 *
 * Storage schema (STORAGE_KEY):
 *   { version: 1, gestures: SavedGesture[] }
 *
 * If an older/unknown schema version is found the data is discarded and a
 * fresh library is created (forward-only migration strategy).
 */

import { useCallback, useEffect, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { SavedGesture } from '../utils/GestureMatcher';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const STORAGE_KEY = '@glyphos:gesture_store';
const SCHEMA_VERSION = 1;

// ---------------------------------------------------------------------------
// Serialised shape
// ---------------------------------------------------------------------------

interface StoredData {
  version: number;
  gestures: SavedGesture[];
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function loadFromStorage(): Promise<SavedGesture[]> {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: StoredData = JSON.parse(raw);
    if (parsed.version !== SCHEMA_VERSION) {
      // Incompatible schema – start fresh
      await AsyncStorage.removeItem(STORAGE_KEY);
      return [];
    }
    return Array.isArray(parsed.gestures) ? parsed.gestures : [];
  } catch (err) {
    console.warn('[GestureStore] Failed to load gestures from storage:', err);
    return [];
  }
}

async function saveToStorage(gestures: SavedGesture[]): Promise<void> {
  try {
    const data: StoredData = { version: SCHEMA_VERSION, gestures };
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch (err) {
    // Storage write failure is non-fatal – gestures remain usable in memory.
    console.warn('[GestureStore] Failed to persist gestures:', err);
  }
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

export interface GestureStore {
  /** The current in-memory (and persisted) list of saved gestures. */
  gestures: SavedGesture[];
  /** True while the initial load from storage is in progress. */
  isLoading: boolean;
  /** Append a new gesture and persist the library. */
  addGesture: (gesture: SavedGesture) => void;
  /** Replace an existing gesture (matched by label) and persist. */
  updateGesture: (updated: SavedGesture) => void;
  /** Remove the gesture with the given label and persist. */
  deleteGesture: (label: string) => void;
  /** Remove all gestures and persist. */
  clearGestures: () => void;
}

export function useGestureStore(): GestureStore {
  const [gestures, setGestures] = useState<SavedGesture[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Initial load
  useEffect(() => {
    let cancelled = false;
    loadFromStorage().then((loaded) => {
      if (!cancelled) {
        setGestures(loaded);
        setIsLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const addGesture = useCallback((gesture: SavedGesture) => {
    setGestures((prev) => {
      const next = [...prev, gesture];
      saveToStorage(next);
      return next;
    });
  }, []);

  const updateGesture = useCallback((updated: SavedGesture) => {
    setGestures((prev) => {
      const next = prev.map((g) => (g.label === updated.label ? updated : g));
      saveToStorage(next);
      return next;
    });
  }, []);

  const deleteGesture = useCallback((label: string) => {
    setGestures((prev) => {
      const next = prev.filter((g) => g.label !== label);
      saveToStorage(next);
      return next;
    });
  }, []);

  const clearGestures = useCallback(() => {
    setGestures([]);
    saveToStorage([]);
  }, []);

  return { gestures, isLoading, addGesture, updateGesture, deleteGesture, clearGestures };
}
