import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Link } from 'expo-router';
import * as nativeAuth from '@/src/auth/nativeAuth';
import { FormField } from '@/src/components/FormField';
import { PrimaryButton } from '@/src/components/PrimaryButton';
import { ErrorBanner } from '@/src/components/Banner';
import { spacing, useThemedStyles, type ThemeColors } from '@/src/theme';

export default function RegisterScreen() {
  const { styles } = useThemedStyles(makeStyles);
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit() {
    setError(null);
    if (password !== confirm) {
      setError('Passwords do not match');
      return;
    }
    setBusy(true);
    try {
      await nativeAuth.register({ username: username.trim(), email: email.trim(), displayName, password });
      // Auto sign-in after registering, then AuthContext flips the app into the (app) group.
      await nativeAuth.login(username.trim(), password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed');
      setBusy(false);
    }
  }

  const valid = username.trim() && email.trim() && password.length >= 8 && confirm.length >= 8;

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Text style={styles.brand}>🌸 Lilac Planner</Text>
        <View style={styles.card}>
          <Text style={styles.heading}>Create your account</Text>
          {error && <ErrorBanner message={error} />}
          <FormField label="Username" value={username} onChangeText={setUsername} autoFocus />
          <FormField
            label="Email"
            value={email}
            onChangeText={setEmail}
            keyboardType="email-address"
            textContentType="emailAddress"
          />
          <FormField
            label="Display name (optional)"
            value={displayName}
            onChangeText={setDisplayName}
            autoCapitalize="words"
          />
          <FormField label="Password" value={password} onChangeText={setPassword} secureTextEntry />
          <FormField label="Confirm password" value={confirm} onChangeText={setConfirm} secureTextEntry />
          <PrimaryButton title="Create account" onPress={onSubmit} busy={busy} disabled={!valid} />
          <View style={styles.links}>
            <Link href="/login" style={styles.link}>
              Already have an account? Sign in
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
