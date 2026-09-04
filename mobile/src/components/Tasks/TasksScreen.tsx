import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { CalendarDays, ChevronRight, CircleCheckBig, ClipboardList, Clock3, MapPin, BedDouble, ShoppingBasket, Wrench, UserRound } from "lucide-react-native";

import { getCurrentUserDisplayName, getCurrentUserHotelLabel, getCurrentUserRoleCodes } from "../../auth/currentUserHelpers";
import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { AssignmentCandidate, TaskDetail, TaskFilterState, TaskSummary, hasActiveTaskFilters } from "../../tasks/types";
import { TaskListItem } from "./TaskListItem";
import { TaskDetailCard } from "./TaskDetailCard";
import { TaskEmptyState } from "./TaskEmptyState";

type TasksScreenProps = {
  accessToken?: string | null;
  tasks: TaskSummary[];
  selectedTask: TaskDetail | null;
  selectedTaskId: string | null;
  currentUser: CurrentUserSnapshot | null;
  isLoading: boolean;
  isRefreshing: boolean;
  errorMessage: string | null;
  staleReason: string | null;
  cachedAt: string | null;
  filters: TaskFilterState;
  onRefresh: () => Promise<void>;
  onFiltersChange: (filters: TaskFilterState) => void;
  onClearFilters: () => void;
  onSelectTask: (taskId: string) => Promise<void>;
  onStartTask: () => Promise<void>;
  onPauseTask: () => Promise<void>;
  onResumeTask: () => Promise<void>;
  onCompleteTask: () => Promise<void>;
  onCancelTask: () => Promise<void>;
  assignmentCandidates: AssignmentCandidate[];
  onAssignmentOpen: () => Promise<void>;
  onAssignTask: (candidate: AssignmentCandidate) => Promise<void>;
  frontlineSimple?: boolean;
};

