import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Link, useLocalSearchParams } from 'expo-router';
import * as nativeAuth from '@/src/auth/nativeAuth';
import { FormField } from '@/src/components/FormField';
import { PrimaryButton } from '@/src/components/PrimaryButton';
import { ErrorBanner, SuccessBanner } from '@/src/components/Banner';
import { spacing, useThemedStyles, type ThemeColors } from '@/src/theme';

export default function ResetPasswordScreen() {
  const { styles } = useThemedStyles(makeStyles);
  const params = useLocalSearchParams<{ token?: string }>();
  // Deep-linking the emailed reset link into the app is Phase 2 (needs PLANNER_RESET_URL
  // pointed at an app scheme/universal link). Until then, a manually pasted token still works.
  const [token, setToken] = useState(params.token ?? '');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  async function onSubmit() {
    setError(null);
    if (password !== confirm) {
      setError('Passwords do not match');
      return;
    }
    setBusy(true);
    try {
      await nativeAuth.resetPassword(token.trim(), password);
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Reset failed');
    }
    setBusy(false);
  }

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Text style={styles.brand}>🌸 Lilac Planner</Text>
        <View style={styles.card}>
          <Text style={styles.heading}>Choose a new password</Text>
          {error && <ErrorBanner message={error} />}
          {done ? (
            <SuccessBanner message="Your password has been reset. You can sign in now." />
          ) : (
            <>
              {!params.token && (
                <FormField label="Reset token (from your email link)" value={token} onChangeText={setToken} />
              )}
              <FormField label="New password" value={password} onChangeText={setPassword} secureTextEntry />
              <FormField label="Confirm new password" value={confirm} onChangeText={setConfirm} secureTextEntry />
              <PrimaryButton
                title="Reset password"
                onPress={onSubmit}
                busy={busy}
                disabled={!token.trim() || password.length < 8 || confirm.length < 8}
              />
            </>
          )}
          <View style={styles.links}>
            <Link href="/login" style={styles.link}>
              Back to sign in
            </Link>
          </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  flex: { flex: 1 },
  container: { flexGrow: 1, justifyContent: 'center', padding: spacing.xl, backgroundColor: colors.background },
  brand: { fontSize: 28, fontWeight: '800', textAlign: 'center', marginBottom: spacing.xl, color: colors.text },
  card: { backgroundColor: colors.card, borderRadius: 16, padding: spacing.xl, borderWidth: 1, borderColor: colors.border },
  heading: { fontSize: 20, fontWeight: '700', marginBottom: spacing.lg, color: colors.text },
  links: { marginTop: spacing.md },
  link: { color: colors.primary, fontWeight: '600', textAlign: 'center', paddingVertical: spacing.xs },
});
