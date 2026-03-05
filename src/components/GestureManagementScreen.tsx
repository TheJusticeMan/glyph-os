/**
 * GestureManagementScreen.tsx
 *
 * Full-screen view that displays the saved gesture library.  Users can
 * reassign an app to any gesture or delete individual entries; a "Clear All"
 * action wipes the entire library after confirmation.
 */

import React, { useCallback, useState } from 'react';
import {
  Alert,
  FlatList,
  ListRenderItemInfo,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';

import type { AppDetail } from '../services/InstalledAppsService';
import type { SavedGesture } from '../utils/GestureMatcher';
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
}

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
    marginBottom: 16,
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
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#222',
  },
  rowInfo: {
    flex: 1,
    marginRight: 12,
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
