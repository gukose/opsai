import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { useMemo, useState } from "react";
import { X } from "lucide-react-native";

import { colors, shadow, typography } from "../../theme/tokens";
import { TaskPreviewMessage } from "../../assistant/types";

type TaskPreviewProps = {
  task: TaskPreviewMessage["task"];
  onCancel?: () => void;
  onCreateTask?: () => void;
  disabled?: boolean;
  roomOptions?: string[];
  onRoomChange?: (room: string) => void;
  roomMasterLoading?: boolean;
  roomMasterError?: string | null;
  onRetryRooms?: () => void;
};

export function TaskPreview({ task, onCancel, onCreateTask, disabled, roomOptions = [], onRoomChange, roomMasterLoading, roomMasterError, onRetryRooms }: TaskPreviewProps) {
  const [room, setRoom] = useState(task.room || "");
  const [pickerOpen, setPickerOpen] = useState(false);
  const [query, setQuery] = useState("");
  const rooms = useMemo(() => roomOptions.filter((value) => value.toLowerCase().includes(query.trim().toLowerCase())), [roomOptions, query]);
  const chooseRoom = (value: string) => { setRoom(value); setPickerOpen(false); onRoomChange?.(value); };
  return (
    <Modal transparent visible animationType="slide" onRequestClose={onCancel}>
      <View style={styles.backdrop}>
        <Pressable style={styles.dismissArea} onPress={onCancel} />
        <View style={styles.sheet}>
          <View style={styles.handle} />
          <View style={styles.header}><Text style={styles.title}>Task Preview</Text><Pressable accessibilityRole="button" accessibilityLabel="Close task preview" onPress={onCancel}><X color={colors.nav} size={21} /></Pressable></View>
          <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
            <Text style={styles.fieldLabel}>Room</Text>
            <Pressable style={styles.selectField} onPress={() => setPickerOpen(true)}><Text style={styles.selectValue}>{room || "Select room"}</Text><Text style={styles.selectChevron}>⌄</Text></Pressable>
            {pickerOpen ? <View style={styles.picker}>{roomMasterLoading ? <View style={styles.roomStatus}><ActivityIndicator size="small" /><Text>Loading rooms...</Text></View> : roomMasterError ? <View style={styles.roomStatus}><Text style={styles.error}>{roomMasterError}</Text><Pressable onPress={onRetryRooms}><Text style={styles.retry}>Retry</Text></Pressable></View> : <><TextInput autoFocus value={query} onChangeText={setQuery} placeholder="Search room..." style={styles.search} /><ScrollView style={styles.pickerList}>{rooms.map((value) => <Pressable key={value} style={styles.roomOption} onPress={() => chooseRoom(value)}><Text style={styles.value}>ROOM {value.replace(/^ROOM\s+/i, "")}</Text></Pressable>)}</ScrollView></>}</View> : null}
            <PreviewRow label="Issue" value={task.intent || task.type} />
            <PreviewRow label="Category" value={friendlyCategory(task.type)} />
            <PreviewRow label="Priority" value={friendlyPriority(task.priority)} />
            <PreviewRow label="Description" value={task.description} />
            <Text style={styles.assignment}>Will be assigned automatically if a suitable employee is available.</Text>
          </ScrollView>
          <View style={styles.actions}>
        <Pressable
          accessibilityRole="button"
          disabled={disabled}
          onPress={onCancel}
          style={({ pressed }) => [
            styles.cancelButton,
            pressed && !disabled ? styles.pressed : null,
            disabled ? styles.disabled : null
          ]}
        >
          <Text style={styles.cancelLabel}>Cancel</Text>
        </Pressable>
        <Pressable
          accessibilityRole="button"
          disabled={disabled}
          onPress={onCreateTask}
          style={({ pressed }) => [
            styles.createButton,
            pressed && !disabled ? styles.pressed : null,
            disabled ? styles.disabled : null
          ]}
        >
          <Text style={styles.createLabel}>{disabled ? "Creating…" : "Create Task"}</Text>
        </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

function friendlyCategory(value: string): string { const normalized = value.toUpperCase(); if (normalized.includes("MINIBAR")) return "Minibar"; if (normalized.includes("HOUSE") || normalized.includes("CLEAN")) return "Housekeeping"; if (normalized.includes("GUEST")) return "Guest Request"; return "Technical Service"; }
function friendlyPriority(value: string): string { const normalized = value.toUpperCase(); if (normalized.includes("URGENT")) return "Urgent"; if (normalized.includes("HIGH")) return "High"; if (normalized.includes("LOW")) return "Low"; return "Medium"; }

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
  backdrop: { flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(15, 23, 42, 0.36)" },
  dismissArea: { flex: 1 },
  sheet: { maxHeight: "74%", borderTopLeftRadius: 24, borderTopRightRadius: 24, backgroundColor: colors.surface, paddingTop: 8, paddingHorizontal: 18, paddingBottom: 20, ...shadow.card },
  handle: { alignSelf: "center", width: 38, height: 4, borderRadius: 3, backgroundColor: colors.cardBorder, marginBottom: 12 },
  header: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  content: { paddingVertical: 14, gap: 3 },
  fieldLabel: { color: colors.textMuted, fontSize: typography.caption, fontWeight: "700", marginTop: 4 },
  selectField: { minHeight: 42, flexDirection: "row", alignItems: "center", justifyContent: "space-between", borderWidth: 1, borderColor: colors.cardBorder, borderRadius: 9, paddingHorizontal: 12, backgroundColor: colors.surface },
  selectValue: { color: colors.text, fontSize: typography.caption, fontWeight: "900" },
  selectChevron: { color: colors.textMuted, fontSize: 18 },
  picker: { borderWidth: 1, borderColor: colors.cardBorder, borderRadius: 10, padding: 8, backgroundColor: colors.surface },
  search: { borderWidth: 1, borderColor: colors.cardBorder, borderRadius: 8, paddingHorizontal: 9, paddingVertical: 7, color: colors.text },
  pickerList: { maxHeight: 150 },
  roomOption: { paddingVertical: 9, borderBottomWidth: 1, borderBottomColor: colors.divider },
  roomStatus: { paddingVertical: 12, alignItems: "center", gap: 6 },
  error: { color: colors.red, fontSize: typography.caption },
  retry: { color: colors.green, fontWeight: "800" },
  assignment: { marginTop: 12, color: colors.textMuted, fontSize: typography.caption, lineHeight: 18 },
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
  pressed: {
    opacity: 0.9
  },
  disabled: {
    opacity: 0.55
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
