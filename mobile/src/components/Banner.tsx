import { Pressable, StyleSheet, Text, View } from 'react-native';
import { radii, spacing, useThemedStyles, type ThemeColors } from '../theme';

export function ErrorBanner({ message, onDismiss }: { message: string; onDismiss?: () => void }) {
  const { styles } = useThemedStyles(makeStyles);
  return (
    <View style={[styles.container, styles.error]}>
      <Text style={[styles.text, styles.errorText]}>⚠ {message}</Text>
      {onDismiss && (
        <Pressable onPress={onDismiss} hitSlop={8}>
          <Text style={[styles.dismiss, styles.errorText]}>dismiss</Text>
        </Pressable>
      )}
    </View>
  );
}

export function SuccessBanner({ message }: { message: string }) {
  const { styles } = useThemedStyles(makeStyles);
  return <Text style={[styles.base, styles.success]}>{message}</Text>;
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  base: {
    borderRadius: radii.sm,
    padding: spacing.md,
    marginBottom: spacing.md,
    fontSize: 14,
    fontWeight: '600',
  },
  container: {
    borderRadius: radii.sm,
    padding: spacing.md,
    marginBottom: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.sm,
  },
  error: { backgroundColor: colors.errorBg },
  errorText: { color: colors.error },
  text: { flex: 1, fontSize: 14, fontWeight: '600' },
  dismiss: { fontSize: 13, fontWeight: '700', textDecorationLine: 'underline' },
  success: { backgroundColor: colors.successBg, color: colors.success },
});