export function TasksScreen({
  accessToken,
  tasks,
  selectedTask,
  selectedTaskId,
  currentUser,
  isLoading,
  isRefreshing,
  errorMessage,
  staleReason,
  cachedAt,
  filters,
  onRefresh,
  onFiltersChange,
  onClearFilters,
  onSelectTask,
  onStartTask,
  onPauseTask,
  onResumeTask,
  onCompleteTask,
  onCancelTask,
  assignmentCandidates,
  onAssignmentOpen,
  onAssignTask,
  frontlineSimple = false
}: TasksScreenProps) {
  const taskCount = tasks.length;
  const canAssignTasks = currentUser?.permissions?.some((permission) => permission.code === "TASK_ASSIGN") ?? false;
  const openTasks = tasks.filter((task) => task.status !== "COMPLETED" && task.status !== "CANCELLED");
  const displayName = getCurrentUserDisplayName(currentUser);
  const hotelLabel = getCurrentUserHotelLabel(currentUser);
  const roleCodes = getCurrentUserRoleCodes(currentUser);

  return (
    <View style={styles.container}>
      <View style={styles.titleRow}>
        <View>
          <Text style={styles.kicker}>{frontlineSimple ? `${hotelLabel} · ${displayName}` : "MY TASKS"}</Text>
          <Text style={styles.subtitle} numberOfLines={1}>
            {frontlineSimple ? "Housekeeper" : `${hotelLabel}${displayName ? ` · ${displayName}` : ""}${roleCodes.length > 0 ? ` · ${roleCodes.join(", ")}` : ""}`}
          </Text>
        </View>
        <View style={styles.countPill}>
          <ClipboardList color={colors.green} size={12} strokeWidth={2.2} />
          <Text style={styles.countText}>{taskCount}</Text>
        </View>
      </View>

      {errorMessage ? <Text style={styles.error}>{errorMessage}</Text> : null}
      {staleReason ? (
        <Text style={styles.stale}>
          {staleReason}
          {cachedAt ? ` Last updated ${formatCacheTime(cachedAt)}.` : ""}
        </Text>
      ) : null}
      {isLoading ? <Text style={styles.loading}>Loading tasks...</Text> : null}
      <TaskFilterRow
        filters={filters}
        onChange={onFiltersChange}
        onClear={onClearFilters}
        canViewAssignmentQueue={canAssignTasks}
        frontlineSimple={frontlineSimple}
      />

      {!isLoading && tasks.length === 0 ? (
        <TaskEmptyState
          title={staleReason ? "No saved data" : "No tasks yet"}
          message={staleReason ? "No saved data is available offline." : `When real work arrives for ${hotelLabel}, the list will appear here.`}
        />
      ) : null}

      {tasks.length > 0 ? (
        <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.list}>
          <View style={styles.summaryRow}>
            <SummaryCard icon={frontlineSimple ? ClipboardList : undefined} label={frontlineSimple ? "Open Tasks" : "Open"} value={String(openTasks.length)} />
            <SummaryCard icon={frontlineSimple ? UserRound : undefined} label="Selected" value={frontlineSimple ? (selectedTask?.roomOrLocation ? formatRoom(selectedTask.roomOrLocation) : "None") : (selectedTask?.status ?? "None")} />
          </View>

          {isRefreshing ? (
            <View style={styles.refreshBadge}>
              <CircleCheckBig color={colors.green} size={12} strokeWidth={2.3} />
              <Text style={styles.refreshText}>Refreshing</Text>
            </View>
          ) : null}

          <View style={styles.cards}>
            {tasks.map((task) => (
              frontlineSimple ? <FrontlineTaskListItem
                key={task.id}
                task={task}
                active={task.id === selectedTaskId}
                onPress={() => { void onSelectTask(task.id); }}
              /> : <TaskListItem
                key={task.id}
                task={task}
                active={task.id === selectedTaskId}
                onPress={() => {
                  void onSelectTask(task.id);
                }}
              />
            ))}
          </View>

          {selectedTask ? (
            <TaskDetailCard
              task={selectedTask}
              onStart={() => {
                void onStartTask();
              }}
              onPause={() => {
                void onPauseTask();
              }}
              onResume={() => {
                void onResumeTask();
              }}
              onComplete={() => {
                void onCompleteTask();
              }}
              onCancel={() => {
                void onCancelTask();
              }}
              disabled={isRefreshing}
              assignmentCandidates={assignmentCandidates}
              onAssignmentOpen={onAssignmentOpen}
              onAssign={canAssignTasks ? onAssignTask : undefined}
              accessToken={accessToken}
              currentUser={currentUser}
              onInspectionDecision={onRefresh}
            />
          ) : null}
        </ScrollView>
      ) : null}

      {!frontlineSimple ? <View style={styles.refreshFooter}>
        <Text style={styles.refreshFooterText}>Pull style refresh is not wired yet.</Text>
        <Text
          accessibilityRole="button"
          onPress={() => {
            void onRefresh();
          }}
          style={styles.refreshLink}
        >
          Refresh
        </Text>
      </View> : null}
    </View>
  );
}

function TaskFilterRow({
  filters,
  onChange,
  onClear,
  canViewAssignmentQueue,
  frontlineSimple = false
}: {
  filters: TaskFilterState;
  onChange: (filters: TaskFilterState) => void;
  onClear: () => void;
  canViewAssignmentQueue: boolean;
  frontlineSimple?: boolean;
}) {
  const active = hasActiveTaskFilters(filters);

  return (
    <View style={styles.filterWrap}>
      <TextInput
        value={filters.q}
        onChangeText={(q) => onChange({ ...filters, q })}
        placeholder="Search tasks"
        placeholderTextColor={colors.textSubtle}
        style={styles.searchInput}
      />
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterChips}>
        {frontlineSimple ? <FilterChip label="All" active={!active} onPress={onClear} /> : null}
        <FilterChip
          label="Open"
          active={filters.status.includes("CREATED")}
          onPress={() =>
            onChange({
              ...filters,
              status: filters.status.includes("CREATED")
                ? []
                : ["CREATED", "ASSIGNED", "STARTED", "IN_PROGRESS", "WAITING", "OVERDUE"]
            })
          }
        />
        <FilterChip
          label="Done"
          active={filters.status.includes("COMPLETED")}
          onPress={() =>
            onChange({
              ...filters,
              status: filters.status.includes("COMPLETED") ? [] : ["COMPLETED"]
            })
          }
        />
        {frontlineSimple ? <FilterChip label="In Progress" active={filters.status.includes("IN_PROGRESS") || filters.status.includes("STARTED")} onPress={() => onChange({ ...filters, status: ["STARTED", "IN_PROGRESS"] })} /> : null}
        <FilterChip
          label={frontlineSimple ? "High Priority" : "High"}
          active={filters.priority.includes("HIGH")}
          onPress={() =>
            onChange({
              ...filters,
              priority: filters.priority.includes("HIGH") ? [] : ["HIGH", "URGENT"]
            })
          }
        />
        <FilterChip
          label={frontlineSimple ? "Inspection" : "Inspection Required"}
          active={filters.inspectionRequired === true}
          onPress={() => onChange({ ...filters, inspectionRequired: !filters.inspectionRequired, status: [] })}
        />
        <FilterChip
          label="Mine"
          active={filters.assignment === "mine"}
          onPress={() => onChange({ ...filters, assignment: filters.assignment === "mine" ? null : "mine" })}
        />
        {canViewAssignmentQueue ? (
          <FilterChip
            label="Needs Assignment"
            active={filters.assignment === "unassigned"}
            onPress={() =>
              onChange({ ...filters, assignment: filters.assignment === "unassigned" ? null : "unassigned" })
            }
          />
        ) : null}
        {active ? <FilterChip label="Clear" active={false} onPress={onClear} /> : null}
      </ScrollView>
    </View>
  );
}

