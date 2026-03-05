/**
 * AppPicker.tsx
 *
 * Full-screen modal that lists installed launchable apps and lets the user
 * pick one to bind to a gesture.  Includes a live search/filter field.
 */

import React, { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Image,
  ListRenderItem,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

import type { AppInfo } from '../hooks/useInstalledApps';

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface Props {
  apps: AppInfo[];
  isLoading: boolean;
  /** Called with the chosen app, or null if the user cancels. */
  onPick: (app: AppInfo | null) => void;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export const AppPicker: React.FC<Props> = ({ apps, isLoading, onPick }) => {
  const [query, setQuery] = useState('');

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return apps;
    return apps.filter(
      (a) => a.label.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q)
    );
  }, [apps, query]);

  const renderItem: ListRenderItem<AppInfo> = ({ item }) => (
    <TouchableOpacity style={styles.row} onPress={() => onPick(item)} activeOpacity={0.7}>
      {item.icon ? (
        <Image source={{ uri: `data:image/png;base64,${item.icon}` }} style={styles.icon} />
      ) : (
        <View style={[styles.icon, styles.iconPlaceholder]} />
      )}
      <View style={styles.labelContainer}>
        <Text style={styles.label} numberOfLines={1}>
          {item.label}
        </Text>
        <Text style={styles.pkg} numberOfLines={1}>
          {item.packageName}
        </Text>
      </View>
    </TouchableOpacity>
  );

  return (
    <View style={styles.root}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.title}>Choose an App</Text>
        <TouchableOpacity onPress={() => onPick(null)} hitSlop={HIT_SLOP}>
          <Text style={styles.cancel}>Cancel</Text>
        </TouchableOpacity>
      </View>

      {/* Search */}
      <TextInput
        style={styles.search}
        placeholder="Search apps…"
        placeholderTextColor="#666"
        value={query}
        onChangeText={setQuery}
        autoFocus
        returnKeyType="search"
      />

      {/* List */}
      {isLoading ? (
        <ActivityIndicator color="#00FFCC" size="large" style={styles.loader} />
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={(item) => item.packageName}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          keyboardShouldPersistTaps="handled"
          ListEmptyComponent={<Text style={styles.empty}>No apps found.</Text>}
        />
      )}
    </View>
  );
};

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const HIT_SLOP = { top: 12, bottom: 12, left: 12, right: 12 };

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
    fontSize: 20,
    fontWeight: '700',
  },
  cancel: {
    color: '#00FFCC',
    fontSize: 16,
  },
  search: {
    margin: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
    backgroundColor: '#1A1A1A',
    borderRadius: 10,
    color: '#FFFFFF',
    fontSize: 16,
    borderWidth: 1,
    borderColor: '#333',
  },
  loader: {
    marginTop: 40,
  },
  list: {
    paddingHorizontal: 12,
    paddingBottom: 24,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#1A1A1A',
  },
  icon: {
    width: 44,
    height: 44,
    borderRadius: 10,
    marginRight: 14,
  },
  iconPlaceholder: {
    backgroundColor: '#222',
  },
  labelContainer: {
    flex: 1,
  },
  label: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '500',
  },
  pkg: {
    color: '#666',
    fontSize: 12,
    marginTop: 2,
  },
  empty: {
    color: '#666',
    textAlign: 'center',
    marginTop: 40,
    fontSize: 15,
  },
});
