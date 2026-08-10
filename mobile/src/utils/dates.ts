import { format } from 'date-fns';

export const DATE_FMT = 'yyyy-MM-dd';

export function todayStr(): string {
  return format(new Date(), DATE_FMT);
}