function FilterChip({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.filterChip, active && styles.filterChipActive, pressed && styles.filterChipPressed]}
    >
      <Text style={[styles.filterChipText, active && styles.filterChipTextActive]}>{label}</Text>
    </Pressable>
  );
}

function FrontlineTaskListItem({ task, active, onPress }: { task: TaskSummary; active?: boolean; onPress?: () => void }) {
  const normalized = `${task.intentType} ${task.title}`.toUpperCase();
  const Icon = normalized.includes("MINIBAR") ? ShoppingBasket : normalized.includes("TECHNICAL") || normalized.includes("REPAIR") ? Wrench : BedDouble;
  const status = task.awaitingInspection ? "WAITING FOR INSPECTION" : task.status.replaceAll("_", " ");
  const tone = status.includes("COMPLETED") ? colors.green : status.includes("OVERDUE") ? colors.red : status.includes("IN PROGRESS") || status.includes("STARTED") ? "#3267a8" : colors.textMuted;
  return <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [styles.frontlineCard, active && styles.frontlineCardActive, pressed && styles.pressed]}>
    <View style={styles.frontlineIcon}><Icon size={22} color={colors.green} /></View><View style={styles.frontlineMain}><View style={styles.frontlineTitleRow}><Text style={styles.frontlineRoom}>{formatRoom(task.roomOrLocation)}</Text><ChevronRight size={18} color={colors.textSubtle} /></View><Text style={styles.frontlineTaskTitle} numberOfLines={1}>{task.title}</Text>{task.description ? <Text style={styles.frontlineDescription} numberOfLines={1}>{task.description}</Text> : null}<View style={styles.frontlineMeta}><Text style={[styles.frontlineBadge, { color: tone, backgroundColor: `${tone}18` }]}>{status}</Text><Text style={styles.frontlineBadge}>{task.priority}</Text>{task.roomOrLocation ? <Text style={styles.frontlineDetail}><MapPin size={11} color={colors.textMuted} /> {floorLabel(task.roomOrLocation)}</Text> : null}<Text style={styles.frontlineDetail}><Clock3 size={11} color={colors.textMuted} /> {formatWorkingDuration(task)}</Text><Text style={styles.frontlineDetail}><CalendarDays size={11} color={colors.textMuted} /> {formatDateTime(task.updatedAt)}</Text></View></View>
  </Pressable>;
}

