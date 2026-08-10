import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';

export function useStats(from: string, to: string) {
  return useQuery({
    queryKey: ['stats', from, to],
    queryFn: () => api.getStats(from, to),
  });
}
