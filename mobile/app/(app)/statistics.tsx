import { useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LineChart } from 'react-native-gifted-charts';
import {
  eachDayOfInterval,
  endOfMonth,
  endOfQuarter,
  endOfWeek,
  endOfYear,
  format,
  parseISO,
  startOfMonth,
  startOfQuarter,
  startOfWeek,
  startOfYear,
} from 'date-fns';
import { useStats } from '@/src/hooks/useStats';
import { ErrorBanner } from '@/src/components/Banner';
import { radii, spacing, useThemedStyles, type ThemeColors } from '@/src/theme';
import { DATE_FMT } from '@/src/utils/dates';

type RangeKey = 'week' | 'month' | 'quarter' | 'year';

const RANGES: { key: RangeKey; label: string; start: (d: Date) => Date; end: (d: Date) => Date }[] = [
  { key: 'week', label: 'Week', start: (d) => startOfWeek(d, { weekStartsOn: 1 }), end: (d) => endOfWeek(d, { weekStartsOn: 1 }) },
  { key: 'month', label: 'Month', start: startOfMonth, end: endOfMonth },
  { key: 'quarter', label: 'Quarter', start: startOfQuarter, end: endOfQuarter },
  { key: 'year', label: 'Year', start: startOfYear, end: endOfYear },
];

export default function StatisticsScreen() {
  const { colors, styles } = useThemedStyles(makeStyles);
  const [range, setRange] = useState<RangeKey>('week');
  const cfg = RANGES.find((r) => r.key === range)!;
  const today = new Date();
  const from = cfg.start(today);
  const to = cfg.end(today);
  const fromStr = format(from, DATE_FMT);
  const toStr = format(to, DATE_FMT);

  const { data: points, isLoading, error } = useStats(fromStr, toStr);

  const chartData = useMemo(() => {
    const start = parseISO(fromStr);
    const end = parseISO(toStr);
    const byDate = new Map((points ?? []).map((p) => [p.date, p]));
    return eachDayOfInterval({ start, end }).map((d) => {
      const k = format(d, DATE_FMT);
      const p = byDate.get(k);
      return {
        date: k,
        label: format(d, range === 'year' || range === 'quarter' ? 'MMM d' : 'EEE d'),
        points: p ? p.points : 0,
        completed: p ? p.completedTasks : 0,
        total: p ? p.totalTasks : 0,
      };
    });
  }, [points, range, fromStr, toStr]);

  const summary = useMemo(() => {
    const totalPoints = chartData.reduce((s, x) => s + x.points, 0);
    const completed = chartData.reduce((s, x) => s + x.completed, 0);
    const totalTasks = chartData.reduce((s, x) => s + x.total, 0);
    const activeDays = chartData.filter((x) => x.points > 0 || x.total > 0).length;
    const avg = activeDays === 0 ? 0 : (totalPoints / activeDays).toFixed(1);
    return { totalPoints, completed, totalTasks, avg };
  }, [chartData]);

  // gifted-charts wants an explicit maxValue or it derives from data - but a flat all-zero
  // series renders a degenerate 0-height chart, so floor it the same way the axis would.
  const maxValue = Math.max(1, ...chartData.map((d) => Math.max(d.points, d.completed)));

  return (
    <SafeAreaView style={styles.flex} edges={['bottom']}>
      <View style={styles.rangeRow}>
        {RANGES.map((r) => (
          <Pressable
            key={r.key}
            onPress={() => setRange(r.key)}
            style={[styles.rangeBtn, range === r.key && styles.rangeBtnActive]}
          >
            <Text style={[styles.rangeBtnText, range === r.key && styles.rangeBtnTextActive]}>{r.label}</Text>
          </Pressable>
        ))}
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.card}>
          <View style={styles.header}>
            <Text style={styles.headerText}>
              {format(from, 'MMM d, yyyy')} - {format(to, 'MMM d, yyyy')}
            </Text>
            {isLoading && <ActivityIndicator size="small" color={colors.primary} />}
          </View>

          {error && <ErrorBanner message={error instanceof Error ? error.message : 'Failed to load'} />}

          <View style={styles.summaryRow}>
            <View style={styles.tile}>
              <Text style={styles.tileNum}>{summary.totalPoints}</Text>
              <Text style={styles.tileLabel}>Total points</Text>
            </View>
            <View style={styles.tile}>
              <Text style={styles.tileNum}>{summary.completed}/{summary.totalTasks}</Text>
              <Text style={styles.tileLabel}>Tasks completed</Text>
            </View>
            <View style={styles.tile}>
              <Text style={styles.tileNum}>{summary.avg}</Text>
              <Text style={styles.tileLabel}>Avg pts / active day</Text>
            </View>
          </View>

          <View style={styles.chartWrap}>
            <LineChart
              data={chartData.map((d) => ({ value: d.points, label: d.label }))}
              data2={chartData.map((d) => ({ value: d.completed }))}
              height={220}
              maxValue={maxValue}
              noOfSections={4}
              spacing={Math.max(28, 300 / Math.max(1, chartData.length))}
              color={colors.primary}
              color2={colors.primaryLight}
              thickness={2.5}
              thickness2={2}
              strokeDashArray2={[4, 4]}
              dataPointsColor={colors.primary}
              dataPointsColor2="transparent"
              dataPointsRadius={3}
              yAxisTextStyle={{ color: colors.textMuted, fontSize: 10 }}
              xAxisLabelTextStyle={{ color: colors.textMuted, fontSize: 9 }}
              rulesColor={colors.border}
              yAxisColor={colors.border}
              xAxisColor={colors.border}
              curved
              isAnimated
            />
          </View>
          <View style={styles.legendRow}>
            <LegendDot color={colors.primary} label="Points" />
            <LegendDot color={colors.primaryLight} label="Completed tasks" />
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function LegendDot({ color, label }: { color: string; label: string }) {
  const { styles } = useThemedStyles(makeStyles);
  return (
    <View style={styles.legendItem}>
      <View style={[styles.legendSwatch, { backgroundColor: color }]} />
      <Text style={styles.legendText}>{label}</Text>
    </View>
  );
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.md, paddingBottom: spacing.xxl },
  rangeRow: { flexDirection: 'row', gap: spacing.sm, padding: spacing.md, paddingBottom: 0 },
  rangeBtn: {
    flex: 1,
    paddingVertical: spacing.sm,
    borderRadius: radii.pill,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
  },
  rangeBtnActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  rangeBtnText: { fontSize: 13, fontWeight: '700', color: colors.textMuted },
  rangeBtnTextActive: { color: '#fff' },
  card: {
    backgroundColor: colors.card,
    borderRadius: radii.lg,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
  },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.md },
  headerText: { fontSize: 14, fontWeight: '700', color: colors.text },
  summaryRow: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.lg },
  tile: {
    flex: 1,
    backgroundColor: colors.progressTrack,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  tileNum: { fontSize: 18, fontWeight: '800', color: colors.primaryDark },
  tileLabel: { fontSize: 10, color: colors.textMuted, marginTop: 2, textAlign: 'center' },
  chartWrap: { marginLeft: -spacing.md },
  legendRow: { flexDirection: 'row', gap: spacing.lg, justifyContent: 'center', marginTop: spacing.sm },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  legendSwatch: { width: 10, height: 10, borderRadius: 5 },
  legendText: { fontSize: 12, color: colors.textMuted },
});
