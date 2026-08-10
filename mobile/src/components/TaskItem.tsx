import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { TaskDto } from '../api/types';
import { radii, spacing, useThemedStyles, type ThemeColors } from '../theme';

const RECURRENCE_LABELS: Record<string, string | null> = {
  NONE: null,
  DAILY: 'daily',
  WEEKLY: 'weekly',
  MONTHLY: 'monthly',
  YEARLY: 'yearly',
};

interface Props {
  task: TaskDto;
  isActive: boolean;
  onDrag: () => void;
  onToggle: (taskId: string, completed: boolean) => void;
  onPoints: (taskId: string, value: number) => void;
  onTitle: (taskId: string, title: string) => void;
  onDelete: (taskId: string) => void;
  onCopyToNextDay: (task: TaskDto) => Promise<void> | void;
}

export function TaskItem({
  task,
  isActive,
  onDrag,
  onToggle,
  onPoints,
  onTitle,
  onDelete,
  onCopyToNextDay,
}: Props) {
  const { colors, styles } = useThemedStyles(makeStyles);
  const [points, setPoints] = useState(String(task.points));
  const [editingTitle, setEditingTitle] = useState(false);
  const [title, setTitle] = useState(task.title);
  const [copying, setCopying] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const confirmTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => setPoints(String(task.points)), [task.points]);
  useEffect(() => setTitle(task.title), [task.title]);
  useEffect(() => () => {
    if (confirmTimeoutRef.current) clearTimeout(confirmTimeoutRef.current);
  }, []);

  const commitPoints = () => {
    const parsed = Math.max(0, Number.isFinite(+points) ? Math.floor(+points) : task.points);
    if (parsed !== task.points) onPoints(task.id, parsed);
    else setPoints(String(task.points));
  };

  const commitTitle = () => {
    const trimmed = title.trim();
    setEditingTitle(false);
    if (trimmed && trimmed !== task.title) onTitle(task.id, trimmed);
    else setTitle(task.title);
  };

  const handleCopyPress = async () => {
    if (copying) return;
    setCopying(true);
    try {
      await onCopyToNextDay(task);
    } finally {
      setCopying(false);
    }
  };

  const handleDeletePress = () => {
    if (!confirmDelete) {
      setConfirmDelete(true);
      confirmTimeoutRef.current = setTimeout(() => setConfirmDelete(false), 3000);
      return;
    }
    if (confirmTimeoutRef.current) clearTimeout(confirmTimeoutRef.current);
    setConfirmDelete(false);
    onDelete(task.id);
  };

  const recLabel = RECURRENCE_LABELS[task.recurrence];

  return (
    <View style={[styles.row, isActive && styles.rowActive, task.completed && styles.rowCompleted]}>
      <Pressable onLongPress={onDrag} delayLongPress={150} hitSlop={8} style={styles.dragHandle}>
        <Ionicons name="reorder-three-outline" size={22} color={colors.textMuted} />
      </Pressable>

      <Pressable
        onPress={() => onToggle(task.id, !task.completed)}
        hitSlop={8}
        style={[styles.checkbox, task.completed && styles.checkboxChecked]}
      >
        {task.completed && <Ionicons name="checkmark" size={16} color="#fff" />}
      </Pressable>

      <View style={styles.titleCell}>
        {editingTitle ? (
          <TextInput
            style={styles.titleInput}
            value={title}
            onChangeText={setTitle}
            onBlur={commitTitle}
            onSubmitEditing={commitTitle}
            autoFocus
            maxLength={200}
            selectTextOnFocus
          />
        ) : (
          <Pressable onPress={() => setEditingTitle(true)}>
            <Text style={[styles.title, task.completed && styles.titleCompleted]} numberOfLines={2}>
              {task.title}
            </Text>
          </Pressable>
        )}
        {(task.scheduledTime || recLabel) && (
          <View style={styles.badgeRow}>
            {task.scheduledTime && (
              <Text style={styles.badge}>⏰ {task.scheduledTime.slice(0, 5)}</Text>
            )}
            {recLabel && <Text style={styles.badge}>↻ {recLabel}</Text>}
          </View>
        )}
      </View>

      <TextInput
        style={styles.pointsInput}
        value={points}
        onChangeText={setPoints}
        onBlur={commitPoints}
        onSubmitEditing={commitPoints}
        keyboardType="number-pad"
        maxLength={5}
      />

      <Pressable onPress={handleCopyPress} disabled={copying} hitSlop={8} style={styles.iconButton}>
        <Ionicons name="arrow-redo-outline" size={20} color={colors.primary} />
      </Pressable>

      <Pressable onPress={handleDeletePress} hitSlop={8} style={styles.iconButton}>
        <Ionicons
          name={confirmDelete ? 'help-circle-outline' : 'close-circle-outline'}
          size={22}
          color={confirmDelete ? colors.error : colors.textMuted}
        />
      </Pressable>
    </View>
  );
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.card,
    borderRadius: radii.md,
    padding: spacing.sm,
    marginBottom: spacing.sm,
    borderWidth: 1,
    borderColor: colors.border,
    gap: spacing.sm,
  },
  rowActive: { opacity: 0.7, borderColor: colors.primary },
  rowCompleted: { backgroundColor: colors.progressTrack },
  dragHandle: { padding: spacing.xs },
  checkbox: {
    width: 24,
    height: 24,
    borderRadius: radii.sm,
    borderWidth: 2,
    borderColor: colors.primaryLight,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkboxChecked: { backgroundColor: colors.primary, borderColor: colors.primary },
  titleCell: { flex: 1, minWidth: 0 },
  title: { fontSize: 15, color: colors.text, fontWeight: '600' },
  titleCompleted: { textDecorationLine: 'line-through', color: colors.textMuted },
  titleInput: {
    fontSize: 15,
    color: colors.text,
    borderBottomWidth: 1,
    borderBottomColor: colors.primary,
    paddingVertical: 2,
  },
  badgeRow: { flexDirection: 'row', gap: spacing.xs, marginTop: 2 },
  badge: { fontSize: 11, color: colors.textMuted, backgroundColor: colors.progressTrack, borderRadius: radii.sm, paddingHorizontal: 6, paddingVertical: 2 },
  pointsInput: {
    width: 44,
    textAlign: 'center',
    fontSize: 14,
    color: colors.text,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    paddingVertical: 4,
  },
  iconButton: { padding: spacing.xs },
});
