/**
 * GestureManager.tsx
 *
 * Settings / management screen that lists saved gestures and allows the user
 * to rename them, reassign their bound app, delete individual entries, or
 * clear the entire library.
 */

import React, { useState } from 'react';
import {
  Alert,
  FlatList,
  Image,
  ListRenderItem,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { RNLauncherKitHelper } from 'react-native-launcher-kit';

import type { GestureStore } from '../hooks/useGestureStore';
import type { InstalledAppsStore } from '../hooks/useInstalledApps';
import type { SavedGesture } from '../utils/GestureMatcher';
import { AppPicker } from './AppPicker';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface Props {
  gestureStore: GestureStore;
  appsStore: InstalledAppsStore;
  /** Called when the user wants to leave this screen. */
  onClose: () => void;
}

// ---------------------------------------------------------------------------
// Row component
// ---------------------------------------------------------------------------

interface RowProps {
  gesture: SavedGesture;
  appIcon: string;
  onRename: () => void;
  onReassign: () => void;
  onDelete: () => void;
}

const GestureRow: React.FC<RowProps> = ({ gesture, appIcon, onRename, onReassign, onDelete }) => (
  <View style={rowStyles.container}>
    <View style={rowStyles.info}>
      {appIcon ? (
        <Image
          source={{ uri: `data:image/png;base64,${appIcon}` }}
          style={rowStyles.icon}
        />
      ) : (
        <View style={[rowStyles.icon, rowStyles.iconPlaceholder]} />
      )}
      <View style={rowStyles.labels}>
        <Text style={rowStyles.label} numberOfLines={1}>
          {gesture.label}
        </Text>
        {gesture.packageName ? (
          <Text style={rowStyles.pkg} numberOfLines={1}>
            {gesture.packageName}
          </Text>
        ) : (
          <Text style={rowStyles.unbound}>Not bound to any app</Text>
        )}
      </View>
    </View>
    <View style={rowStyles.actions}>
      <TouchableOpacity onPress={onRename} hitSlop={HIT_SLOP} style={rowStyles.actionBtn}>
        <Text style={rowStyles.actionText}>Rename</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={onReassign} hitSlop={HIT_SLOP} style={rowStyles.actionBtn}>
        <Text style={rowStyles.actionText}>App</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={onDelete} hitSlop={HIT_SLOP} style={rowStyles.actionBtn}>
        <Text style={[rowStyles.actionText, rowStyles.deleteText]}>Del</Text>
      </TouchableOpacity>
    </View>
  </View>
);

const HIT_SLOP = { top: 8, bottom: 8, left: 8, right: 8 };

const rowStyles = StyleSheet.create({
  container: {
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#1A1A1A',
  },
  info: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  icon: {
    width: 40,
    height: 40,
    borderRadius: 8,
    marginRight: 12,
  },
  iconPlaceholder: {
    backgroundColor: '#222',
  },
  labels: {
    flex: 1,
  },
  label: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '500',
  },
  pkg: {
    color: '#666',
    fontSize: 12,
    marginTop: 2,
  },
  unbound: {
    color: '#444',
    fontSize: 12,
    marginTop: 2,
    fontStyle: 'italic',
  },
  actions: {
    flexDirection: 'row',
    marginTop: 6,
    paddingLeft: 52,
  },
  actionBtn: {
    marginRight: 16,
    paddingVertical: 4,
  },
  actionText: {
    color: '#00FFCC',
    fontSize: 13,
    fontWeight: '600',
  },
  deleteText: {
    color: '#FF4455',
  },
});

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

