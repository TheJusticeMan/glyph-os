import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  LayoutChangeEvent,
  Modal,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Svg, { Path } from 'react-native-svg';

import { blendNormalizedPaths } from '../utils/GestureMatcher';
import type { Point } from '../utils/GestureNormalizer';

export type MergeDecision = 'merge' | 'create';

interface MergeGestureDialogProps {
  visible: boolean;
  appLabel: string;
  oldPath: Point[];
  newPath: Point[];
  minT: number;
  maxT: number;
  initialT: number;
  onCancel: () => void;
  onConfirm: (decision: MergeDecision, t: number) => void;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

const PREVIEW_SIZE = 56;
const PREVIEW_PADDING = 6;

function buildPreviewPath(points: Point[]): string {
  if (points.length < 2) return '';

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  for (const p of points) {
    if (p.x < minX) minX = p.x;
    if (p.y < minY) minY = p.y;
    if (p.x > maxX) maxX = p.x;
    if (p.y > maxY) maxY = p.y;
  }

  const width = maxX - minX || 1;
  const height = maxY - minY || 1;
  const available = PREVIEW_SIZE - PREVIEW_PADDING * 2;
  const scale = available / Math.max(width, height);
  const offsetX = PREVIEW_PADDING + (available - width * scale) / 2;
  const offsetY = PREVIEW_PADDING + (available - height * scale) / 2;

  return points
    .map((p, i) => {
      const x = (p.x - minX) * scale + offsetX;
      const y = (p.y - minY) * scale + offsetY;
      return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
    })
    .join(' ');
}

interface GesturePreviewProps {
  title: string;
  points: Point[];
}

const GesturePreview: React.FC<GesturePreviewProps> = ({ title, points }) => {
  const d = useMemo(() => buildPreviewPath(points), [points]);

  return (
    <View style={styles.previewColumn}>
      <Text style={styles.previewTitle}>{title}</Text>
      <View style={styles.previewBox}>
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
          <Text style={styles.previewEmpty}>-</Text>
        )}
      </View>
    </View>
  );
};