function SummaryCard({ icon: Icon, label, value }: { icon?: typeof ClipboardList; label: string; value: string }) {
  return (
    <View style={styles.summaryCard}>
      {Icon ? <Icon color={colors.green} size={17} /> : null}
      <Text style={styles.summaryLabel}>{label}</Text>
      <Text style={styles.summaryValue} numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

function formatRoom(value: string | null): string { if (!value) return "Location unavailable"; const match = value.match(/(\d{3,4}[A-Za-z]?)/); return match ? `ROOM ${match[1]}` : value; }
function floorLabel(value: string): string { const match = value.match(/(\d{3,4})/); return match ? `Floor ${Math.floor(Number(match[1]) / 100)}` : value; }
function formatDateTime(value: string): string { const date = new Date(value); return Number.isNaN(date.getTime()) ? "Updated recently" : `Updated ${date.toLocaleDateString([], { day: "numeric", month: "short" })} ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`; }
function formatWorkingDuration(task: TaskSummary): string { const seconds = Math.max(0, Math.floor(task.actualWorkingDurationSeconds ?? 0)); if (seconds < 60) return `${seconds}s`; return `${Math.floor(seconds / 60)} min`; }

function formatCacheTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "recently";
  }

  return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: spacing.xl,
    paddingTop: 8,
    paddingBottom: 6
  },
  titleRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between",
    gap: spacing.md,
    marginBottom: 10
  },
  kicker: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  title: {
    marginTop: 2,
    color: colors.text,
    fontSize: 14,
    fontWeight: "800"
  },
  subtitle: {
    marginTop: 2,
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "700"
  },
  countPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    paddingHorizontal: 8,
    paddingVertical: 5,
    borderRadius: radius.pill,
    backgroundColor: "#f5f7fa"
  },
  countText: {
    color: colors.text,
    fontSize: typography.caption,
    fontWeight: "800"
  },
  error: {
    marginBottom: 8,
    color: colors.red,
    fontSize: typography.caption,
    fontWeight: "700"
  },
  stale: {
    marginHorizontal: 2,
    marginBottom: 5,
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  loading: {
    marginBottom: 8,
    color: colors.textMuted,
    fontSize: typography.caption,
    fontWeight: "700"
  },
  filterWrap: {
    gap: 8,
    marginBottom: 10
  },
  searchInput: {
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    paddingHorizontal: 10,
    paddingVertical: 8,
    color: colors.text,
    fontSize: typography.caption,
    fontWeight: "700"
  },
  filterChips: {
    gap: 6,
    paddingRight: spacing.md
  },
  filterChip: {
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    paddingHorizontal: 10,
    paddingVertical: 6
  },
  filterChipActive: {
    borderColor: colors.green,
    backgroundColor: "#e9f7ef"
  },
  filterChipPressed: {
    opacity: 0.82
  },
  filterChipText: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  filterChipTextActive: {
    color: colors.green
  },
  list: {
    gap: 10,
    paddingBottom: 12
  },
  summaryRow: {
    flexDirection: "row",
    gap: spacing.xs
  },
  summaryCard: {
    flex: 1,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    padding: spacing.md,
    ...shadow.card
  },
  summaryLabel: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  summaryValue: {
    marginTop: 3,
    color: colors.text,
    fontSize: 13,
    fontWeight: "800"
  },
  refreshBadge: {
    alignSelf: "flex-start",
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: radius.pill,
    backgroundColor: "#e9f7ef"
  },
  refreshText: {
    color: colors.green,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  cards: {
    gap: 8
  },
  refreshFooter: {
    marginTop: 8,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: spacing.sm
  },
  refreshFooterText: {
    color: colors.textSubtle,
    fontSize: typography.tiny,
    fontWeight: "700"
  },
  refreshLink: {
    color: colors.blue,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  pressed: {
    opacity: 0.82
  },
  frontlineCard: {
    flexDirection: "row",
    gap: spacing.sm,
    padding: spacing.md,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    ...shadow.card
  },
  frontlineCardActive: {
    borderColor: colors.green
  },
  frontlineIcon: {
    width: 42,
    height: 42,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.pill,
    backgroundColor: "#e8f5ed"
  },
  frontlineMain: {
    flex: 1,
    minWidth: 0
  },
  frontlineTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between"
  },
  frontlineRoom: {
    color: colors.text,
    fontSize: 15,
    fontWeight: "900"
  },
  frontlineTaskTitle: {
    marginTop: 2,
    color: colors.text,
    fontSize: 13,
    fontWeight: "700"
  },
  frontlineDescription: {
    marginTop: 2,
    color: colors.textMuted,
    fontSize: 11
  },
  frontlineMeta: {
    flexDirection: "row",
    flexWrap: "wrap",
    alignItems: "center",
    gap: 5,
    marginTop: 7
  },
  frontlineBadge: {
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: radius.pill,
    backgroundColor: "#f5f7fa",
    color: colors.textMuted,
    fontSize: 10,
    fontWeight: "800"
  },
  frontlineDetail: {
    flexDirection: "row",
    alignItems: "center",
    color: colors.textMuted,
    fontSize: 10
  }
});
