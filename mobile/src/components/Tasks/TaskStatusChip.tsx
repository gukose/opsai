import { StyleSheet, Text, View } from "react-native";

import { colors, radius, spacing, typography } from "../../theme/tokens";

type TaskStatusChipProps = {
  status: string;
};

export function TaskStatusChip({ status }: TaskStatusChipProps) {
  const tone = getTone(status);

  return (
    <View style={[styles.chip, tone.container]}>
      <View style={[styles.dot, tone.dot]} />
      <Text style={[styles.label, tone.label]}>{status.split("_").join(" ")}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    flexDirection: "row",
    alignItems: "center",
    alignSelf: "flex-start",
    gap: spacing.xs,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: radius.pill
  },
  dot: {
    width: 5,
    height: 5,
    borderRadius: radius.pill
  },
  label: {
    fontSize: typography.tiny,
    fontWeight: "900",
    textTransform: "uppercase"
  }
});

function getTone(status: string) {
  switch (status.toUpperCase()) {
    case "COMPLETED":
      return {
        container: { backgroundColor: colors.greenSoft },
        dot: { backgroundColor: colors.green },
        label: { color: colors.green }
      };
    case "CANCELLED":
      return {
        container: { backgroundColor: colors.redSoft },
        dot: { backgroundColor: colors.red },
        label: { color: colors.red }
      };
    case "OVERDUE":
      return {
        container: { backgroundColor: "#fff4db" },
        dot: { backgroundColor: colors.amber },
        label: { color: "#b45309" }
      };
    case "IN_PROGRESS":
    case "STARTED":
    case "ASSIGNED":
      return {
        container: { backgroundColor: "#e9f1ff" },
        dot: { backgroundColor: colors.blue },
        label: { color: colors.blue }
      };
    case "WAITING":
      return {
        container: { backgroundColor: "#f3f4f6" },
        dot: { backgroundColor: colors.nav },
        label: { color: colors.nav }
      };
    default:
      return {
        container: { backgroundColor: "#f3f4f6" },
        dot: { backgroundColor: colors.textSubtle },
        label: { color: colors.textMuted }
      };
  }
}
