import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Link } from 'expo-router';
import * as nativeAuth from '@/src/auth/nativeAuth';
import { FormField } from '@/src/components/FormField';
import { PrimaryButton } from '@/src/components/PrimaryButton';
import { ErrorBanner } from '@/src/components/Banner';
import { spacing, useThemedStyles, type ThemeColors } from '@/src/theme';

export default function LoginScreen() {
  const { styles } = useThemedStyles(makeStyles);
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit() {
    setError(null);
    setBusy(true);
    try {
      await nativeAuth.login(login.trim(), password);
      // No manual navigation needed: AuthContext observes the token change and the root
      // layout's Stack.Protected guard swaps to the (app) group.
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign in failed');
      setBusy(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Text style={styles.brand}>🌸 Lilac Planner</Text>
        <View style={styles.card}>
          <Text style={styles.heading}>Sign in</Text>
          {error && <ErrorBanner message={error} />}
          <FormField
            label="Username or email"
            value={login}
            onChangeText={setLogin}
            autoFocus
            returnKeyType="next"
          />
          <FormField
            label="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            returnKeyType="go"
            onSubmitEditing={onSubmit}
          />
          <PrimaryButton
            title="Sign in"
            onPress={onSubmit}
            busy={busy}
            disabled={!login.trim() || !password}
          />
          <View style={styles.links}>
            <Link href="/forgot-password" style={styles.link}>
              Forgot password?
            </Link>
            <Link href="/register" style={styles.link}>
              Create an account
            </Link>
          </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  flex: { flex: 1 },
  container: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: spacing.xl,
    backgroundColor: colors.background,
  },
  brand: { fontSize: 28, fontWeight: '800', textAlign: 'center', marginBottom: spacing.xl, color: colors.text },
  card: {
    backgroundColor: colors.card,
    borderRadius: 16,
    padding: spacing.xl,
    borderWidth: 1,
    borderColor: colors.border,
  },
  heading: { fontSize: 20, fontWeight: '700', marginBottom: spacing.lg, color: colors.text },
  links: { marginTop: spacing.md, gap: spacing.sm },
  link: { color: colors.primary, fontWeight: '600', textAlign: 'center', paddingVertical: spacing.xs },
});
