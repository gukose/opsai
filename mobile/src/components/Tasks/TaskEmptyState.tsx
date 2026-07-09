import { StyleSheet, Text, View } from "react-native";
import { ClipboardList } from "lucide-react-native";

import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";

type TaskEmptyStateProps = {
  title: string;
  message: string;
};

export function TaskEmptyState({ title, message }: TaskEmptyStateProps) {
  return (
    <View style={styles.card}>
      <View style={styles.iconWell}>
        <ClipboardList color={colors.textMuted} size={18} strokeWidth={2.2} />
      </View>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.message}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    padding: spacing.md,
    alignItems: "center",
    ...shadow.card
  },
  iconWell: {
    width: 40,
    height: 40,
    borderRadius: radius.pill,
    backgroundColor: "#f5f7fa",
    alignItems: "center",
    justifyContent: "center"
  },
  title: {
    marginTop: 10,
    color: colors.text,
    fontSize: 13,
    fontWeight: "800"
  },
  message: {
    marginTop: 4,
    color: colors.textMuted,
    fontSize: typography.caption,
    lineHeight: 14,
    fontWeight: "600",
    textAlign: "center"
  }
});
