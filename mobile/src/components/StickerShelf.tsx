import { StyleSheet, Text, View } from 'react-native';
import { useStickerCatalog } from '../hooks/useStickers';
import { nextThreshold, thresholdFor } from '../stickers';
import { radii, spacing, useThemedStyles, type ThemeColors } from '../theme';

interface Props {
  earned: string[];
  totalPoints: number;
}

export function StickerShelf({ earned, totalPoints }: Props) {
  const { styles } = useThemedStyles(makeStyles);
  const { data: catalog } = useStickerCatalog();
  const catalogMap = new Map((catalog ?? []).map((s) => [s.code, s]));
  const next = nextThreshold(totalPoints);
  const remaining = next - totalPoints;

  return (
    <View style={styles.section}>
      <Text style={styles.heading}>✨ Sticker Collection</Text>
      {earned.length === 0 ? (
        <Text style={styles.hint}>Earn your first sticker at 20 points - {remaining} to go!</Text>
      ) : (
        <View style={styles.grid}>
          {earned.map((code, i) => {
            const s = catalogMap.get(code);
            return (
              <View key={`${code}-${i}`} style={styles.sticker}>
                <Text style={styles.stickerEmoji}>{s ? s.emoji : '🎉'}</Text>
                <Text style={styles.stickerThreshold}>{thresholdFor(i)}</Text>
              </View>
            );
          })}
          <View style={[styles.sticker, styles.placeholder]}>
            <Text style={styles.placeholderText}>+{remaining}</Text>
          </View>
        </View>
      )}
    </View>
  );
}

const makeStyles = (colors: ThemeColors) => StyleSheet.create({
  section: { marginTop: spacing.lg },
  heading: { fontSize: 15, fontWeight: '700', color: colors.text, marginBottom: spacing.sm },
  hint: { fontSize: 13, color: colors.textMuted },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  sticker: {
    width: 56,
    height: 56,
    borderRadius: radii.md,
    backgroundColor: colors.progressTrack,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stickerEmoji: { fontSize: 22 },
  stickerThreshold: { fontSize: 9, color: colors.textMuted, marginTop: 2 },
  placeholder: { backgroundColor: 'transparent', borderWidth: 1, borderColor: colors.border, borderStyle: 'dashed' },
  placeholderText: { fontSize: 12, color: colors.textMuted, fontWeight: '700' },
});
