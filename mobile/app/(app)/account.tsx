import { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as nativeAuth from '@/src/auth/nativeAuth';
import { useAuth } from '@/src/auth/AuthContext';
import { FormField } from '@/src/components/FormField';
import { PrimaryButton } from '@/src/components/PrimaryButton';
import { ErrorBanner, SuccessBanner } from '@/src/components/Banner';
import { spacing, toggleTheme, useIsSystemTheme, useThemeMode, useThemedStyles, type ThemeColors } from '@/src/theme';
import { Ionicons } from '@expo/vector-icons';
import { api } from '@/src/api/client';
import type { PushSubscriptionDto } from '@/src/api/types';
import { detectTimezone } from '@/src/notifications/push';

function ChangePasswordCard() {
  const { styles } = useThemedStyles(makeStyles);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit() {
    setMsg(null);
    setError(null);
    if (next !== confirm) {
      setError('Passwords do not match');
      return;
    }
    setBusy(true);
    try {
      await nativeAuth.changePassword(current, next);
      setMsg('Password changed.');
      setCurrent('');
      setNext('');
      setConfirm('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to change password');
    }
    setBusy(false);
  }

  return (
    <View style={styles.card}>
      <Text style={styles.heading}>Change password</Text>
      {msg && <SuccessBanner message={msg} />}
      {error && <ErrorBanner message={error} />}
      <FormField label="Current password" value={current} onChangeText={setCurrent} secureTextEntry />
      <FormField label="New password" value={next} onChangeText={setNext} secureTextEntry />
      <FormField label="Confirm new password" value={confirm} onChangeText={setConfirm} secureTextEntry />
      <PrimaryButton
        title="Change password"
        onPress={onSubmit}
        busy={busy}
        disabled={!current || next.length < 8 || confirm.length < 8}
      />
    </View>
  );
}

function AdminResetCard() {
  const { styles } = useThemedStyles(makeStyles);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit() {
    setMsg(null);
    setError(null);
    setBusy(true);
    try {
      await nativeAuth.adminResetPassword(username.trim(), password);
      setMsg(`Password reset for ${username.trim()}.`);
      setUsername('');
      setPassword('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to reset password');
    }
    setBusy(false);
  }

  return (
    <View style={styles.card}>
      <Text style={styles.heading}>Admin: reset a user's password</Text>
      {msg && <SuccessBanner message={msg} />}
      {error && <ErrorBanner message={error} />}
      <FormField label="Username" value={username} onChangeText={setUsername} />
      <FormField label="New password" value={password} onChangeText={setPassword} secureTextEntry />
      <PrimaryButton title="Reset password" onPress={onSubmit} busy={busy} disabled={!username.trim() || password.length < 8} />
    </View>
  );
}

function TimezoneCard() {
  const { styles } = useThemedStyles(makeStyles);
  const [timezone, setTimezone] = useState('');
  const [current, setCurrent] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.me()
      .then((user) => {
        if (cancelled) return;
        setCurrent(user.timezone);
        setTimezone(user.timezone ?? '');
      })
      .catch((err) => { if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load timezone'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  async function onSubmit() {
    setMsg(null);
    setError(null);
    const value = timezone.trim();
    setBusy(true);
    try {
      await api.updateTimezone(value);
      setCurrent(value);
      setMsg('Timezone updated.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update timezone');
    }
    setBusy(false);
  }

  function useDeviceTimezone() {
    const detected = detectTimezone();
    if (detected) setTimezone(detected);
  }

  return (
    <View style={styles.card}>
      <Text style={styles.heading}>Timezone</Text>
      <Text style={styles.hint}>
        Used to work out "today" for task alarm push notifications.
        {!loading && ` Currently: ${current ?? 'not set (using server default)'}.`}
      </Text>
      {msg && <SuccessBanner message={msg} />}
      {error && <ErrorBanner message={error} />}
      {!loading && (
        <>
          <FormField label="IANA timezone (e.g. Europe/Prague)" value={timezone} onChangeText={setTimezone} />
          <View style={styles.timezoneActions}>
            <View style={styles.flex1}>
              <PrimaryButton title="Use device timezone" onPress={useDeviceTimezone} variant="ghost" />
            </View>
            <View style={styles.flex1}>
              <PrimaryButton title="Save" onPress={onSubmit} busy={busy} disabled={!timezone.trim()} />
            </View>
          </View>
        </>
      )}
    </View>
  );
}

function DevicesCard() {
  const { colors, styles } = useThemedStyles(makeStyles);
  const [devices, setDevices] = useState<PushSubscriptionDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.listPushSubscriptions()
      .then((list) => { if (!cancelled) setDevices(list); })
      .catch((err) => { if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load devices'); });
    return () => { cancelled = true; };
  }, []);

  async function onRemove(id: string) {
    setBusyId(id);
    setError(null);
    try {
      await api.deletePushSubscription(id);
      setDevices((prev) => (prev ? prev.filter((d) => d.id !== id) : prev));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to remove device');
    }
    setBusyId(null);
  }

  return (
    <View style={styles.card}>
      <Text style={styles.heading}>Registered devices</Text>
      {error && <ErrorBanner message={error} />}
      {devices === null && !error && <Text style={styles.hint}>Loading…</Text>}
      {devices !== null && devices.length === 0 && (
        <Text style={styles.hint}>No devices registered for push notifications yet.</Text>
      )}
      {devices?.map((d) => (
        <View key={d.id} style={styles.deviceRow}>
          <View style={styles.deviceRowLeft}>
            <Ionicons name={d.platform === 'WEB' ? 'globe-outline' : 'phone-portrait-outline'} size={18} color={colors.primary} />
            <View>
              <Text style={styles.deviceRowLabel}>{d.platform === 'WEB' ? 'Browser' : 'Mobile'}</Text>
              <Text style={styles.deviceRowMeta}>registered {new Date(d.createdAt).toLocaleDateString()}</Text>
            </View>
          </View>
          <Pressable onPress={() => onRemove(d.id)} disabled={busyId === d.id} hitSlop={8}>
            <Text style={styles.deviceRemove}>{busyId === d.id ? '…' : 'Remove'}</Text>
          </Pressable>
        </View>
      ))}
    </View>
  );
}

function ThemeCard() {
  const { colors, styles } = useThemedStyles(makeStyles);
  const mode = useThemeMode();
  const isSystem = useIsSystemTheme();
  const dark = mode === 'dark';
  return (
    <View style={styles.card}>
      <Text style={styles.heading}>Appearance</Text>
      <Pressable onPress={toggleTheme} style={styles.themeRow}>
        <View style={styles.themeRowLeft}>
          <Ionicons name={dark ? 'moon' : 'sunny'} size={20} color={colors.primary} />
          <Text style={styles.themeRowLabel}>{dark ? 'Dark mode' : 'Light mode'}</Text>
        </View>
        <Text style={styles.themeRowHint}>{isSystem ? 'Following system - tap to override' : 'Tap to switch'}</Text>
      </Pressable>
    </View>
  );
}

export default function AccountScreen() {
  const { styles } = useThemedStyles(makeStyles);
  const { isAdmin, displayName } = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogout() {
    setLoggingOut(true);
    await nativeAuth.logout();
    // AuthContext observes the token clear and the root layout swaps to (auth) automatically.
  }

  return (
    <SafeAreaView style={styles.flex} edges={['bottom']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <Text style={styles.greeting}>👋 {displayName}</Text>
          <ThemeCard />
          <TimezoneCard />
          <DevicesCard />
          <ChangePasswordCard />
          {isAdmin && <AdminResetCard />}
          <PrimaryButton title="Sign out" onPress={handleLogout} busy={loggingOut} variant="danger" />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.lg, gap: spacing.lg },
  greeting: { fontSize: 18, fontWeight: '700', color: colors.text, marginBottom: spacing.sm },
  card: {
    backgroundColor: colors.card,
    borderRadius: 16,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
    marginBottom: spacing.lg,
  },
  heading: { fontSize: 16, fontWeight: '700', color: colors.text, marginBottom: spacing.md },
  themeRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  themeRowLeft: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  themeRowLabel: { fontSize: 15, fontWeight: '600', color: colors.text },
  themeRowHint: { fontSize: 11, color: colors.textMuted, flexShrink: 1, textAlign: 'right', marginLeft: spacing.sm },
  hint: { fontSize: 13, color: colors.textMuted, marginBottom: spacing.md },
  timezoneActions: { flexDirection: 'row', gap: spacing.sm },
  flex1: { flex: 1 },
  deviceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  deviceRowLeft: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  deviceRowLabel: { fontSize: 14, fontWeight: '600', color: colors.text },
  deviceRowMeta: { fontSize: 11, color: colors.textMuted },
  deviceRemove: { fontSize: 13, fontWeight: '700', color: colors.error },
});
