import { Pressable, StyleSheet, Text, View } from "react-native";

import { colors, shadow, typography } from "../../theme/tokens";
import { TaskPreviewMessage } from "../../assistant/types";

type TaskPreviewProps = {
  task: TaskPreviewMessage["task"];
  onCancel?: () => void;
  onCreateTask?: () => void;
};

export function TaskPreview({ task, onCancel, onCreateTask }: TaskPreviewProps) {
  return (
    <View style={styles.card}>
      <Text style={styles.title}>Task Preview</Text>
      <PreviewRow label="Type" value={task.type} />
      <PreviewRow label="Room" value={task.room} />
      <PreviewRow label="Description" value={task.description} />
      <PreviewRow label="Assigned to" value={task.assignedTo} />
      <PreviewRow label="Priority" value={task.priority} />
      <PreviewRow label="SLA" value={task.sla} />
      <View style={styles.actions}>
        <Pressable accessibilityRole="button" onPress={onCancel} style={styles.cancelButton}>
          <Text style={styles.cancelLabel}>Cancel</Text>
        </Pressable>
        <Pressable accessibilityRole="button" onPress={onCreateTask} style={styles.createButton}>
          <Text style={styles.createLabel}>Create Task</Text>
        </Pressable>
      </View>
    </View>
  );
}

function PreviewRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.value}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    alignSelf: "center",
    width: "90%",
    marginTop: 3,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: 10,
    backgroundColor: colors.surface,
    paddingHorizontal: 10,
    paddingTop: 9,
    paddingBottom: 7,
    ...shadow.card
  },
  title: {
    marginBottom: 5,
    color: colors.text,
    fontSize: typography.body,
    fontWeight: "900"
  },
  row: {
    minHeight: 17,
    flexDirection: "row",
    alignItems: "center"
  },
  label: {
    width: 78,
    color: colors.textMuted,
    fontSize: typography.caption,
    fontWeight: "700"
  },
  value: {
    flex: 1,
    color: colors.text,
    fontSize: typography.caption,
    fontWeight: "900"
  },
  actions: {
    marginTop: 7,
    flexDirection: "row",
    gap: 9
  },
  cancelButton: {
    flex: 1,
    minHeight: 29,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: 7,
    backgroundColor: colors.surface
  },
  createButton: {
    flex: 1,
    minHeight: 29,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 7,
    backgroundColor: colors.green
  },
  cancelLabel: {
    color: colors.text,
    fontSize: typography.body,
    fontWeight: "900"
  },
  createLabel: {
    color: "#ffffff",
    fontSize: typography.body,
    fontWeight: "900"
  }
});
