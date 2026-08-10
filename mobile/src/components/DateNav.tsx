import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { addDays, format, parseISO } from 'date-fns';
import { Ionicons } from '@expo/vector-icons';
import { radii, spacing, useThemedStyles, type ThemeColors } from '../theme';
import { DATE_FMT, todayStr } from '../utils/dates';

interface Props {
  date: string;
  onNavigate: (date: string) => void;
}

export function DateNav({ date, onNavigate }: Props) {
  const { colors, styles } = useThemedStyles(makeStyles);
  const [pickerOpen, setPickerOpen] = useState(false);
  const current = parseISO(date);
  const isToday = date === todayStr();

  const go = (d: Date) => onNavigate(format(d, DATE_FMT));

  return (
    <View style={styles.row}>
      <Pressable onPress={() => go(addDays(current, -1))} hitSlop={8} style={styles.navBtn}>
        <Ionicons name="chevron-back" size={20} color={colors.primary} />
      </Pressable>

      <Pressable onPress={() => setPickerOpen(true)} style={styles.center}>
        <Text style={styles.dateLabel}>{format(current, 'EEEE, MMMM d, yyyy')}</Text>
        {!isToday && <Text style={styles.jumpHint}>Tap to jump to a date</Text>}
      </Pressable>

      <View style={styles.side}>
        {!isToday && (
          <Pressable onPress={() => go(new Date())} hitSlop={8} style={styles.todayBtn}>
            <Text style={styles.todayBtnText}>Today</Text>
          </Pressable>
        )}
        <Pressable onPress={() => go(addDays(current, 1))} hitSlop={8} style={styles.navBtn}>
          <Ionicons name="chevron-forward" size={20} color={colors.primary} />
        </Pressable>
      </View>

      {pickerOpen && (
        <DateTimePicker
          value={current}
          mode="date"
          display="default"
          onChange={(event, selected) => {
            setPickerOpen(false);
            if (event.type === 'set' && selected) go(selected);
          }}
        />
      )}
    </View>
  );
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
  },
  navBtn: { padding: spacing.xs },
  center: { flex: 1, alignItems: 'center' },
  dateLabel: { fontSize: 14, fontWeight: '700', color: colors.text, textAlign: 'center' },
  jumpHint: { fontSize: 10, color: colors.textMuted, marginTop: 2 },
  side: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  todayBtn: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.pill,
    paddingHorizontal: spacing.sm,
    paddingVertical: 4,
  },
  todayBtnText: { fontSize: 12, fontWeight: '700', color: colors.primary },
});
