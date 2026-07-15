import { ComponentType } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { CalendarDays, Clock3, Flag, MapPin, Play, Pause, RotateCcw, Ban, CheckCheck, Image as ImageIcon } from "lucide-react-native";
import { LucideProps } from "lucide-react-native";

import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";
import { TaskDetail } from "../../tasks/types";
import { formatDateTime, formatSlaCountdown } from "../../tasks/formatters";
import { formatAttachmentSize } from "../../assistant/attachmentMetadata";
import { TaskStatusChip } from "./TaskStatusChip";

type TaskDetailCardProps = {
  task: TaskDetail;
  onStart?: () => void;
  onPause?: () => void;
  onResume?: () => void;
  onComplete?: () => void;
  onCancel?: () => void;
  disabled?: boolean;
};

export function TaskDetailCard({
  task,
  onStart,
  onPause,
  onResume,
  onComplete,
  onCancel,
  disabled
}: TaskDetailCardProps) {
  const actions = getAvailableActions(task.status);

  return (
    <View style={styles.card}>
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <Text style={styles.kicker}>TASK DETAIL</Text>
          <Text style={styles.title} numberOfLines={2}>
            {task.title}
          </Text>
        </View>
        <TaskStatusChip status={task.status} />
      </View>

      <Text style={styles.description}>{task.description}</Text>

      <View style={styles.metaGrid}>
        <DetailRow icon={Flag} label="Priority" value={task.priority} />
        <DetailRow icon={Clock3} label="SLA" value={formatSlaCountdown(task.slaDeadline)} />
        {task.roomOrLocation ? (
          <DetailRow icon={MapPin} label="Location" value={task.roomOrLocation} />
        ) : null}
        <DetailRow icon={CalendarDays} label="Updated" value={formatDateTime(task.updatedAt)} />
      </View>

      <View style={styles.infoRow}>
        <InfoChip label="Intent" value={task.intentType} />
        <InfoChip label="Source" value={task.source} />
      </View>

      <View style={styles.infoRow}>
        <InfoChip label="Assignment" value={task.assignmentLabel ?? "Unassigned"} />
        <InfoChip label="Assignee" value={task.assigneeType ?? "N/A"} />
      </View>

      <TaskAttachmentSection task={task} />

      <View style={styles.actions}>
        {actions.start ? (
          <ActionButton
            icon={Play}
            label="Start"
            onPress={onStart}
            tone="primary"
            disabled={disabled}
          />
        ) : null}
        {actions.pause ? (
          <ActionButton
            icon={Pause}
            label="Pause"
            onPress={onPause}
            tone="secondary"
            disabled={disabled}
          />
        ) : null}
        {actions.resume ? (
          <ActionButton
            icon={RotateCcw}
            label="Resume"
            onPress={onResume}
            tone="secondary"
            disabled={disabled}
          />
        ) : null}
        {actions.complete ? (
          <ActionButton
            icon={CheckCheck}
            label="Complete"
            onPress={onComplete}
            tone="success"
            disabled={disabled}
          />
        ) : null}
        {actions.cancel ? (
          <ActionButton
            icon={Ban}
            label="Cancel"
            onPress={onCancel}
            tone="danger"
            disabled={disabled}
          />
        ) : null}
      </View>
    </View>
  );
}

function TaskAttachmentSection({ task }: { task: TaskDetail }) {
  const attachments = task.attachments ?? [];
  return (
    <View style={styles.attachmentSection}>
      <Text style={styles.attachmentSectionTitle}>Attachments</Text>
      {attachments.length === 0 ? (
        <Text style={styles.attachmentEmpty}>No registered attachment metadata.</Text>
      ) : (
        attachments.map((attachment) => (
          <View key={`${attachment.attachmentId}-${attachment.sourceType}`} style={styles.attachmentRow}>
            <View style={styles.detailIcon}>
              <ImageIcon color={colors.blue} size={12} strokeWidth={2.2} />
            </View>
            <View style={styles.attachmentBody}>
              <Text style={styles.attachmentName} numberOfLines={1}>{attachment.originalFileName}</Text>
              <Text style={styles.attachmentMeta} numberOfLines={2}>
                {attachment.type} · {attachment.declaredMimeType} · {formatAttachmentSize(attachment.declaredSizeBytes)}
                {attachment.widthPx && attachment.heightPx ? ` · ${attachment.widthPx}x${attachment.heightPx}` : ""}
              </Text>
              <Text style={styles.attachmentMeta} numberOfLines={1}>
                Registered metadata · {attachment.sourceType === "VISION_ANALYSIS" ? "Vision provenance" : "Assistant message"}
              </Text>
            </View>
          </View>
        ))
      )}
    </View>
  );
}

type DetailRowProps = {
  icon: ComponentType<LucideProps>;
  label: string;
  value: string;
};

