import React from 'react';
import { ReactTestRenderer, act, create } from 'react-test-renderer';

import App from '../../App';
import { blendNormalizedPaths, SavedGesture } from '../utils/GestureMatcher';
import { NUM_POINTS, Point } from '../utils/GestureNormalizer';

const mockLoadGestures = jest.fn();
const mockSaveGestures = jest.fn();
const mockClearGestures = jest.fn();
const mockLaunchApplication = jest.fn();

let latestGestureCanvasProps: any = null;
let latestAssignModalProps: any = null;
let latestMergeDialogProps: any = null;
let renderer: ReactTestRenderer | null = null;

jest.mock('@react-native-async-storage/async-storage', () => {
  const asyncStorageMock = require('@react-native-async-storage/async-storage/jest/async-storage-mock');
  return {
    ...asyncStorageMock,
    getItem: jest.fn(async (key: string) => {
      if (key === 'glyph_os_onboarding_done') return 'true';
      if (key === 'glyph_os_launch_on_create_shortcut') return 'false';
      if (key === 'glyph_os_trail_effect') return 'false';
      return null;
    }),
  };
});

jest.mock('../../src/utils/GestureStorage', () => ({
  loadGestures: (...args: unknown[]) => mockLoadGestures(...args),
  saveGestures: (...args: unknown[]) => mockSaveGestures(...args),
  clearGestures: (...args: unknown[]) => mockClearGestures(...args),
}));

jest.mock('react-native-launcher-kit', () => ({
  RNLauncherKitHelper: {
    launchApplication: (...args: unknown[]) => mockLaunchApplication(...args),
  },
}));

jest.mock('../../src/components/GestureCanvas', () => {
  const React = require('react');
  return function MockGestureCanvas(props: unknown) {
    latestGestureCanvasProps = props;
    return React.createElement('MockGestureCanvas');
  };
});

jest.mock('../../src/components/AssignAppModal', () => {
  const React = require('react');
  return function MockAssignAppModal(props: unknown) {
    latestAssignModalProps = props;
    return React.createElement('MockAssignAppModal');
  };
});

jest.mock('../../src/components/MergeGestureDialog', () => {
  const React = require('react');
  return function MockMergeGestureDialog(props: unknown) {
    latestMergeDialogProps = props;
    return React.createElement('MockMergeGestureDialog');
  };
});

jest.mock('../../src/components/GestureManagementScreen', () => {
  const React = require('react');
  return function MockGestureManagementScreen() {
    return React.createElement('MockGestureManagementScreen');
  };
});

jest.mock('../../src/components/OnboardingScreen', () => {
  const React = require('react');
  return function MockOnboardingScreen() {
    return React.createElement('MockOnboardingScreen');
  };
});

jest.mock('../../src/components/FeedbackOverlay', () => {
  const React = require('react');
  return function MockFeedbackOverlay() {
    return React.createElement('MockFeedbackOverlay');
  };
});

jest.mock('react-native-safe-area-context', () => {
  const React = require('react');
  return {
    SafeAreaProvider: ({ children }: { children: React.ReactNode }) =>
      React.createElement(React.Fragment, null, children),
    SafeAreaView: ({ children }: { children: React.ReactNode }) =>
      React.createElement(React.Fragment, null, children),
  };
});

jest.mock('expo-status-bar', () => ({
  StatusBar: () => null,
}));

function makePath(offsetY = 0): Point[] {
  return Array.from({ length: NUM_POINTS }, (_, i) => ({
    x: (i / (NUM_POINTS - 1)) * 100,
    y: offsetY,
  }));
}

async function flushPromises() {
  await act(async () => {
    await Promise.resolve();
  });
}

describe('App merge decision flow', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
    latestGestureCanvasProps = null;
    latestAssignModalProps = null;
    latestMergeDialogProps = null;
    renderer = null;
  });

  afterEach(() => {
    if (renderer) {
      act(() => {
        renderer?.unmount();
      });
      renderer = null;
    }
    act(() => {
      jest.runOnlyPendingTimers();
    });
    jest.useRealTimers();
  });

  it('appends a new binding when merge dialog chooses create', async () => {
    const oldPath = makePath(0);
    const incomingPath = makePath(0.15);
    const initialGestures: SavedGesture[] = [
      { label: 'existing', packageName: 'com.example.target', normalizedPath: oldPath },
      { label: 'other', packageName: 'com.example.other', normalizedPath: makePath(10) },
    ];

    mockLoadGestures.mockResolvedValue(initialGestures);

    await act(async () => {
      renderer = create(<App />);
    });
    await flushPromises();

    await act(async () => {
      latestGestureCanvasProps.onRequestAssignApp('incoming', incomingPath);
    });

    const targetApp = { label: 'Target App', packageName: 'com.example.target' };
    await act(async () => {
      latestAssignModalProps.onAssign(targetApp);
    });

    expect(latestMergeDialogProps.visible).toBe(true);

    await act(async () => {
      latestMergeDialogProps.onConfirm('create', 0.5);
    });

    const nextGestures: SavedGesture[] = latestGestureCanvasProps.savedGestures;
    expect(nextGestures).toHaveLength(initialGestures.length + 1);

    const appended = nextGestures.find((g) => g.label === 'incoming');
    expect(appended).toBeDefined();
    expect(appended?.packageName).toBe('com.example.target');

    const existing = nextGestures.find((g) => g.label === 'existing');
    expect(existing?.normalizedPath).toEqual(oldPath);
  });

  it('replaces existing binding path when merge dialog chooses merge', async () => {
    const oldPath = makePath(0);
    const incomingPath = makePath(0.15);
    const initialGestures: SavedGesture[] = [
      { label: 'existing', packageName: 'com.example.target', normalizedPath: oldPath },
      { label: 'other', packageName: 'com.example.other', normalizedPath: makePath(10) },
    ];

    mockLoadGestures.mockResolvedValue(initialGestures);

    await act(async () => {
      renderer = create(<App />);
    });
    await flushPromises();

    await act(async () => {
      latestGestureCanvasProps.onRequestAssignApp('incoming', incomingPath);
    });

    const targetApp = { label: 'Target App', packageName: 'com.example.target' };
    await act(async () => {
      latestAssignModalProps.onAssign(targetApp);
    });

    expect(latestMergeDialogProps.visible).toBe(true);

    await act(async () => {
      latestMergeDialogProps.onConfirm('merge', 0.5);
    });

    const nextGestures: SavedGesture[] = latestGestureCanvasProps.savedGestures;
    expect(nextGestures).toHaveLength(initialGestures.length);
    expect(nextGestures.find((g) => g.label === 'incoming')).toBeUndefined();

    const existing = nextGestures.find((g) => g.label === 'existing');
    expect(existing).toBeDefined();

    const expectedBlended = blendNormalizedPaths(oldPath, incomingPath, 0.5);
    expect(existing?.normalizedPath).toEqual(expectedBlended);
  });
});
