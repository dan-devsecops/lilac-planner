import { Redirect } from 'expo-router';
import { todayStr } from '@/src/utils/dates';

export default function AppIndex() {
  return <Redirect href={`/day/${todayStr()}`} />;
}
