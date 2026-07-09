import { Pressable, StyleSheet, Text, View } from "react-native";
import { ArrowRight, Clock3, Play, RotateCcw, ShoppingBasket } from "lucide-react-native";

import { colors, radius, shadow, typography } from "../../theme/tokens";
import { formatSlaCountdown } from "../../tasks/formatters";
import { TaskSummary } from "../../tasks/types";
import { getHomeTaskPresentation } from "../../tasks/taskBoardSelectors";

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
  const presentation = getHomeTaskPresentation(task);
  const action = getActionForTask(task.status);
  const actionHandler =
    action === "start" ? onStartTask : action === "resume" ? onResumeTask : onContinueTask;
  const actionLabel = presentation?.actionLabel ?? "Open Task";

  return (
    <View style={styles.card}>
      <View style={styles.iconWell}>
        <ShoppingBasket color={colors.green} size={21} strokeWidth={2.25} />
      </View>

      <View style={styles.details}>
        <Text style={styles.kicker}>{presentation?.bannerLabel ?? "NEXT TASK"}</Text>
        <Text style={styles.title} numberOfLines={1}>
          {task.title}
        </Text>

        <View style={styles.statusRow}>
          <Text style={styles.statusLabel}>Status</Text>
          <Text style={styles.statusValue}>{presentation?.statusLabel ?? task.status}</Text>
        </View>

        <Text style={styles.room}>{task.roomOrLocation ?? "No room assigned"}</Text>
        <View style={styles.priorityRow}>
          <View style={styles.priorityDot} />
          <Text style={styles.priority}>{task.priority}</Text>
        </View>
      </View>

      <View style={styles.sla}>
        <Text style={styles.slaLabel}>SLA REMAINING</Text>
        <View style={styles.slaTimeRow}>
          <Clock3 color={colors.green} size={13} strokeWidth={2.35} />
          <Text style={styles.slaTime}>{formatSlaCountdown(task.slaDeadline)}</Text>
        </View>
        <Text style={styles.remaining}>remaining</Text>
      </View>

      {actionHandler ? (
        <Pressable
          accessibilityRole="button"
          disabled={isActionInProgress}
          onPress={actionHandler}
          style={({ pressed }) => [
            styles.startButton,
            pressed && !isActionInProgress ? styles.startButtonPressed : null,
            isActionInProgress ? styles.startButtonDisabled : null
          ]}
        >
          {action === "resume" ? (
            <RotateCcw color="#ffffff" size={10} strokeWidth={2.4} />
          ) : action === "continue" ? (
            <ArrowRight color="#ffffff" size={10} strokeWidth={2.4} />
          ) : (
            <Play color="#ffffff" size={9} strokeWidth={2.4} fill="#ffffff" />
          )}
          <Text style={styles.startLabel}>{actionLabel}</Text>
        </Pressable>
      ) : null}
    </View>
  );
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
    minHeight: 58,
    marginHorizontal: 13,
    marginTop: 7,
    marginBottom: 2,
    flexDirection: "row",
    alignItems: "center",
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    backgroundColor: "#fbfffc",
    paddingHorizontal: 10,
    paddingVertical: 7,
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
    fontSize: 11,
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
    fontSize: 8,
    fontWeight: "800"
  },
  statusValue: {
    color: colors.text,
    fontSize: 9,
    fontWeight: "900"
  },
  room: {
    marginTop: 1,
    color: "#64748b",
    fontSize: 9,
    fontWeight: "700"
  },
  priorityRow: {
    marginTop: 4,
    flexDirection: "row",
    alignItems: "center",
    gap: 6
  },
  priorityDot: {
    width: 5,
    height: 5,
    borderRadius: radius.pill,
    backgroundColor: "#f97316"
  },
  priority: {
    color: colors.textMuted,
    fontSize: typography.caption,
    fontWeight: "800"
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
    width: 88,
    height: 34,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 5,
    borderRadius: 13,
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
    fontSize: 9,
    fontWeight: "800"
  }
});
