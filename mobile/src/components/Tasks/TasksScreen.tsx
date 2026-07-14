import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { CircleCheckBig, ClipboardList } from "lucide-react-native";

import { getCurrentUserDisplayName, getCurrentUserHotelLabel, getCurrentUserRoleCodes } from "../../auth/currentUserHelpers";
import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { TaskDetail, TaskFilterState, TaskSummary, hasActiveTaskFilters } from "../../tasks/types";
import { TaskListItem } from "./TaskListItem";
import { TaskDetailCard } from "./TaskDetailCard";
import { TaskEmptyState } from "./TaskEmptyState";

type TasksScreenProps = {
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
};

export function TasksScreen({
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
  onCancelTask
}: TasksScreenProps) {
  const taskCount = tasks.length;
  const openTasks = tasks.filter((task) => task.status !== "COMPLETED" && task.status !== "CANCELLED");
  const displayName = getCurrentUserDisplayName(currentUser);
  const hotelLabel = getCurrentUserHotelLabel(currentUser);
  const roleCodes = getCurrentUserRoleCodes(currentUser);

  return (
    <View style={styles.container}>
      <View style={styles.titleRow}>
        <View>
          <Text style={styles.kicker}>MY TASKS</Text>
          <Text style={styles.title}>Backend task queue</Text>
          <Text style={styles.subtitle} numberOfLines={1}>
            {hotelLabel}
            {displayName ? ` · ${displayName}` : ""}
            {roleCodes.length > 0 ? ` · ${roleCodes.join(", ")}` : ""}
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
      <TaskFilterRow filters={filters} onChange={onFiltersChange} onClear={onClearFilters} />

      {!isLoading && tasks.length === 0 ? (
        <TaskEmptyState
          title={staleReason ? "No saved data" : "No tasks yet"}
          message={staleReason ? "No saved data is available offline." : `When real work arrives for ${hotelLabel}, the list will appear here.`}
        />
      ) : null}

      {tasks.length > 0 ? (
        <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.list}>
          <View style={styles.summaryRow}>
            <SummaryCard label="Open" value={String(openTasks.length)} />
            <SummaryCard label="Selected" value={selectedTask?.status ?? "None"} />
          </View>

          {isRefreshing ? (
            <View style={styles.refreshBadge}>
              <CircleCheckBig color={colors.green} size={12} strokeWidth={2.3} />
              <Text style={styles.refreshText}>Refreshing</Text>
            </View>
          ) : null}

          <View style={styles.cards}>
            {tasks.map((task) => (
              <TaskListItem
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
            />
          ) : null}
        </ScrollView>
      ) : null}

      <View style={styles.refreshFooter}>
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
      </View>
    </View>
  );
}

function TaskFilterRow({
  filters,
  onChange,
  onClear
}: {
  filters: TaskFilterState;
  onChange: (filters: TaskFilterState) => void;
  onClear: () => void;
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
        <FilterChip
          label="High"
          active={filters.priority.includes("HIGH")}
          onPress={() =>
            onChange({
              ...filters,
              priority: filters.priority.includes("HIGH") ? [] : ["HIGH", "URGENT"]
            })
          }
        />
        <FilterChip
          label="Mine"
          active={filters.assignment === "mine"}
          onPress={() => onChange({ ...filters, assignment: filters.assignment === "mine" ? null : "mine" })}
        />
        <FilterChip
          label="Unassigned"
          active={filters.assignment === "unassigned"}
          onPress={() =>
            onChange({ ...filters, assignment: filters.assignment === "unassigned" ? null : "unassigned" })
          }
        />
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

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.summaryCard}>
      <Text style={styles.summaryLabel}>{label}</Text>
      <Text style={styles.summaryValue} numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

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
  }
});
