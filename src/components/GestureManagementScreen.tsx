/**
 * GestureManagementScreen.tsx
 *
 * Full-screen view that displays the saved gesture library.  Users can
 * reassign an app to any gesture or delete individual entries; a "Clear All"
 * action wipes the entire library after confirmation.
 *
 * Each gesture row includes a small SVG preview of the recorded path.
 * A trail-effect toggle lets users enable the comet-trail drawing style
 * (beautiful on fast devices, disabled by default for performance).
 */

import React, { useCallback, useMemo, useState } from 'react';
import {
  Alert,
  FlatList,
  ListRenderItemInfo,
  StyleSheet,
  Switch,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Svg, { Path } from 'react-native-svg';

import type { AppDetail } from '../services/InstalledAppsService';
import type { SavedGesture } from '../utils/GestureMatcher';
import type { Point } from '../utils/GestureNormalizer';
import AssignAppModal from './AssignAppModal';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface GestureManagementScreenProps {
  gestures: SavedGesture[];
  onUpdateGesture: (label: string, updates: Partial<SavedGesture>) => void;
  onDeleteGesture: (label: string) => void;
  onClearAll: () => void;
  onClose: () => void;
  trailEffect: boolean;
  onToggleTrailEffect: () => void;
}

// ---------------------------------------------------------------------------
// GesturePreview – small SVG thumbnail of a normalised path
// ---------------------------------------------------------------------------

const PREVIEW_SIZE = 52;
const PREVIEW_PADDING = 6;

function buildPreviewPath(points: Point[]): string {
  if (points.length < 2) return '';

  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const p of points) {
    if (p.x < minX) minX = p.x;
    if (p.y < minY) minY = p.y;
    if (p.x > maxX) maxX = p.x;
    if (p.y > maxY) maxY = p.y;
  }

  const w = maxX - minX || 1;
  const h = maxY - minY || 1;
  const available = PREVIEW_SIZE - PREVIEW_PADDING * 2;
  const scale = available / Math.max(w, h);
  const offsetX = PREVIEW_PADDING + (available - w * scale) / 2;
  const offsetY = PREVIEW_PADDING + (available - h * scale) / 2;

  return points
    .map((p, i) => {
      const sx = (p.x - minX) * scale + offsetX;
      const sy = (p.y - minY) * scale + offsetY;
      return `${i === 0 ? 'M' : 'L'} ${sx.toFixed(1)} ${sy.toFixed(1)}`;
    })
    .join(' ');
}

interface GesturePreviewProps {
  points: Point[];
}

const GesturePreview: React.FC<GesturePreviewProps> = React.memo(({ points }) => {
  const d = useMemo(() => buildPreviewPath(points), [points]);

  return (
    <View style={styles.previewBox} accessibilityLabel="Gesture path preview">
      {d ? (
        <Svg width={PREVIEW_SIZE} height={PREVIEW_SIZE}>
          <Path
            d={d}
            stroke="#00FFCC"
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
            fill="none"
          />
        </Svg>
      ) : (
        <Text style={styles.previewEmpty} accessibilityLabel="Gesture preview unavailable">?</Text>
      )}
    </View>
  );
});

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

interface GestureRowProps {
  gesture: SavedGesture;
  onReassign: (label: string) => void;
  onDelete: (label: string) => void;
}

