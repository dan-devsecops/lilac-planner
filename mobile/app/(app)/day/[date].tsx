import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { addDays, format, parseISO } from 'date-fns';
import DraggableFlatList, { type RenderItemParams } from 'react-native-draggable-flatlist';
import { useDay, useDayMutations, dayQueryKey } from '@/src/hooks/useDay';
import { api, ApiError } from '@/src/api/client';
import { nextThreshold } from '@/src/stickers';
import { DateNav } from '@/src/components/DateNav';
import { TaskItem } from '@/src/components/TaskItem';
import { AddTaskForm } from '@/src/components/AddTaskForm';
import { StickerShelf } from '@/src/components/StickerShelf';
import { ErrorBanner } from '@/src/components/Banner';
import { DATE_FMT } from '@/src/utils/dates';
import { radii, spacing, useThemedStyles, type ThemeColors } from '@/src/theme';
import { reconcileDayNotifications } from '@/src/notifications/alarms';
import type { TaskDto } from '@/src/api/types';

/** "Task (moved)" -> "Task (moved x2)" -> "Task (moved x3)" -- mirrors DayView.jsx's movedTitle,
 *  used only for the copy-to-next-day action. */
function movedTitle(title: string): string {
  const m = title.match(/^(.*) \(moved(?: x(\d+))?\)$/);
  if (m) {
    const count = m[2] ? parseInt(m[2], 10) : 1;
    return `${m[1]} (moved x${count + 1})`;
  }
  return `${title} (moved)`;
}

