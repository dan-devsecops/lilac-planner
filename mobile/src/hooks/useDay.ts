import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../api/client';
import type { DayDto, TaskRequest } from '../api/types';

export function dayQueryKey(date: string) {
  return ['day', date] as const;
}

export function useDay(date: string) {
  return useQuery({
    queryKey: dayQueryKey(date),
    queryFn: () => api.getDay(date),
  });
}

/**
 * Mutations for a single day. Each resolves to the full updated DayDto (never a diff)
 * and writes it straight into the query cache - no separate refetch needed
 * on the happy path. A 404 means another client (web, another device) already changed the day
 * out from under us; refetching is the only sane recovery, mirroring DayView.jsx's `refresh()`.
 */
export function useDayMutations(date: string) {
  const qc = useQueryClient();
  const setDay = (day: DayDto) => qc.setQueryData(dayQueryKey(date), day);
  const invalidate = () => qc.invalidateQueries({ queryKey: dayQueryKey(date) });
  const onStaleNotFound = (err: unknown) => {
    if (err instanceof ApiError && err.status === 404) invalidate();
  };

  const addTask = useMutation({
    mutationFn: (payload: TaskRequest) => api.addTask(date, payload),
    onSuccess: setDay,
  });

  const updateTask = useMutation({
    mutationFn: ({ taskId, payload }: { taskId: string; payload: TaskRequest }) =>
      api.updateTask(date, taskId, payload),
    onSuccess: setDay,
    onError: onStaleNotFound,
  });

  const deleteTask = useMutation({
    mutationFn: (taskId: string) => api.deleteTask(date, taskId),
    onSuccess: setDay,
    onError: onStaleNotFound,
  });

  const reorderTasks = useMutation({
    mutationFn: (orderedIds: string[]) => api.reorderTasks(date, orderedIds),
    onSuccess: setDay,
    onError: () => invalidate(),
  });

  return { addTask, updateTask, deleteTask, reorderTasks, setDay, invalidate };
}