const GestureRow: React.FC<GestureRowProps> = React.memo(({ gesture, onReassign, onDelete }) => {
  const handleReassign = useCallback(() => onReassign(gesture.label), [gesture.label, onReassign]);
  const handleDelete = useCallback(() => onDelete(gesture.label), [gesture.label, onDelete]);

  return (
    <View style={styles.row}>
      <GesturePreview points={gesture.normalizedPath ?? []} />
      <View style={styles.rowInfo}>
        <Text style={styles.gestureLabel} numberOfLines={1}>
          {gesture.label}
        </Text>
        {gesture.packageName ? (
          <Text style={styles.packageName} numberOfLines={1}>
            {gesture.packageName}
          </Text>
        ) : (
          <Text style={styles.noApp}>No app assigned</Text>
        )}
      </View>
      <View style={styles.rowActions}>
        <TouchableOpacity onPress={handleReassign} activeOpacity={0.7} style={styles.actionButton}>
          <Text style={styles.reassignText}>Reassign</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={handleDelete} activeOpacity={0.7} style={styles.actionButton}>
          <Text style={styles.deleteText}>Delete</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
});

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

const GestureManagementScreen: React.FC<GestureManagementScreenProps> = ({
  gestures,
  onUpdateGesture,
  onDeleteGesture,
  onClearAll,
  onClose,
  trailEffect,
  onToggleTrailEffect,
}) => {
  const [reassignTarget, setReassignTarget] = useState<string | null>(null);

  // -------------------------------------------------------------------------
  // Handlers
  // -------------------------------------------------------------------------

  const handleReassign = useCallback((label: string) => {
    setReassignTarget(label);
  }, []);

  const handleAssignApp = useCallback(
    (app: AppDetail) => {
      if (reassignTarget !== null) {
        onUpdateGesture(reassignTarget, { packageName: app.packageName });
      }
      setReassignTarget(null);
    },
    [reassignTarget, onUpdateGesture],
  );

  const handleCancelAssign = useCallback(() => {
    setReassignTarget(null);
  }, []);

  const handleDelete = useCallback(
    (label: string) => {
      Alert.alert(
        'Delete Gesture',
        `Are you sure you want to delete "${label}"?`,
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Delete',
            style: 'destructive',
            onPress: () => onDeleteGesture(label),
          },
        ],
      );
    },
    [onDeleteGesture],
  );

  const handleClearAll = useCallback(() => {
    Alert.alert(
      'Clear All Gestures',
      'This will permanently delete every saved gesture. Continue?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Clear All',
          style: 'destructive',
          onPress: onClearAll,
        },
      ],
    );
  }, [onClearAll]);

  // -------------------------------------------------------------------------
  // List helpers
  // -------------------------------------------------------------------------

  const keyExtractor = useCallback((item: SavedGesture) => item.label, []);

  const renderItem = useCallback(
    ({ item }: ListRenderItemInfo<SavedGesture>) => (
      <GestureRow gesture={item} onReassign={handleReassign} onDelete={handleDelete} />
    ),
    [handleReassign, handleDelete],
  );

  const renderEmpty = useCallback(
    () => (
      <View style={styles.emptyContainer}>
        <Text style={styles.emptyText}>No gestures saved yet.</Text>
      </View>
    ),
    [],
  );

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.title}>Gesture Library</Text>
        <TouchableOpacity onPress={onClose} activeOpacity={0.7} style={styles.closeButton}>
          <Text style={styles.closeText}>✕</Text>
        </TouchableOpacity>
      </View>

      {/* Settings row – trail effect toggle */}
      <View style={styles.settingsRow}>
        <Text style={styles.settingsLabel}>Trail effect</Text>
        <Text style={styles.settingsHint}>(may slow old devices)</Text>
        <Switch
          value={trailEffect}
          onValueChange={onToggleTrailEffect}
          trackColor={{ false: '#333', true: '#00FFCC55' }}
          thumbColor={trailEffect ? '#00FFCC' : '#888'}
          accessibilityLabel="Toggle trail effect for gesture drawing"
        />
      </View>

      {/* Clear All */}
      <TouchableOpacity
        onPress={handleClearAll}
        activeOpacity={0.7}
        style={styles.clearAllButton}
      >
        <Text style={styles.clearAllText}>Clear All</Text>
      </TouchableOpacity>

      {/* Gesture list */}
      <FlatList
        data={gestures}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        ListEmptyComponent={renderEmpty}
        contentContainerStyle={gestures.length === 0 ? styles.listContentEmpty : styles.listContent}
        showsVerticalScrollIndicator={false}
      />

      {/* Reassign modal */}
      <AssignAppModal
        visible={reassignTarget !== null}
        onAssign={handleAssignApp}
        onCancel={handleCancelAssign}
      />
    </View>
  );
};

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000000',
    paddingTop: 48,
    paddingHorizontal: 16,
  },
  // Header
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  title: {
    fontSize: 22,
    fontWeight: '700',
    color: '#00FFCC',
  },
  closeButton: {
    padding: 8,
  },
  closeText: {
    fontSize: 20,
    color: '#00FFCC',
    fontWeight: '600',
  },
  // Settings
  settingsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    marginBottom: 4,
    gap: 8,
  },
  settingsLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#CCCCCC',
  },
  settingsHint: {
    flex: 1,
    fontSize: 11,
    color: '#555',
  },
  // Clear All
  clearAllButton: {
    alignSelf: 'flex-end',
    borderWidth: 1,
    borderColor: '#00FFCC',
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 8,
    marginBottom: 16,
  },
  clearAllText: {
    color: '#FF4444',
    fontSize: 14,
    fontWeight: '600',
  },
  // List
  listContent: {
    paddingBottom: 24,
  },
  listContentEmpty: {
    flex: 1,
  },
  // Row
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#222',
    gap: 10,
  },
  // Gesture path preview
  previewBox: {
    width: PREVIEW_SIZE,
    height: PREVIEW_SIZE,
    borderRadius: 6,
    backgroundColor: '#111',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: '#333',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  previewEmpty: {
    fontSize: 18,
    color: '#444',
  },
  rowInfo: {
    flex: 1,
    marginRight: 4,
  },
  gestureLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: '#FFFFFF',
  },
  packageName: {
    fontSize: 12,
    color: '#888',
    marginTop: 2,
  },
  noApp: {
    fontSize: 12,
    color: '#555',
    marginTop: 2,
  },
  rowActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  actionButton: {
    paddingHorizontal: 4,
    paddingVertical: 4,
  },
  reassignText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#00FFCC',
  },
  deleteText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#FF4444',
  },
  // Empty state
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
    color: '#555',
    textAlign: 'center',
  },
});

export default GestureManagementScreen;