export default function DayScreen() {
  const { date } = useLocalSearchParams<{ date: string }>();
  const router = useRouter();
  const qc = useQueryClient();
  const { colors, styles } = useThemedStyles(makeStyles);
  const { data: day, isLoading, error: queryError, dataUpdatedAt } = useDay(date);
  const { addTask, updateTask, deleteTask, reorderTasks, setDay, invalidate } = useDayMutations(date);

  const [pageError, setPageError] = useState<string | null>(null);
  const [flash, setFlash] = useState<string | null>(null);

  useEffect(() => {
    if (!flash) return;
    const id = setTimeout(() => setFlash(null), 2500);
    return () => clearTimeout(id);
  }, [flash]);

  // Reconciles OS-scheduled reminders every time this day's tasks change (initial load, add,
  // edit, toggle, delete, reorder) - see src/notifications/alarms.ts.
  useEffect(() => {
    if (day) reconcileDayNotifications(date, day.tasks);
  }, [date, day]);

  // dataUpdatedAt ticks on every successful (re)fetch - including the invalidate() below
  // resolving after a 409 - even if structural sharing keeps `day` referentially equal. Clear
  // any stale error banner so it doesn't outlive the problem it was reporting, mirroring
  // DayView.jsx's refresh()-then-clear behavior.
  useEffect(() => {
    if (dataUpdatedAt) setPageError(null);
  }, [dataUpdatedAt]);

  function reportError(err: unknown) {
    invalidate(); // mirrors DayView.jsx's refresh(): re-sync with the server after any failure
    if (err instanceof ApiError && err.status === 404) return; // already resolved by the refetch
    setPageError(err instanceof Error ? err.message : 'Something went wrong');
  }

  async function handleToggle(taskId: string, completed: boolean) {
    try {
      const updated = await updateTask.mutateAsync({ taskId, payload: { completed } });
      // Completed tasks float to the top, matching the web app exactly.
      const sorted = [...updated.tasks.filter((t) => t.completed), ...updated.tasks.filter((t) => !t.completed)];
      setDay({ ...updated, tasks: sorted });
      await reorderTasks.mutateAsync(sorted.map((t) => t.id));
    } catch (err) {
      reportError(err);
    }
  }

  async function handlePoints(taskId: string, value: number) {
    try {
      await updateTask.mutateAsync({ taskId, payload: { points: value } });
    } catch (err) {
      reportError(err);
    }
  }

  async function handleTitle(taskId: string, title: string) {
    try {
      await updateTask.mutateAsync({ taskId, payload: { title } });
    } catch (err) {
      reportError(err);
    }
  }

  async function handleDelete(taskId: string) {
    try {
      await deleteTask.mutateAsync(taskId);
    } catch (err) {
      reportError(err);
    }
  }

  async function handleAdd(payload: Parameters<typeof addTask.mutateAsync>[0]) {
    try {
      await addTask.mutateAsync(payload);
    } catch (err) {
      reportError(err);
    }
  }

  async function handleReorder(tasks: TaskDto[]) {
    if (!day) return;
    setDay({ ...day, tasks });
    try {
      await reorderTasks.mutateAsync(tasks.map((t) => t.id));
    } catch (err) {
      reportError(err);
    }
  }

  async function handleCopyToNextDay(task: TaskDto) {
    const nextDate = format(addDays(parseISO(date), 1), DATE_FMT);
    try {
      await api.addTask(nextDate, {
        title: movedTitle(task.title),
        points: task.points,
        scheduledTime: task.scheduledTime,
        recurrence: 'NONE',
      });
      qc.invalidateQueries({ queryKey: dayQueryKey(nextDate) });
      setFlash(`Copied to ${nextDate}`);
    } catch (err) {
      reportError(err);
    }
  }

  if (isLoading && !day) {
    return (
      <SafeAreaView style={styles.center} edges={['bottom']}>
        <ActivityIndicator color={colors.primary} />
      </SafeAreaView>
    );
  }
  if (queryError && !day) {
    return (
      <SafeAreaView style={styles.center} edges={['bottom']}>
        <ErrorBanner message={queryError instanceof Error ? queryError.message : 'Failed to load'} />
      </SafeAreaView>
    );
  }
  if (!day) return null;

  const total = day.totalPoints;
  const totalAvailable = day.totalAvailablePoints;
  const next = nextThreshold(total);
  const pct = totalAvailable === 0 ? 0 : Math.min(100, Math.max(0, (total / totalAvailable) * 100));

  return (
    <SafeAreaView style={styles.flex} edges={['bottom']}>
      <DateNav date={date} onNavigate={(d) => router.setParams({ date: d })} />

      <DraggableFlatList
        data={day.tasks}
        keyExtractor={(item) => item.id}
        onDragEnd={({ data }) => handleReorder(data)}
        contentContainerStyle={styles.listContent}
        ListHeaderComponent={
          pageError ? <ErrorBanner message={pageError} onDismiss={() => setPageError(null)} /> : null
        }
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyEmoji}>🪻</Text>
            <Text style={styles.emptyText}>No tasks yet - add your first one below 💜</Text>
          </View>
        }
        renderItem={({ item, drag, isActive }: RenderItemParams<TaskDto>) => (
          <TaskItem
            task={item}
            isActive={isActive}
            onDrag={drag}
            onToggle={handleToggle}
            onPoints={handlePoints}
            onTitle={handleTitle}
            onDelete={handleDelete}
            onCopyToNextDay={handleCopyToNextDay}
          />
        )}
        ListFooterComponent={
          <>
            <AddTaskForm onSubmit={handleAdd} />

            <View style={styles.totalsBar}>
              <Text style={styles.totalsText}>
                Earned <Text style={styles.totalsBold}>{total}</Text> / <Text style={styles.totalsBold}>{totalAvailable}</Text> pts
              </Text>
              <View style={styles.progressTrack}>
                <View style={[styles.progressFill, { width: `${pct}%` }]} />
              </View>
              <Text style={styles.nextStickerText}>Next sticker: {next} pts</Text>
            </View>

            <StickerShelf earned={day.earnedStickers} totalPoints={total} />
          </>
        }
      />

      {flash && (
        <View style={styles.flashToast}>
          <Text style={styles.flashText}>{flash} ✓</Text>
        </View>
      )}
    </SafeAreaView>
  );
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.background },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.background },
  listContent: { padding: spacing.md, paddingBottom: spacing.xxl },
  empty: { alignItems: 'center', paddingVertical: spacing.xxl },
  emptyEmoji: { fontSize: 40, marginBottom: spacing.sm },
  emptyText: { color: colors.textMuted, fontSize: 14, textAlign: 'center' },
  totalsBar: { marginTop: spacing.lg, alignItems: 'center' },
  totalsText: { fontSize: 14, color: colors.text, marginBottom: spacing.sm },
  totalsBold: { fontWeight: '800' },
  progressTrack: {
    width: '100%',
    height: 10,
    borderRadius: radii.pill,
    backgroundColor: colors.progressTrack,
    overflow: 'hidden',
  },
  progressFill: { height: '100%', backgroundColor: colors.primary },
  nextStickerText: { fontSize: 12, color: colors.textMuted, marginTop: spacing.xs },
  flashToast: {
    position: 'absolute',
    bottom: spacing.xl,
    alignSelf: 'center',
    backgroundColor: colors.text,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
    borderRadius: radii.pill,
  },
  flashText: { color: '#fff', fontSize: 13, fontWeight: '600' },
});