const MergeGestureDialog: React.FC<MergeGestureDialogProps> = ({
  visible,
  appLabel,
  oldPath,
  newPath,
  minT,
  maxT,
  initialT,
  onCancel,
  onConfirm,
}) => {
  const [decision, setDecision] = useState<MergeDecision>('merge');
  const [sliderWidth, setSliderWidth] = useState(0);
  const [value, setValue] = useState(() => clamp(initialT, minT, maxT));
  const trackLeftPageXRef = React.useRef(0);

  const boundedInitial = useMemo(() => clamp(initialT, minT, maxT), [initialT, minT, maxT]);

  useEffect(() => {
    if (!visible) return;
    setDecision('merge');
    setValue(boundedInitial);
  }, [visible, boundedInitial]);

  const handleTrackLayout = useCallback((evt: LayoutChangeEvent) => {
    // onLayout gives x relative to parent, not screen; we still cache this
    // and keep a dynamic page-space calibration on grant.
    setSliderWidth(evt.nativeEvent.layout.width);
  }, []);

  const setValueFromX = useCallback(
    (x: number) => {
      if (sliderWidth <= 0) return;
      const normalized = clamp(x / sliderWidth, 0, 1);
      const next = minT + normalized * (maxT - minT);
      setValue(clamp(next, minT, maxT));
    },
    [sliderWidth, minT, maxT],
  );

  const handleResponderStart = useCallback((x: number, pageX: number) => {
    trackLeftPageXRef.current = pageX - x;
    setValueFromX(x);
    return true;
  }, [setValueFromX]);

  const handleResponderMove = useCallback((pageX: number) => {
    const localX = pageX - trackLeftPageXRef.current;
    setValueFromX(localX);
  }, [setValueFromX]);

  const progress = maxT > minT ? (value - minT) / (maxT - minT) : 0;
  const mergedPath = useMemo(() => blendNormalizedPaths(oldPath, newPath, value), [oldPath, newPath, value]);

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <View style={styles.backdrop}>
        <View style={styles.card}>
          <Text style={styles.title}>Gesture Conflict Found</Text>
          <Text style={styles.subtitle}>
            {appLabel} already has a gesture. Merge the old and new swipe, or keep both bindings.
          </Text>

          <View style={styles.previewsRow}>
            <GesturePreview title="Old" points={oldPath} />
            <GesturePreview title="Merged" points={mergedPath} />
            <GesturePreview title="New" points={newPath} />
          </View>

          <View style={styles.choiceRow}>
            <TouchableOpacity
              style={[styles.choiceButton, decision === 'merge' && styles.choiceButtonActive]}
              onPress={() => setDecision('merge')}
              activeOpacity={0.7}
            >
              <Text style={[styles.choiceLabel, decision === 'merge' && styles.choiceLabelActive]}>
                Merge
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.choiceButton, decision === 'create' && styles.choiceButtonActive]}
              onPress={() => setDecision('create')}
              activeOpacity={0.7}
            >
              <Text style={[styles.choiceLabel, decision === 'create' && styles.choiceLabelActive]}>
                Create Another
              </Text>
            </TouchableOpacity>
          </View>

          <Text style={styles.sliderTitle}>Merge Amount: {(value * 100).toFixed(0)}%</Text>
          <View
            style={styles.sliderTrack}
            onLayout={handleTrackLayout}
            onStartShouldSetResponder={() => true}
            onMoveShouldSetResponder={() => true}
            onResponderGrant={(evt) =>
              handleResponderStart(evt.nativeEvent.locationX, evt.nativeEvent.pageX)
            }
            onResponderMove={(evt) => handleResponderMove(evt.nativeEvent.pageX)}
          >
            <View
              pointerEvents="none"
              style={[styles.sliderFill, { width: `${progress * 100}%` }]}
            />
            <View
              pointerEvents="none"
              style={[styles.sliderThumb, { left: `${progress * 100}%` }]}
            />
          </View>
          <View style={styles.sliderLabels}>
            <Text style={styles.sliderLabel}>{(minT * 100).toFixed(0)}%</Text>
            <Text style={styles.sliderLabel}>{(((minT + maxT) / 2) * 100).toFixed(0)}%</Text>
            <Text style={styles.sliderLabel}>{(maxT * 100).toFixed(0)}%</Text>
          </View>

          <View style={styles.actions}>
            <TouchableOpacity onPress={onCancel} style={styles.cancelButton} activeOpacity={0.7}>
              <Text style={styles.cancelLabel}>Cancel</Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={() => onConfirm(decision, value)}
              style={styles.confirmButton}
              activeOpacity={0.7}
            >
              <Text style={styles.confirmLabel}>Continue</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: '#00000099',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 20,
  },
  card: {
    width: '100%',
    borderRadius: 14,
    backgroundColor: '#131313',
    borderWidth: 1,
    borderColor: '#262626',
    padding: 16,
  },
  title: {
    color: '#00FFCC',
    fontSize: 20,
    fontWeight: '700',
  },
  subtitle: {
    marginTop: 8,
    color: '#CCCCCC',
    lineHeight: 20,
  },
  previewsRow: {
    marginTop: 14,
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 8,
  },
  previewColumn: {
    flex: 1,
    alignItems: 'center',
  },
  previewTitle: {
    color: '#9A9A9A',
    fontSize: 11,
    fontWeight: '600',
    marginBottom: 6,
  },
  previewBox: {
    width: PREVIEW_SIZE,
    height: PREVIEW_SIZE,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#2A2A2A',
    backgroundColor: '#0D0D0D',
    alignItems: 'center',
    justifyContent: 'center',
  },
  previewEmpty: {
    color: '#555555',
    fontSize: 16,
  },
  choiceRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 16,
    marginBottom: 12,
  },
  choiceButton: {
    flex: 1,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#3A3A3A',
    paddingVertical: 10,
    alignItems: 'center',
  },
  choiceButtonActive: {
    borderColor: '#00FFCC',
    backgroundColor: '#00FFCC22',
  },
  choiceLabel: {
    color: '#9A9A9A',
    fontWeight: '600',
  },
  choiceLabelActive: {
    color: '#00FFCC',
  },
  sliderTitle: {
    color: '#E5E5E5',
    fontSize: 13,
    marginBottom: 8,
  },
  sliderTrack: {
    height: 30,
    borderRadius: 15,
    backgroundColor: '#2A2A2A',
    overflow: 'hidden',
    justifyContent: 'center',
  },
  sliderFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    backgroundColor: '#00FFCC33',
  },
  sliderThumb: {
    position: 'absolute',
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: '#00FFCC',
    marginLeft: -9,
    top: 6,
  },
  sliderLabels: {
    marginTop: 6,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  sliderLabel: {
    color: '#888888',
    fontSize: 11,
  },
  actions: {
    flexDirection: 'row',
    marginTop: 18,
    gap: 10,
  },
  cancelButton: {
    flex: 1,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#3A3A3A',
    paddingVertical: 11,
    alignItems: 'center',
  },
  cancelLabel: {
    color: '#A5A5A5',
    fontWeight: '600',
  },
  confirmButton: {
    flex: 1,
    borderRadius: 10,
    backgroundColor: '#00FFCC',
    paddingVertical: 11,
    alignItems: 'center',
  },
  confirmLabel: {
    color: '#04251E',
    fontWeight: '700',
  },
});

export default MergeGestureDialog;
