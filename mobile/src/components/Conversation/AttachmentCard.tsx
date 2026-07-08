import { Image, StyleSheet, Text, View } from "react-native";
import { Image as ImageIcon } from "lucide-react-native";

import { colors, radius, spacing, typography } from "../../theme/tokens";

type AttachmentCardProps = {
  imageUri?: string;
  filename: string;
  size: string;
  timestamp?: string;
};

export function AttachmentCard({ imageUri, filename, size, timestamp }: AttachmentCardProps) {
  return (
    <View style={styles.card}>
      {imageUri ? (
        <Image source={{ uri: imageUri }} style={styles.image} />
      ) : (
        <View style={styles.imageFallback}>
          <ImageIcon color={colors.nav} size={16} strokeWidth={2} />
        </View>
      )}
      <View style={styles.meta}>
        <Text style={styles.title}>Photo attached</Text>
        <Text style={styles.filename}>{filename}</Text>
        <Text style={styles.size}>{size}</Text>
      </View>
      {timestamp ? <Text style={styles.timestamp}>{timestamp}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    minHeight: 74,
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    borderWidth: 1,
    borderColor: "#e7defa",
    borderRadius: radius.md,
    backgroundColor: "#f4efff",
    padding: spacing.sm
  },
  image: {
    width: 58,
    height: 50,
    borderRadius: radius.sm,
    backgroundColor: colors.cardBorder
  },
  imageFallback: {
    width: 58,
    height: 50,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.sm,
    backgroundColor: "#edf1f7",
    borderWidth: 1,
    borderColor: "#dbe2ee"
  },
  meta: {
    flex: 1
  },
  title: {
    color: colors.text,
    fontSize: typography.body,
    fontWeight: "900"
  },
  filename: {
    marginTop: spacing.xs,
    color: colors.nav,
    fontSize: typography.body,
    fontWeight: "700"
  },
  size: {
    color: colors.nav,
    fontSize: typography.body,
    fontWeight: "700"
  },
  timestamp: {
    alignSelf: "flex-end",
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "700"
  }
});