export const GestureManager: React.FC<Props> = ({ gestureStore, appsStore, onClose }) => {
  const { gestures, updateGesture, deleteGesture, clearGestures } = gestureStore;
  const [pickingFor, setPickingFor] = useState<SavedGesture | null>(null);
  const [renamingFor, setRenamingFor] = useState<SavedGesture | null>(null);
  const [renameText, setRenameText] = useState('');

  // -------------------------------------------------------------------------
  // Rename
  // -------------------------------------------------------------------------
  const startRename = (g: SavedGesture) => {
    setRenamingFor(g);
    setRenameText(g.label);
  };

  const commitRename = () => {
    if (!renamingFor) return;
    const trimmed = renameText.trim();
    if (!trimmed) return;
    updateGesture({ ...renamingFor, label: trimmed });
    setRenamingFor(null);
  };

  // -------------------------------------------------------------------------
  // Reassign app
  // -------------------------------------------------------------------------
  const handlePickResult = (app: { label: string; packageName: string; icon: string } | null) => {
    if (!app || !pickingFor) {
      setPickingFor(null);
      return;
    }
    // Preserve the user's existing label; only update the packageName binding.
    updateGesture({ ...pickingFor, packageName: app.packageName });
    setPickingFor(null);
  };

  // -------------------------------------------------------------------------
  // Clear all
  // -------------------------------------------------------------------------
  const handleClearAll = () => {
    Alert.alert(
      'Clear All Gestures',
      'This will permanently delete all saved gestures. Continue?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete All',
          style: 'destructive',
          onPress: clearGestures,
        },
      ]
    );
  };

  // -------------------------------------------------------------------------
  // Set default launcher
  // -------------------------------------------------------------------------
  const handleSetDefault = () => {
    RNLauncherKitHelper.openSetDefaultLauncher().catch((err) => {
      console.warn('[GestureManager] Could not open default launcher settings:', err);
    });
  };

  // -------------------------------------------------------------------------
  // App Picker overlay
  // -------------------------------------------------------------------------
  if (pickingFor) {
    return (
      <AppPicker apps={appsStore.apps} isLoading={appsStore.isLoading} onPick={handlePickResult} />
    );
  }

  // -------------------------------------------------------------------------
  // Render item
  // -------------------------------------------------------------------------
  const iconForGesture = (g: SavedGesture) => {
    if (!g.packageName) return '';
    const match = appsStore.apps.find((a) => a.packageName === g.packageName);
    return match?.icon ?? '';
  };

  const renderItem: ListRenderItem<SavedGesture> = ({ item }) => (
    <GestureRow
      gesture={item}
      appIcon={iconForGesture(item)}
      onRename={() => startRename(item)}
      onReassign={() => setPickingFor(item)}
      onDelete={() =>
        Alert.alert('Delete gesture', `Delete "${item.label}"?`, [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Delete',
            style: 'destructive',
            onPress: () => deleteGesture(item.label),
          },
        ])
      }
    />
  );

  return (
    <View style={styles.root}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.title}>Gesture Library</Text>
        <TouchableOpacity onPress={onClose} hitSlop={HIT_SLOP}>
          <Text style={styles.done}>Done</Text>
        </TouchableOpacity>
      </View>

      {/* Rename inline editor */}
      {renamingFor ? (
        <View style={styles.renameBar}>
          <TextInput
            style={styles.renameInput}
            value={renameText}
            onChangeText={setRenameText}
            autoFocus
            onSubmitEditing={commitRename}
            returnKeyType="done"
            placeholderTextColor="#666"
            placeholder="New label…"
          />
          <TouchableOpacity onPress={commitRename} style={styles.renameConfirm}>
            <Text style={styles.renameConfirmText}>OK</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => setRenamingFor(null)} style={styles.renameConfirm}>
            <Text style={[styles.renameConfirmText, { color: '#FF4455' }]}>✕</Text>
          </TouchableOpacity>
        </View>
      ) : null}

      {/* List */}
      <FlatList
        data={gestures}
        keyExtractor={(g) => g.label}
        renderItem={renderItem}
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          <Text style={styles.empty}>No gestures saved yet.{'\n'}Draw a gesture to add one.</Text>
        }
      />

      {/* Footer actions */}
      <View style={styles.footer}>
        <TouchableOpacity style={styles.footerBtn} onPress={handleSetDefault}>
          <Text style={styles.footerBtnText}>Set as Default Launcher</Text>
        </TouchableOpacity>
        {gestures.length > 0 ? (
          <TouchableOpacity style={[styles.footerBtn, styles.footerBtnDanger]} onPress={handleClearAll}>
            <Text style={[styles.footerBtnText, styles.footerBtnDangerText]}>Clear All Gestures</Text>
          </TouchableOpacity>
        ) : null}
      </View>
    </View>
  );
};

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#0A0A0A',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#1A1A1A',
  },
  title: {
    color: '#FFFFFF',
    fontSize: 22,
    fontWeight: '700',
  },
  done: {
    color: '#00FFCC',
    fontSize: 17,
    fontWeight: '600',
  },
  renameBar: {
    flexDirection: 'row',
    alignItems: 'center',
    margin: 12,
    backgroundColor: '#1A1A1A',
    borderRadius: 8,
    paddingHorizontal: 10,
    borderWidth: 1,
    borderColor: '#00FFCC',
  },
  renameInput: {
    flex: 1,
    color: '#FFFFFF',
    fontSize: 15,
    paddingVertical: 8,
  },
  renameConfirm: {
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  renameConfirmText: {
    color: '#00FFCC',
    fontSize: 15,
    fontWeight: '700',
  },
  list: {
    paddingHorizontal: 16,
    paddingBottom: 16,
    flexGrow: 1,
  },
  empty: {
    color: '#444',
    textAlign: 'center',
    marginTop: 60,
    fontSize: 16,
    lineHeight: 26,
  },
  footer: {
    padding: 16,
    gap: 10,
    borderTopWidth: 1,
    borderTopColor: '#1A1A1A',
  },
  footerBtn: {
    paddingVertical: 14,
    borderRadius: 10,
    backgroundColor: '#1A1A1A',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#00FFCC',
  },
  footerBtnText: {
    color: '#00FFCC',
    fontSize: 15,
    fontWeight: '600',
  },
  footerBtnDanger: {
    borderColor: '#FF4455',
    backgroundColor: '#1A0000',
  },
  footerBtnDangerText: {
    color: '#FF4455',
  },
});
