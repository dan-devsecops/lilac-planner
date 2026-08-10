import { Link, Stack } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';
import { spacing, useThemedStyles, type ThemeColors } from '@/src/theme';

export default function NotFoundScreen() {
  const { styles } = useThemedStyles(makeStyles);
  return (
    <>
      <Stack.Screen options={{ title: 'Oops!' }} />
      <View style={styles.container}>
        <Text style={styles.title}>This screen doesn't exist.</Text>
        <Link href="/" style={styles.link}>
          <Text style={styles.linkText}>Go to home screen</Text>
        </Link>
      </View>
    </>
  );
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
    backgroundColor: colors.background,
  },
  title: { fontSize: 18, fontWeight: '700', color: colors.text },
  link: { marginTop: spacing.lg, paddingVertical: spacing.md },
  linkText: { fontSize: 15, color: colors.primary, fontWeight: '600' },
});
