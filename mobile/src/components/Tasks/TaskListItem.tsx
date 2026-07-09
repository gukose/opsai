import { Pressable, StyleSheet, Text, View } from "react-native";
import { Clock3, MapPin, ChevronRight } from "lucide-react-native";

import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";
import { TaskSummary } from "../../tasks/types";
import { formatDateTime, formatSlaCountdown } from "../../tasks/formatters";
import { TaskStatusChip } from "./TaskStatusChip";

type TaskListItemProps = {
  task: TaskSummary;
  active?: boolean;
  onPress?: () => void;
};

export function TaskListItem({ task, active, onPress }: TaskListItemProps) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        active && styles.activeCard,
        pressed && styles.pressed
      ]}
    >
      <View style={styles.row}>
        <View style={styles.main}>
          <Text style={styles.title} numberOfLines={1}>
            {task.title}
          </Text>
          <Text style={styles.description} numberOfLines={2}>
            {task.description}
          </Text>
          <View style={styles.metaRow}>
            <TaskStatusChip status={task.status} />
            <View style={styles.metaPill}>
              <Clock3 color={colors.textMuted} size={11} strokeWidth={2.2} />
              <Text style={styles.metaText}>{formatSlaCountdown(task.slaDeadline)}</Text>
            </View>
          </View>
          <View style={styles.subRow}>
            <Text style={styles.priority}>{task.priority}</Text>
            {task.roomOrLocation ? (
              <View style={styles.locationRow}>
                <MapPin color={colors.textMuted} size={11} strokeWidth={2.2} />
                <Text style={styles.location} numberOfLines={1}>
                  {task.roomOrLocation}
                </Text>
              </View>
            ) : null}
          </View>
          <Text style={styles.timestamp}>Updated {formatDateTime(task.updatedAt)}</Text>
        </View>
        <ChevronRight color={colors.textSubtle} size={16} strokeWidth={2.1} />
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    padding: spacing.md,
    ...shadow.card
  },
  activeCard: {
    borderColor: colors.green
  },
  pressed: {
    opacity: 0.82
  },
  row: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: spacing.sm
  },
  main: {
    flex: 1,
    minWidth: 0
  },
  title: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "800"
  },
  description: {
    marginTop: 3,
    color: colors.textMuted,
    fontSize: typography.caption,
    lineHeight: 14,
    fontWeight: "600"
  },
  metaRow: {
    marginTop: 7,
    flexDirection: "row",
    flexWrap: "wrap",
    alignItems: "center",
    gap: spacing.xs
  },
  metaPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: radius.pill,
    backgroundColor: "#f5f7fa"
  },
  metaText: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  subRow: {
    marginTop: 7,
    flexDirection: "row",
    flexWrap: "wrap",
    alignItems: "center",
    gap: spacing.sm
  },
  priority: {
    color: colors.text,
    fontSize: typography.caption,
    fontWeight: "800"
  },
  locationRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    minWidth: 0
  },
  location: {
    color: colors.textMuted,
    fontSize: typography.caption,
    fontWeight: "700"
  },
  timestamp: {
    marginTop: 7,
    color: colors.textSubtle,
    fontSize: typography.tiny,
    fontWeight: "700"
  }
});