function DetailRow({ icon: Icon, label, value }: DetailRowProps) {
  return (
    <View style={styles.detailRow}>
      <View style={styles.detailIcon}>
        <Icon color={colors.blue} size={12} strokeWidth={2.2} />
      </View>
      <View style={styles.detailText}>
        <Text style={styles.detailLabel}>{label}</Text>
        <Text style={styles.detailValue} numberOfLines={1}>
          {value}
        </Text>
      </View>
    </View>
  );
}

type InfoChipProps = {
  label: string;
  value: string;
};

function InfoChip({ label, value }: InfoChipProps) {
  return (
    <View style={styles.infoChip}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoValue} numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

type ActionButtonProps = {
  icon: ComponentType<LucideProps>;
  label: string;
  onPress?: () => void;
  tone: "primary" | "secondary" | "success" | "danger";
  disabled?: boolean;
};

function ActionButton({ icon: Icon, label, onPress, tone, disabled }: ActionButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.actionButton,
        getActionToneStyle(tone),
        pressed && !disabled ? styles.actionPressed : null,
        disabled ? styles.actionDisabled : null
      ]}
    >
      <Icon color="#ffffff" size={12} strokeWidth={2.4} />
      <Text style={styles.actionLabel}>{label}</Text>
    </Pressable>
  );
}

function getAvailableActions(status: string) {
  switch (status.toUpperCase()) {
    case "CREATED":
    case "ASSIGNED":
      return { start: true, pause: false, resume: false, complete: false, cancel: true };
    case "STARTED":
    case "IN_PROGRESS":
      return { start: false, pause: true, resume: false, complete: true, cancel: true };
    case "WAITING":
      return { start: false, pause: false, resume: true, complete: true, cancel: true };
    case "OVERDUE":
      return { start: false, pause: false, resume: false, complete: true, cancel: true };
    default:
      return { start: false, pause: false, resume: false, complete: false, cancel: false };
  }
}

function getActionToneStyle(tone: ActionButtonProps["tone"]) {
  switch (tone) {
    case "primary":
      return styles.action_primary;
    case "secondary":
      return styles.action_secondary;
    case "success":
      return styles.action_success;
    case "danger":
      return styles.action_danger;
  }
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
  header: {
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between",
    gap: spacing.sm
  },
  headerLeft: {
    flex: 1,
    minWidth: 0
  },
  kicker: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  title: {
    marginTop: 3,
    color: colors.text,
    fontSize: 14,
    fontWeight: "800"
  },
  description: {
    marginTop: 6,
    color: colors.textMuted,
    fontSize: typography.caption,
    lineHeight: 14,
    fontWeight: "600"
  },
  metaGrid: {
    marginTop: 10,
    gap: spacing.xs
  },
  detailRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingVertical: 2
  },
  detailIcon: {
    width: 24,
    height: 24,
    borderRadius: radius.pill,
    backgroundColor: "#eef4ff",
    alignItems: "center",
    justifyContent: "center"
  },
  detailText: {
    flex: 1,
    minWidth: 0
  },
  detailLabel: {
    color: colors.textSubtle,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  detailValue: {
    marginTop: 1,
    color: colors.text,
    fontSize: typography.caption,
    fontWeight: "700"
  },
  infoRow: {
    marginTop: 8,
    flexDirection: "row",
    gap: spacing.xs
  },
  infoChip: {
    flex: 1,
    minWidth: 0,
    borderRadius: radius.md,
    backgroundColor: "#f7f8fa",
    paddingHorizontal: 8,
    paddingVertical: 7
  },
  infoLabel: {
    color: colors.textSubtle,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  infoValue: {
    marginTop: 2,
    color: colors.text,
    fontSize: typography.caption,
    fontWeight: "700"
  },
  actions: {
    marginTop: 12,
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8
  },
  attachmentSection: {
    marginTop: 10,
    gap: 6
  },
  attachmentSectionTitle: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  attachmentEmpty: {
    color: colors.textSubtle,
    fontSize: typography.tiny,
    fontWeight: "700"
  },
  attachmentRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    borderRadius: radius.md,
    backgroundColor: "#f7f8fa",
    padding: 7
  },
  attachmentBody: {
    flex: 1,
    minWidth: 0
  },
  attachmentName: {
    color: colors.text,
    fontSize: typography.caption,
    fontWeight: "800"
  },
  attachmentMeta: {
    marginTop: 1,
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "700"
  },
  actionButton: {
    minWidth: 84,
    height: 32,
    paddingHorizontal: 10,
    borderRadius: 12,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6
  },
  action_primary: {
    backgroundColor: colors.green
  },
  action_secondary: {
    backgroundColor: colors.nav
  },
  action_success: {
    backgroundColor: colors.blue
  },
  action_danger: {
    backgroundColor: colors.red
  },
  actionLabel: {
    color: "#ffffff",
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  actionPressed: {
    opacity: 0.86
  },
  actionDisabled: {
    opacity: 0.55
  }
});
