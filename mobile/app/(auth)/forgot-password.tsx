import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Link } from 'expo-router';
import * as nativeAuth from '@/src/auth/nativeAuth';
import { FormField } from '@/src/components/FormField';
import { PrimaryButton } from '@/src/components/PrimaryButton';
import { SuccessBanner } from '@/src/components/Banner';
import { spacing, useThemedStyles, type ThemeColors } from '@/src/theme';

export default function ForgotPasswordScreen() {
  const { styles } = useThemedStyles(makeStyles);
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);

  async function onSubmit() {
    setBusy(true);
    try {
      await nativeAuth.forgotPassword(email.trim());
    } catch {
      // never reveal whether the email exists
    }
    setSent(true);
    setBusy(false);
  }

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Text style={styles.brand}>🌸 Lilac Planner</Text>
        <View style={styles.card}>
          <Text style={styles.heading}>Reset your password</Text>
          {sent ? (
            <SuccessBanner message="If an account exists for that email, a reset link is on its way." />
          ) : (
            <>
              <FormField
                label="Email"
                value={email}
                onChangeText={setEmail}
                keyboardType="email-address"
                textContentType="emailAddress"
                autoFocus
                onSubmitEditing={onSubmit}
              />
              <PrimaryButton title="Send reset link" onPress={onSubmit} busy={busy} disabled={!email.trim()} />
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
