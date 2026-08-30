import { ComponentType } from "react";
import { useEffect, useMemo, useState } from "react";
import { FlatList, KeyboardAvoidingView, Modal, Platform, Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { CalendarDays, Clock3, Flag, MapPin, Play, Pause, RotateCcw, Ban, CheckCheck, Image as ImageIcon, FileText } from "lucide-react-native";
import { LucideProps } from "lucide-react-native";

import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";
import { AssignmentCandidate, TaskDetail } from "../../tasks/types";
import { formatDateTime, formatSlaCountdown } from "../../tasks/formatters";
import { formatAttachmentSize } from "../../assistant/attachmentMetadata";
import { TaskStatusChip } from "./TaskStatusChip";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { hasPermission } from "../../auth/currentUserHelpers";
import { InspectionApi, InspectionDetail } from "../../api/housekeeping/InspectionApi";
import { createMobileHotelOpAiClient } from "../../api/hotelOpAiClient";

type TaskDetailCardProps = {
  task: TaskDetail;
  onStart?: () => void;
  onPause?: () => void;
  onResume?: () => void;
  onComplete?: () => void;
  onCancel?: () => void;
  disabled?: boolean;
  assignmentCandidates?: AssignmentCandidate[];
  onAssignmentOpen?: () => void | Promise<void>;
  onAssign?: (candidate: AssignmentCandidate) => void | Promise<void>;
  accessToken?: string | null;
  currentUser?: CurrentUserSnapshot | null;
  onInspectionDecision?: () => void | Promise<void>;
};

export function TaskDetailCard({
  task,
  onStart,
  onPause,
  onResume,
  onComplete,
  onCancel,
  disabled,
  assignmentCandidates = [],
  onAssignmentOpen,
  onAssign, accessToken, currentUser, onInspectionDecision
}: TaskDetailCardProps) {
  const selfInspection = Boolean(currentUser && task.assigneeId && [currentUser.employeeId, currentUser.userId].filter(Boolean).includes(task.assigneeId));
  const inspectionPending = task.awaitingInspection === true;
  const inspectionMode = inspectionPending && hasPermission(currentUser ?? null, "HOUSEKEEPING_INSPECTION") && !selfInspection;
  const actions = inspectionPending ? { start:false, pause:false, resume:false, complete:false, cancel:false } : getAvailableActions(task.status);
  const [assignmentOpen, setAssignmentOpen] = useState(false);
  const [selectedCandidate, setSelectedCandidate] = useState<AssignmentCandidate | null>(null);
  const [candidateQuery, setCandidateQuery] = useState("");
  const [assignmentError, setAssignmentError] = useState<string | null>(null);
  const [assigning, setAssigning] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const [inspection, setInspection] = useState<InspectionDetail | null>(null);
  const [inspectionAnswers, setInspectionAnswers] = useState<Record<string, boolean>>({});
  const [inspectionReason, setInspectionReason] = useState("");
  const [inspectionBusy, setInspectionBusy] = useState(false);
  useEffect(() => {
    if (task.status !== "STARTED" && task.status !== "IN_PROGRESS") return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [task.status]);
  const productiveSeconds = (task.actualWorkingDurationSeconds ?? 0) + (task.status === "STARTED" || task.status === "IN_PROGRESS" ? Math.max(0, Math.floor((now - new Date(task.startedAt ?? task.updatedAt).getTime()) / 1000)) : 0);
  const formatDuration = (seconds: number) => `${String(Math.floor(seconds / 3600)).padStart(2, "0")}:${String(Math.floor((seconds % 3600) / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
  useEffect(() => { if (!inspectionMode || !accessToken) return; const api = new InspectionApi(createMobileHotelOpAiClient({ accessTokenProvider: () => accessToken })); void api.pending().then(async pending => { const match = pending.find(item => item.taskId === task.id); if (match) setInspection(await api.detail(match.id)); }).catch(() => undefined); }, [inspectionMode, accessToken, task.id]);
  const decideInspection = async (result: "PASS" | "REJECT") => { if (!inspection || inspectionBusy) return; const answers = (inspection.template?.items ?? []).map(item => ({ checklistItemId: item.id, passed: inspectionAnswers[item.id] === true })); if (result === "PASS" && answers.some(a => !a.passed)) return; if (result === "REJECT" && !inspectionReason.trim() && !answers.some(a => !a.passed)) return; setInspectionBusy(true); try { const api = new InspectionApi(createMobileHotelOpAiClient({ accessTokenProvider: () => accessToken })); await api.decide(inspection.workflow.id, result, answers, inspectionReason.trim() || undefined); await onInspectionDecision?.(); } finally { setInspectionBusy(false); } };
  const filteredCandidates = useMemo(() => {
    const query = candidateQuery.trim().toLowerCase();
    if (!query) return assignmentCandidates;
    return assignmentCandidates.filter((candidate) =>
      `${candidate.displayName} ${candidate.skillCodes.join(" ")}`.toLowerCase().includes(query)
    );
  }, [assignmentCandidates, candidateQuery]);

  const openAssignment = async () => {
    setAssignmentError(null);
    setCandidateQuery("");
    setSelectedCandidate(null);
    setAssignmentOpen(true);
    try {
      await onAssignmentOpen?.();
    } catch {
      setAssignmentError("Unable to load employees. Please try again.");
    }
  };

  const confirmAssignment = async () => {
    if (!selectedCandidate || !onAssign || assigning) return;
    setAssigning(true);
    setAssignmentError(null);
    try {
      await onAssign(selectedCandidate);
      setAssignmentOpen(false);
    } catch (error) {
      setAssignmentError(error instanceof Error ? error.message : "Assignment failed. Try again.");
    } finally {
      setAssigning(false);
    }
  };

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

      <Text style={styles.description}>{`Problem: ${task.description}`}</Text>

      <View style={styles.metaGrid}>
        <DetailRow icon={Flag} label="Priority" value={task.priority} />
        <DetailRow icon={Clock3} label="SLA" value={formatSlaCountdown(task.slaDeadline)} />
        {task.roomOrLocation ? (
          <DetailRow icon={MapPin} label="Lokasyon" value={formatLocation(task.roomOrLocation)} />
        ) : null}
        <DetailRow icon={CalendarDays} label="Updated" value={formatDateTime(task.updatedAt)} />
      </View>

      <View style={styles.infoRow}>
        <InfoChip label="Intent" value={task.intentType} />
        <InfoChip label="Source" value={task.source} />
      </View>
      {inspectionMode ? <View style={styles.inspectionPanel}><Text style={styles.inspectionTitle}>Inspection Required</Text>{inspection?.template?.items.map(item => <View key={item.id} style={styles.inspectionItem}><Text style={styles.attachmentSectionTitle}>{item.label ?? item.code}</Text><View style={styles.inspectionChoices}><Pressable onPress={() => setInspectionAnswers(a => ({ ...a, [item.id]: true }))}><Text style={inspectionAnswers[item.id] === true ? styles.selectedChoice : styles.choice}>PASS</Text></Pressable><Pressable onPress={() => setInspectionAnswers(a => ({ ...a, [item.id]: false }))}><Text style={inspectionAnswers[item.id] === false ? styles.selectedReject : styles.choice}>FAIL</Text></Pressable></View></View>)}<TextInput value={inspectionReason} onChangeText={setInspectionReason} placeholder="Rejection reason" style={styles.rejectionInput}/><View style={styles.inspectionChoices}><Pressable disabled={inspectionBusy} onPress={() => void decideInspection("PASS")}><Text style={styles.approveLabel}>Approve Inspection</Text></Pressable><Pressable disabled={inspectionBusy} onPress={() => void decideInspection("REJECT")}><Text style={styles.rejectLabel}>Reject Inspection</Text></Pressable></View>{inspection?.history.map(h => <Text key={h.id} style={styles.historyText}>Attempt {h.attempt}: {h.result} · {h.qualityScore ?? 0}%{h.rejectionReason ? ` · ${h.rejectionReason}` : ""}</Text>)}</View> : null}
      <Text style={styles.description}>{task.status === "STARTED" || task.status === "IN_PROGRESS" ? `Working · ${formatDuration(productiveSeconds)}` : task.status === "WAITING" ? `${task.awaitingInspection ? "Waiting for Inspection" : "Paused"} · ${formatDuration(task.totalPauseDurationSeconds ?? 0)}\nWorked · ${formatDuration(task.actualWorkingDurationSeconds ?? 0)}` : task.completedAt ? `Worked · ${formatDuration(task.actualWorkingDurationSeconds ?? 0)}` : ""}</Text>
      {task.intentType === "MINIBAR" || task.priority === "URGENT" ? <Text style={styles.kicker}>FLASH · Minibar Check</Text> : null}

      {onAssign ? (
        <View style={styles.assignmentPanel}>
          <View style={styles.assignmentHeader}>
            <Text style={styles.attachmentSectionTitle}>{task.assignmentLabel ? "Assigned" : "Needs Assignment"}</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={task.assignmentLabel ? "Reassign task" : "Assign task"}
              disabled={disabled}
              onPress={() => { void openAssignment(); }}
              style={({ pressed }) => [styles.assignButton, pressed && styles.actionPressed, disabled && styles.actionDisabled]}
            >
              <Text style={styles.assignButtonLabel}>{task.assignmentLabel ? "Reassign" : "Assign"}</Text>
            </Pressable>
          </View>
        </View>
      ) : null}

      <AssignmentModal
        visible={assignmentOpen}
        task={task}
        candidates={filteredCandidates}
        selectedCandidate={selectedCandidate}
        query={candidateQuery}
        error={assignmentError}
        assigning={assigning}
        onQueryChange={setCandidateQuery}
        onSelect={setSelectedCandidate}
        onCancel={() => setAssignmentOpen(false)}
        onConfirm={() => { void confirmAssignment(); }}
      />

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
        {actions.complete && !task.awaitingInspection ? (
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

function AssignmentModal({
  visible, task, candidates, selectedCandidate, query, error, assigning, onQueryChange, onSelect, onCancel, onConfirm
}: {
  visible: boolean;
  task: TaskDetail;
  candidates: AssignmentCandidate[];
  selectedCandidate: AssignmentCandidate | null;
  query: string;
  error: string | null;
  assigning: boolean;
  onQueryChange: (value: string) => void;
  onSelect: (candidate: AssignmentCandidate) => void;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onCancel}>
      <KeyboardAvoidingView style={styles.modalRoot} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <Pressable style={styles.modalBackdrop} onPress={onCancel} accessibilityLabel="Close assignment dialog" />
        <View style={styles.assignmentSheet} accessibilityViewIsModal accessibilityLabel="Assign task">
          <View style={styles.sheetHandle} />
          <Text style={styles.sheetTitle}>{task.assignmentLabel ? "Reassign task" : "Assign task"}</Text>
          <Text style={styles.sheetTask} numberOfLines={2}>{task.roomOrLocation ? `${task.roomOrLocation} · ` : ""}{task.title}</Text>
          <TextInput
            accessibilityLabel="Search employee"
            placeholder="Search employee..."
            placeholderTextColor={colors.textSubtle}
            value={query}
            onChangeText={onQueryChange}
            style={styles.candidateSearch}
            autoCapitalize="none"
            autoCorrect={false}
          />
          <FlatList
            data={candidates}
            keyExtractor={(candidate) => candidate.assigneeId}
            keyboardShouldPersistTaps="handled"
            style={styles.candidateList}
            contentContainerStyle={styles.candidateListContent}
            renderItem={({ item }) => {
              const selected = selectedCandidate?.assigneeId === item.assigneeId;
              return (
                <Pressable
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  onPress={() => onSelect(item)}
                  style={[styles.candidateRow, selected && styles.candidateRowSelected]}
                >
                  <View style={[styles.radio, selected && styles.radioSelected]}>{selected ? <View style={styles.radioDot} /> : null}</View>
                  <View style={styles.candidateBody}>
                    <Text style={styles.candidateName}>{item.displayName}</Text>
                    <Text style={styles.candidateMeta} numberOfLines={2}>
                      {item.skillCodes.length ? item.skillCodes.join(" · ") : "Suitable employee"} · {item.onShift ? "On shift" : "Off shift"} · {item.available ? "Available" : "Busy"} · {item.workload} active tasks
                    </Text>
                  </View>
                </Pressable>
              );
            }}
            ListEmptyComponent={error ? null : <Text style={styles.attachmentEmpty}>No eligible employees are currently available.</Text>}
          />
          {error ? <Text style={styles.modalError}>{error}</Text> : null}
          <View style={styles.sheetActions}>
            <Pressable accessibilityRole="button" onPress={onCancel} disabled={assigning} style={styles.cancelButton}>
              <Text style={styles.cancelButtonLabel}>Cancel</Text>
            </Pressable>
            <Pressable accessibilityRole="button" onPress={onConfirm} disabled={!selectedCandidate || assigning} style={[styles.confirmButton, (!selectedCandidate || assigning) && styles.actionDisabled]}>
              <Text style={styles.confirmButtonLabel}>{assigning ? "Assigning..." : "Assign"}</Text>
            </Pressable>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
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
              {attachment.transcript ? <FileText color={colors.blue} size={12} strokeWidth={2.2} /> : <ImageIcon color={colors.blue} size={12} strokeWidth={2.2} />}
            </View>
            <View style={styles.attachmentBody}>
              <Text style={styles.attachmentName} numberOfLines={1}>{attachment.originalFileName}</Text>
              <Text style={styles.attachmentMeta} numberOfLines={2}>
                {attachment.type} · {attachment.declaredMimeType} · {formatAttachmentSize(attachment.declaredSizeBytes)}
                {attachment.widthPx && attachment.heightPx ? ` · ${attachment.widthPx}x${attachment.heightPx}` : ""}
              </Text>
              {attachment.transcript ? (
                <Text style={styles.attachmentTranscript} numberOfLines={4}>
                  {attachment.transcript}
                </Text>
              ) : null}
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

function formatLocation(value: string): string {
  const room = value.match(/^Room\s+(.+)$/i);
  return room ? `${room[1]} numaralı oda` : value;
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
  attachmentTranscript: {
    marginTop: 4,
    color: colors.text,
    fontSize: typography.caption,
    lineHeight: 17,
    fontWeight: "600"
  },
  assignmentPanel: {
    marginTop: 10,
    gap: 6
  },
  assignmentHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8
  },
  assignButton: {
    minHeight: 32,
    paddingHorizontal: 14,
    borderRadius: radius.pill,
    backgroundColor: colors.blue,
    alignItems: "center",
    justifyContent: "center"
  },
  assignButtonLabel: {
    color: "#ffffff",
    fontSize: typography.caption,
    fontWeight: "900"
  },
  assignmentCandidate: {
    minHeight: 44,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceMuted,
    paddingHorizontal: 9,
    paddingVertical: 7
  },
  assignmentCandidateBody: {
    flex: 1,
    minWidth: 0
  },
  assignmentCandidateName: {
    color: colors.text,
    fontSize: typography.caption,
    fontWeight: "800"
  },
  assignmentCandidateMeta: {
    marginTop: 2,
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "700"
  },
  assignLabel: {
    color: colors.blue,
    fontSize: typography.caption,
    fontWeight: "900"
  },
  modalRoot: {
    flex: 1,
    justifyContent: "flex-end"
  },
  modalBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(15, 23, 42, 0.38)"
  },
  assignmentSheet: {
    maxHeight: "82%",
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    backgroundColor: colors.surface,
    paddingHorizontal: spacing.md,
    paddingTop: 9,
    paddingBottom: 22,
    ...shadow.card
  },
  sheetHandle: {
    alignSelf: "center",
    width: 38,
    height: 4,
    borderRadius: 4,
    backgroundColor: colors.cardBorder,
    marginBottom: 12
  },
  sheetTitle: {
    color: colors.text,
    fontSize: 18,
    fontWeight: "900"
  },
  sheetTask: {
    marginTop: 3,
    color: colors.textMuted,
    fontSize: typography.caption,
    fontWeight: "700"
  },
  candidateSearch: {
    marginTop: 12,
    minHeight: 42,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceMuted,
    color: colors.text,
    paddingHorizontal: 12,
    fontSize: typography.caption
  },
  candidateList: {
    marginTop: 8,
    minHeight: 80
  },
  candidateListContent: {
    gap: 6,
    paddingBottom: 6
  },
  candidateRow: {
    minHeight: 58,
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.md,
    paddingHorizontal: 10,
    paddingVertical: 8
  },
  candidateRowSelected: {
    borderColor: colors.blue,
    backgroundColor: "#f1f6ff"
  },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: colors.textSubtle,
    alignItems: "center",
    justifyContent: "center"
  },
  radioSelected: {
    borderColor: colors.blue
  },
  radioDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: colors.blue
  },
  candidateBody: {
    flex: 1,
    minWidth: 0
  },
  candidateName: {
    color: colors.text,
    fontSize: typography.caption,
    fontWeight: "900"
  },
  candidateMeta: {
    marginTop: 2,
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "700"
  },
  modalError: {
    marginTop: 4,
    color: colors.red,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  sheetActions: {
    marginTop: 10,
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 8
  },
  cancelButton: {
    minHeight: 40,
    paddingHorizontal: 16,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceMuted,
    alignItems: "center",
    justifyContent: "center"
  },
  cancelButtonLabel: {
    color: colors.textMuted,
    fontSize: typography.caption,
    fontWeight: "900"
  },
  confirmButton: {
    minHeight: 40,
    paddingHorizontal: 18,
    borderRadius: radius.pill,
    backgroundColor: colors.blue,
    alignItems: "center",
    justifyContent: "center"
  },
  confirmButtonLabel: {
    color: "#ffffff",
    fontSize: typography.caption,
    fontWeight: "900"
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
  },
  inspectionPanel:{marginTop:spacing.md,padding:spacing.md,borderWidth:1,borderColor:colors.greenBorder,borderRadius:radius.md,backgroundColor:colors.greenSoft},
  inspectionTitle:{fontSize:16,fontWeight:"900",color:colors.text}, inspectionItem:{paddingVertical:spacing.sm}, inspectionChoices:{flexDirection:"row",gap:spacing.md,alignItems:"center",marginTop:spacing.xs}, choice:{color:colors.textMuted,fontWeight:"800"}, selectedChoice:{color:colors.green,fontWeight:"900"}, selectedReject:{color:colors.red,fontWeight:"900"}, rejectionInput:{minHeight:50,backgroundColor:colors.surface,borderWidth:1,borderColor:colors.cardBorder,borderRadius:radius.sm,padding:spacing.sm,marginTop:spacing.sm}, approveLabel:{color:colors.green,fontWeight:"900"}, rejectLabel:{color:colors.red,fontWeight:"900"}, historyText:{color:colors.textMuted,fontSize:11,marginTop:spacing.xs}
});
