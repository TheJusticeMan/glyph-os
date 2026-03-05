/**
 * AssignAppModal.tsx
 *
 * Full-screen modal that lets the user search their installed apps and bind
 * one to a gesture.  Consuming components receive the selected `AppDetail`
 * via the `onAssign` callback.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Image,
  ListRenderItemInfo,
  Modal,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

import type { AppDetail } from '../services/InstalledAppsService';
import { filterApps } from '../services/InstalledAppsService';
import useInstalledApps from '../hooks/useInstalledApps';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface AssignAppModalProps {
  visible: boolean;
  onAssign: (app: AppDetail) => void;
  onCancel: () => void;
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

interface AppRowProps {
  app: AppDetail;
  onPress: (app: AppDetail) => void;
}

const AppRow: React.FC<AppRowProps> = React.memo(({ app, onPress }) => {
  const handlePress = useCallback(() => onPress(app), [app, onPress]);

  return (
    <TouchableOpacity style={styles.row} onPress={handlePress} activeOpacity={0.7}>
      {app.icon ? (
        <Image
          source={{ uri: `data:image/png;base64,${app.icon}` }}
          style={styles.icon}
          resizeMode="contain"
        />
      ) : (
        <View style={styles.iconPlaceholder} />
      )}
      <View style={styles.rowText}>
        <Text style={styles.appLabel} numberOfLines={1}>
          {app.label}
        </Text>
        <Text style={styles.packageName} numberOfLines={1}>
          {app.packageName}
        </Text>
      </View>
    </TouchableOpacity>
  );
});

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

const AssignAppModal: React.FC<AssignAppModalProps> = ({ visible, onAssign, onCancel }) => {
  const [query, setQuery] = useState<string>('');
  const { apps, loading, error, refresh } = useInstalledApps();

  // Reset search query each time the modal opens.
  useEffect(() => {
    if (visible) {
      setQuery('');
    }
  }, [visible]);

  const filteredApps = useMemo(() => filterApps(apps, query), [apps, query]);

  const handleAssign = useCallback(
    (app: AppDetail) => {
      onAssign(app);
    },
    [onAssign],
  );

  const renderItem = useCallback(
    ({ item }: ListRenderItemInfo<AppDetail>) => (
      <AppRow app={item} onPress={handleAssign} />
    ),
    [handleAssign],
  );

  const keyExtractor = useCallback((item: AppDetail) => item.packageName, []);

  // -------------------------------------------------------------------------
  // Content states
  // -------------------------------------------------------------------------

  const renderContent = () => {
    if (loading) {
      return (
        <View style={styles.centered}>
          <ActivityIndicator size="large" color="#00FFCC" />
        </View>
      );
    }

    if (error) {
      return (
        <View style={styles.centered}>
          <Text style={styles.errorText}>{error}</Text>
          <TouchableOpacity style={styles.retryButton} onPress={refresh} activeOpacity={0.7}>
            <Text style={styles.retryLabel}>Retry</Text>
          </TouchableOpacity>
        </View>
      );
    }

    return (
      <FlatList
        data={filteredApps}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        contentContainerStyle={styles.listContent}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      />
    );
  };

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------

  return (
    <Modal
      visible={visible}
      animationType="slide"
      onRequestClose={onCancel}
      statusBarTranslucent
    >
      <View style={styles.container}>
        <Text style={styles.title}>Assign App to Gesture</Text>

        <TextInput
          style={styles.searchBar}
          placeholder="Search apps…"
          placeholderTextColor="#555"
          value={query}
          onChangeText={setQuery}
          autoCorrect={false}
          autoCapitalize="none"
          returnKeyType="search"
        />

        {renderContent()}

        <TouchableOpacity style={styles.cancelButton} onPress={onCancel} activeOpacity={0.7}>
          <Text style={styles.cancelLabel}>Cancel</Text>
        </TouchableOpacity>
      </View>
    </Modal>
  );
};

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#111',
    paddingTop: 48,
    paddingHorizontal: 16,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: '#00FFCC',
    marginBottom: 16,
    textAlign: 'center',
  },
  searchBar: {
    backgroundColor: '#1A1A1A',
    borderWidth: 1,
    borderColor: '#00FFCC',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: '#FFFFFF',
    fontSize: 15,
    marginBottom: 12,
  },
  listContent: {
    paddingBottom: 8,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 4,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#222',
  },
  icon: {
    width: 40,
    height: 40,
    borderRadius: 8,
  },
  iconPlaceholder: {
    width: 40,
    height: 40,
    borderRadius: 8,
    backgroundColor: '#2A2A2A',
  },
  rowText: {
    flex: 1,
    marginLeft: 12,
    justifyContent: 'center',
  },
  appLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: '#FFFFFF',
  },
  packageName: {
    fontSize: 12,
    color: '#888',
    marginTop: 2,
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  errorText: {
    color: '#FF4444',
    fontSize: 14,
    textAlign: 'center',
    marginBottom: 16,
  },
  retryButton: {
    paddingHorizontal: 24,
    paddingVertical: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#00FFCC',
  },
  retryLabel: {
    color: '#00FFCC',
    fontSize: 14,
    fontWeight: '600',
  },
  cancelButton: {
    alignItems: 'center',
    paddingVertical: 14,
    marginTop: 8,
    marginBottom: 24,
    borderRadius: 8,
    backgroundColor: '#2A2A2A',
  },
  cancelLabel: {
    color: '#888',
    fontSize: 16,
    fontWeight: '600',
  },
});

export default AssignAppModal;
