import { useEffect, useState } from 'react';
import type { ColorValue } from 'react-native';
import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '@/src/theme';
import { todayStr } from '@/src/utils/dates';
import { ensurePushRegistration } from '@/src/notifications/push';

function TodayTabIcon({ color, size }: { color: ColorValue; size: number }) {
  return <Ionicons name="today-outline" size={size} color={color} />;
}

function StatisticsTabIcon({ color, size }: { color: ColorValue; size: number }) {
  return <Ionicons name="stats-chart-outline" size={size} color={color} />;
}

function AccountTabIcon({ color, size }: { color: ColorValue; size: number }) {
  return <Ionicons name="person-circle-outline" size={size} color={color} />;
}

/** Recomputes at the next local midnight so the "Today" tab never points at a stale date
 *  without requiring the user to reopen the app - mirrors frontend/src/App.jsx. */
function useTodayDateString() {
  const [today, setToday] = useState(todayStr);
  useEffect(() => {
    const now = new Date();
    const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
    const id = setTimeout(() => setToday(todayStr()), midnight.getTime() - now.getTime());
    return () => clearTimeout(id);
  }, [today]);
  return today;
}

export default function AppLayout() {
  const today = useTodayDateString();
  const { colors } = useTheme();

  useEffect(() => {
    ensurePushRegistration();
  }, []);

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarStyle: { backgroundColor: colors.card, borderTopColor: colors.border },
        headerStyle: { backgroundColor: colors.card },
        headerTintColor: colors.text,
      }}
    >
      {/* Not a tab - just the redirect target for bare "/" inside this group. */}
      <Tabs.Screen name="index" options={{ href: null }} />
      <Tabs.Screen
        name="day/[date]"
        options={{
          title: 'Today',
          href: `/day/${today}`,
          tabBarIcon: TodayTabIcon,
        }}
      />
      <Tabs.Screen
        name="statistics"
        options={{
          title: 'Statistics',
          tabBarIcon: StatisticsTabIcon,
        }}
      />
      <Tabs.Screen
        name="account"
        options={{
          title: 'Account',
          tabBarIcon: AccountTabIcon,
        }}
      />
    </Tabs>
  );
}
