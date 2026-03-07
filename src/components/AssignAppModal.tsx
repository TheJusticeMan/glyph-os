/**
 * AssignAppModal.tsx
 *
 * Full-screen modal that lets the user search their installed apps and bind
 * one to a gesture.  Consuming components receive the selected `AppDetail`
 * via the `onAssign` callback.
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
import { filterApps, getIconUri } from '../services/InstalledAppsService';
import useInstalledApps from '../hooks/useInstalledApps';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface AssignAppModalProps {
  visible: boolean;
  onAssign: (app: AppDetail) => void;
  onCancel: () => void;
  prioritizedPackageNames?: string[];
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
  const [iconFailed, setIconFailed] = useState(false);
  const iconUri = getIconUri(app.icon);

  return (
    <TouchableOpacity style={styles.row} onPress={handlePress} activeOpacity={0.7}>
      {iconUri && !iconFailed ? (
        <Image
          source={{ uri: iconUri }}
          style={styles.icon}
          resizeMode="contain"
          onError={() => setIconFailed(true)}
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

const AssignAppModal: React.FC<AssignAppModalProps> = ({
  visible,
  onAssign,
  onCancel,
  prioritizedPackageNames = [],
}) => {
  const [query, setQuery] = useState<string>('');
  const searchInputRef = useRef<TextInput | null>(null);
  const { apps, loading, error, refresh } = useInstalledApps();

  // Reset search query each time the modal opens.
  useEffect(() => {
    if (visible) {
      setQuery('');
    }
  }, [visible]);

  const focusSearchInput = useCallback(() => {
    // Modal mount/animation timing differs by platform; schedule a couple of
    // focus attempts so the soft keyboard appears reliably after opening.
    const requestIdle = (globalThis as any).requestIdleCallback as
      | ((callback: () => void) => number)
      | undefined;
    const cancelIdle = (globalThis as any).cancelIdleCallback as
      | ((id: number) => void)
      | undefined;

    const idleId = requestIdle?.(() => {
      searchInputRef.current?.focus();
    });
    const idleFallbackTimeoutId = idleId === undefined
      ? setTimeout(() => {
          searchInputRef.current?.focus();
        }, 0)
      : null;

    const frameId = requestAnimationFrame(() => {
      searchInputRef.current?.focus();
    });

    const timeoutId = setTimeout(() => {
      searchInputRef.current?.focus();
    }, 120);

    return () => {
      if (idleId !== undefined && cancelIdle) {
        cancelIdle(idleId);
      }
      if (idleFallbackTimeoutId !== null) {
        clearTimeout(idleFallbackTimeoutId);
      }
      cancelAnimationFrame(frameId);
      clearTimeout(timeoutId);
    };
  }, []);

  useEffect(() => {
    if (!visible) return;
    return focusSearchInput();
  }, [visible, focusSearchInput]);

  const priorityOrder = useMemo(() => {
    const map = new Map<string, number>();
    prioritizedPackageNames.forEach((name, index) => {
      map.set(name, index);
    });
    return map;
  }, [prioritizedPackageNames]);

  const filteredApps = useMemo(() => {
    const base = filterApps(apps, query);
    return [...base].sort((a, b) => {
      const rankA = priorityOrder.get(a.packageName) ?? Number.POSITIVE_INFINITY;
      const rankB = priorityOrder.get(b.packageName) ?? Number.POSITIVE_INFINITY;
      if (rankA !== rankB) {
        return rankA - rankB;
      }
      return 0;
    });
  }, [apps, query, priorityOrder]);

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
        keyboardDismissMode="on-drag"
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
      onShow={focusSearchInput}
      statusBarTranslucent
    >
      <View style={styles.container}>
        <Text style={styles.title}>Assign App to Gesture</Text>

        <TextInput
          ref={searchInputRef}
          style={styles.searchBar}
          placeholder="Search apps…"
          placeholderTextColor="#555"
          value={query}
          onChangeText={setQuery}
          autoCorrect={false}
          autoCapitalize="none"
          returnKeyType="search"
          autoFocus
        />

        {query.trim() === '' && prioritizedPackageNames.length > 0 && (
          <Text style={styles.hintText}>Top 5 similar apps are pinned first</Text>
        )}

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
  hintText: {
    color: '#6F6F6F',
    fontSize: 12,
    marginBottom: 8,
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
