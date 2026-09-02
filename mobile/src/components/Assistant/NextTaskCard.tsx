import { Pressable, StyleSheet, Text, View } from "react-native";
import { BedDouble, ChevronRight } from "lucide-react-native";

import { colors, radius, shadow, typography } from "../../theme/tokens";
import { TaskSummary } from "../../tasks/types";

type NextTaskCardProps = {
  task: TaskSummary;
  isActionInProgress?: boolean;
  onStartTask?: () => void;
  onResumeTask?: () => void;
  onContinueTask?: () => void;
};

export function NextTaskCard({
  task,
  isActionInProgress,
  onStartTask,
  onResumeTask,
  onContinueTask
}: NextTaskCardProps) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={`Open ${task.roomOrLocation ?? task.title}`} onPress={onContinueTask} style={({ pressed }) => [styles.card, pressed && styles.pressed]}>
      <View style={styles.iconWell}>
        <BedDouble color={colors.green} size={22} strokeWidth={2.25} />
      </View>

      <View style={styles.details}>
        <Text style={styles.kicker}>{formatLocation(task.roomOrLocation) ?? "NEXT TASK"}</Text>
        <Text style={styles.title} numberOfLines={1}>
          {task.title}
        </Text>

        <View style={styles.priorityRow}>
          <View style={styles.priorityDot} />
          <Text style={styles.priorityBadge}>{task.priority}</Text>
          {task.status.toUpperCase() === "OVERDUE" ? <Text style={styles.overdue}>Overdue</Text> : null}
        </View>
      </View>
      <ChevronRight color={colors.green} size={24} strokeWidth={2.4} />
    </Pressable>
  );
}

function formatLocation(value: string | null): string | null {
  if (!value) return null;
  return /^room\s/i.test(value) ? value.toUpperCase() : value;
}

function getActionForTask(status: string): "start" | "resume" | "continue" | "none" {
  switch (status.trim().toUpperCase()) {
    case "CREATED":
    case "ASSIGNED":
    case "OVERDUE":
      return "start";
    case "WAITING":
      return "resume";
    case "STARTED":
    case "IN_PROGRESS":
      return "continue";
    default:
      return "none";
  }
}

const styles = StyleSheet.create({
  card: {
    minHeight: 104,
    marginHorizontal: 14,
    marginTop: 10,
    marginBottom: 2,
    flexDirection: "row",
    alignItems: "center",
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    backgroundColor: "#fbfffc",
    paddingHorizontal: 14,
    paddingVertical: 12,
    ...shadow.card
  },
  iconWell: {
    width: 42,
    height: 42,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.pill,
    backgroundColor: "#dff6e8"
  },
  details: {
    flex: 1,
    minWidth: 0,
    marginLeft: 9
  },
  kicker: {
    color: colors.green,
    fontSize: typography.tiny,
    fontWeight: "900",
    letterSpacing: 0
  },
  title: {
    marginTop: 2,
    color: colors.text,
    fontSize: 15,
    fontWeight: "800"
  },
  statusRow: {
    marginTop: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 5
  },
  statusLabel: {
    color: colors.textMuted,
    fontSize: 12,
    fontWeight: "800"
  },
  statusValue: {
    color: colors.text,
    fontSize: 13,
    fontWeight: "900"
  },
  room: {
    marginTop: 1,
    color: "#64748b",
    fontSize: 12,
    fontWeight: "700"
  },
  priorityRow: {
    marginTop: 4,
    flexDirection: "row",
    alignItems: "center",
    gap: 6
  },
  overdue: {
    marginLeft: 8,
    color: colors.red,
    fontSize: 12,
    fontWeight: "900"
  },
  pressed: {
    opacity: 0.82
  },
  priorityDot: {
    width: 5,
    height: 5,
    borderRadius: radius.pill,
    backgroundColor: "#f97316"
  },
  priority: {
    color: colors.textMuted,
    fontSize: 12,
    fontWeight: "800"
  },
  priorityBadge: {
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: 8,
    backgroundColor: "#fff2dc",
    color: "#b96b00",
    fontSize: 11,
    fontWeight: "900"
  },
  sla: {
    width: 58,
    alignItems: "flex-start",
    marginHorizontal: 5
  },
  slaLabel: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  slaTimeRow: {
    marginTop: 3,
    flexDirection: "row",
    alignItems: "center",
    gap: 3
  },
  slaTime: {
    color: colors.green,
    fontSize: 12,
    fontWeight: "800"
  },
  remaining: {
    alignSelf: "center",
    marginTop: 1,
    color: colors.textMuted,
    fontSize: typography.caption,
    fontWeight: "800"
  },
  startButton: {
    width: "100%",
    height: 48,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 5,
    borderRadius: 16,
    backgroundColor: "#071432"
  },
  startButtonPressed: {
    opacity: 0.88
  },
  startButtonDisabled: {
    opacity: 0.6
  },
  startLabel: {
    color: "#ffffff",
    fontSize: 14,
    fontWeight: "800"
  }
});
