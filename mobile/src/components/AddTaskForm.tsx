import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { Ionicons } from '@expo/vector-icons';
import type { Recurrence, TaskRequest } from '../api/types';
import { radii, spacing, useThemedStyles, type ThemeColors } from '../theme';
import { ensureNotificationPermission } from '../notifications/alarms';

const RECURRENCE_OPTIONS: { value: Recurrence; label: string }[] = [
  { value: 'NONE', label: 'One-off' },
  { value: 'DAILY', label: 'Daily' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'MONTHLY', label: 'Monthly' },
  { value: 'YEARLY', label: 'Yearly' },
];

interface Props {
  onSubmit: (payload: TaskRequest) => Promise<void>;
}

export function AddTaskForm({ onSubmit }: Props) {
  const { colors, styles } = useThemedStyles(makeStyles);
  const [title, setTitle] = useState('');
  const [points, setPoints] = useState('1');
  const [scheduledTime, setScheduledTime] = useState<string | null>(null);
  const [recurrence, setRecurrence] = useState<Recurrence>('NONE');
  const [timePickerOpen, setTimePickerOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const reset = () => {
    setTitle('');
    setPoints('1');
    setScheduledTime(null);
    setRecurrence('NONE');
  };

  async function handleSubmit() {
    const trimmed = title.trim();
    if (!trimmed || submitting) return;
    setSubmitting(true);
    try {
      await onSubmit({
        title: trimmed,
        points: Number(points) || 1,
        scheduledTime,
        recurrence,
      });
      reset();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <View style={styles.card}>
      <TextInput
        style={styles.titleInput}
        placeholder="Add a new task…"
        placeholderTextColor={colors.textMuted}
        value={title}
        onChangeText={setTitle}
        maxLength={200}
      />

      <View style={styles.rowInputs}>
        <View style={styles.pointsField}>
          <Text style={styles.smallLabel}>Points</Text>
          <TextInput
            style={styles.pointsInput}
            value={points}
            onChangeText={setPoints}
            keyboardType="number-pad"
            maxLength={5}
          />
        </View>

        <Pressable style={styles.timeField} onPress={() => setTimePickerOpen(true)}>
          <Text style={styles.smallLabel}>Reminder</Text>
          <View style={styles.timeValueRow}>
            <Ionicons name="alarm-outline" size={16} color={colors.primary} />
            <Text style={styles.timeValue}>{scheduledTime ? scheduledTime.slice(0, 5) : 'None'}</Text>
            {scheduledTime && (
              <Pressable onPress={() => setScheduledTime(null)} hitSlop={8}>
                <Ionicons name="close-circle" size={16} color={colors.textMuted} />
              </Pressable>
            )}
          </View>
        </Pressable>
      </View>

      <Text style={styles.smallLabel}>Repeats</Text>
      <View style={styles.chipRow}>
        {RECURRENCE_OPTIONS.map((opt) => (
          <Pressable
            key={opt.value}
            onPress={() => setRecurrence(opt.value)}
            style={[styles.chip, recurrence === opt.value && styles.chipActive]}
          >
            <Text style={[styles.chipText, recurrence === opt.value && styles.chipTextActive]}>{opt.label}</Text>
          </Pressable>
        ))}
      </View>

      <Pressable
        onPress={handleSubmit}
        disabled={!title.trim() || submitting}
        style={[styles.submit, (!title.trim() || submitting) && styles.submitDisabled]}
      >
        <Text style={styles.submitText}>{submitting ? 'Adding…' : 'Add task'}</Text>
      </Pressable>

      {timePickerOpen && (
        <DateTimePicker
          value={new Date()}
          mode="time"
          display="default"
          onChange={(event, selected) => {
            setTimePickerOpen(false);
            if (event.type === 'set' && selected) {
              const hh = String(selected.getHours()).padStart(2, '0');
              const mm = String(selected.getMinutes()).padStart(2, '0');
              setScheduledTime(`${hh}:${mm}:00`);
              // Fired from the picker's "set" tap - a user gesture, so the OS permission prompt
              // (first time only) won't be silently ignored.
              ensureNotificationPermission();
            }
          }}
        />
      )}
    </View>
  );
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  card: {
    backgroundColor: colors.card,
    borderRadius: radii.lg,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
    marginTop: spacing.md,
  },
  titleInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm + 2,
    fontSize: 16,
    color: colors.text,
    marginBottom: spacing.md,
  },
  rowInputs: { flexDirection: 'row', gap: spacing.md, marginBottom: spacing.md },
  pointsField: { width: 80 },
  timeField: { flex: 1 },
  smallLabel: { fontSize: 11, fontWeight: '700', color: colors.textMuted, marginBottom: spacing.xs, textTransform: 'uppercase' },
  pointsInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.sm,
    fontSize: 15,
    color: colors.text,
    textAlign: 'center',
  },
  timeValueRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.sm,
  },
  timeValue: { fontSize: 14, color: colors.text, flex: 1 },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.xs, marginBottom: spacing.md },
  chip: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.pill,
    paddingHorizontal: spacing.sm + 2,
    paddingVertical: 6,
  },
  chipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { fontSize: 12, color: colors.textMuted, fontWeight: '600' },
  chipTextActive: { color: '#fff' },
  submit: {
    backgroundColor: colors.primary,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  submitDisabled: { opacity: 0.5 },
  submitText: { color: '#fff', fontSize: 15, fontWeight: '700' },
});
