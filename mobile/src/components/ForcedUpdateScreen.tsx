import { Platform, StyleSheet, Text, View } from 'react-native';
import { spacing, useThemedStyles, type ThemeColors } from '../theme';

/**
 * Kill switch: shown when GET /api/v1/meta reports the installed build is
 * below minSupportedAppVersion. Mobile apps can't be force-updated any other way.
 *
 * No "open store" button here on purpose - store distribution hasn't happened yet, so
 * there is no real store listing to deep-link to. Add one once app.json's bundleIdentifier /
 * package and the store listings actually exist.
 */
export function ForcedUpdateScreen() {
  const { styles } = useThemedStyles(makeStyles);
  return (
    <View style={styles.container}>
      <Text style={styles.emoji}>🌸</Text>
      <Text style={styles.title}>Update required</Text>
      <Text style={styles.body}>
        This version of Lilac Planner is no longer supported. Please update the app from the{' '}
        {Platform.OS === 'ios' ? 'App Store' : 'Play Store'} to continue.
      </Text>
    </View>
  );
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl, backgroundColor: colors.background },
  emoji: { fontSize: 48, marginBottom: spacing.md },
  title: { fontSize: 22, fontWeight: '800', color: colors.text, marginBottom: spacing.sm },
  body: { fontSize: 14, color: colors.textMuted, textAlign: 'center', marginBottom: spacing.xl },
});
