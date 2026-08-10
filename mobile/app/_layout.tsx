import 'react-native-gesture-handler';
import { useEffect, useState } from 'react';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import Constants from 'expo-constants';
import { AuthProvider, useAuth } from '@/src/auth/AuthContext';
import { api } from '@/src/api/client';
import { isBelowMinVersion } from '@/src/utils/version';
import { ForcedUpdateScreen } from '@/src/components/ForcedUpdateScreen';
import { configureNotifications } from '@/src/notifications/alarms';
import { useTheme, useThemeHydrated } from '@/src/theme';
import { StatusBar } from 'expo-status-bar';

SplashScreen.preventAutoHideAsync();
configureNotifications();

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1 } },
});

export default function RootLayout() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <RootNavigator />
        </AuthProvider>
      </QueryClientProvider>
    </GestureHandlerRootView>
  );
}

/**
 * Gates the whole app on native-auth state, mirroring frontend/src/App.jsx's top-level branch
 * (isNative && !isAuthenticated -> auth screens only). Uses Stack.Protected (stable since Expo
 * Router v5 / SDK 53) rather than imperative redirects - flipping the guard automatically clears
 * the now-inaccessible group's navigation history.
 */
function RootNavigator() {
  const { isAuthenticated } = useAuth();
  const { mode } = useTheme();
  const themeHydrated = useThemeHydrated();
  const [blocked, setBlocked] = useState<boolean | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    // A slow or unroutable backend must not blank the app forever waiting on this fetch:
    // without a deadline, an address that hangs instead of refusing (e.g. an unreachable
    // host) leaves the whole app on a blank white screen indefinitely. 5s is generous for
    // a same-network/production call yet short enough that a bad network fails visibly fast.
    const timeout = setTimeout(() => controller.abort(), 5000);
    api
      .getMeta(controller.signal)
      .then((meta) => {
        if (cancelled) return;
        const appVersion = Constants.expoConfig?.version ?? '0.0.0';
        setBlocked(isBelowMinVersion(appVersion, meta.minSupportedAppVersion));
      })
      // Never let an unreachable backend (offline, first launch on a bad network) block the
      // whole app - only an explicit "you're below minSupportedAppVersion" answer does that.
      .catch(() => {
        if (!cancelled) setBlocked(false);
      })
      .finally(() => clearTimeout(timeout));
    return () => {
      cancelled = true;
      controller.abort();
      clearTimeout(timeout);
    };
  }, []);

  useEffect(() => {
    if (isAuthenticated !== undefined && blocked !== undefined && themeHydrated) SplashScreen.hideAsync();
  }, [isAuthenticated, blocked, themeHydrated]);

  if (isAuthenticated === undefined || blocked === undefined || !themeHydrated) return null;
  if (blocked) return <ForcedUpdateScreen />;

  return (
    <>
      <StatusBar style={mode === 'dark' ? 'light' : 'dark'} />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Protected guard={isAuthenticated}>
          <Stack.Screen name="(app)" />
        </Stack.Protected>
        <Stack.Protected guard={!isAuthenticated}>
          <Stack.Screen name="(auth)" />
        </Stack.Protected>
      </Stack>
    </>
  );
}
