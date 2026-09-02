import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { AlertTriangle, BedDouble, ClipboardCheck, ChevronRight, ShoppingBasket, Star, Users, Wrench, Check } from "lucide-react-native";

import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";
import { hasPermission } from "../../auth/currentUserHelpers";
import { getCurrentUserDisplayName } from "../../auth/currentUserHelpers";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { TaskDetail, TaskSummary } from "../../tasks/types";
import { TaskBoardOverview } from "../../tasks/taskBoardSelectors";
import { UserExperienceMode } from "../../auth/experienceMode";
import { NextTaskCard } from "./NextTaskCard";
import { taskCompletionXp } from "../../tasks/taskRewardPolicy";

type Props = {
  mode: UserExperienceMode;
  currentUser: CurrentUserSnapshot | null;
  tasks: TaskSummary[];
  homeTask: TaskSummary | null;
  overview: TaskBoardOverview;
  actionInProgress?: boolean;
  taskError?: string | null;
  taskLoading?: boolean;
  onStartTask?: () => void;
  onResumeTask?: () => void;
  onOpenTask?: (taskId: string) => void;
  onAssignTask?: (taskId: string) => void;
};

export function RoleAdaptiveHome({ mode, currentUser, tasks, homeTask, overview, actionInProgress, taskError, taskLoading, onStartTask, onResumeTask, onOpenTask, onAssignTask }: Props) {
  if (mode === "FRONTLINE_SIMPLE") {
    return <FrontlineHome currentUser={currentUser} task={homeTask} tasks={tasks} overview={overview} actionInProgress={actionInProgress} taskError={taskError} taskLoading={taskLoading} onStartTask={onStartTask} onResumeTask={onResumeTask} onOpenTask={onOpenTask} />;
  }
  if (mode === "SUPERVISOR") {
    return <SupervisorHome currentUser={currentUser} tasks={tasks} overview={overview} onOpenTask={onOpenTask} onAssignTask={onAssignTask} />;
  }
  return <ManagerHome tasks={tasks} overview={overview} onOpenTask={onOpenTask} />;
}

export function FrontlineCompletionScreen({ task, tasks, onOpenTask, onViewTasks }: { task: TaskDetail; tasks: TaskSummary[]; onOpenTask?: (id: string) => void; onViewTasks?: () => void }) {
  const completed = tasks.filter((item) => item.status.toUpperCase() === "COMPLETED").length;
  const total = tasks.length;
  const percent = total > 0 ? Math.round((completed / total) * 100) : 0;
  const nextTask = tasks.find((item) => item.id !== task.id && !["COMPLETED", "CANCELLED"].includes(item.status.toUpperCase()));
  return <View style={styles.completionScreen}>
    <View style={[styles.celebration, { minHeight: 218, paddingVertical: 30, borderBottomLeftRadius: 34, borderBottomRightRadius: 34, backgroundColor: "#16834b", overflow: "hidden" }]}><Confetti /><View style={[styles.successCircle, { width: 82, height: 82, borderRadius: 41, backgroundColor: "#35a866", borderWidth: 5, borderColor: "rgba(255,255,255,0.22)" }]}><Check size={40} color="#fff" strokeWidth={3} /></View><Text style={[styles.greatTitle, { fontSize: 30, marginTop: 12 }]}>Great!</Text><Text style={[styles.completedText, { fontSize: 15 }]}>{formatLocation(task.roomOrLocation) ?? task.title} completed.</Text></View>
    <View style={[styles.xpBlock, { marginHorizontal: 36, marginTop: -20, paddingVertical: 12, borderRadius: 16, backgroundColor: colors.surface, shadowColor: "#000", shadowOpacity: 0.12, shadowRadius: 8, shadowOffset: { width: 0, height: 3 }, elevation: 3 }]}><Text style={[styles.xpValue, { fontSize: 25 }]}>+{taskCompletionXp()} XP</Text><Text style={styles.xpLabel}>Earned!</Text></View>
    {total > 0 ? <View style={[styles.progressCard, { marginHorizontal: 14, marginTop: 20, padding: 14, borderRadius: 18 }]}><View style={styles.progressHeader}><View><Text style={styles.progressTitle}>Daily Progress</Text><Text style={[styles.progressValue, { fontSize: 25, marginTop: 3 }]}>{completed} <Text style={{ color: colors.textMuted, fontSize: 17, fontWeight: "700" }}>/ {total}</Text></Text></View><View style={{ width: 32, height: 32, borderRadius: 16, alignItems: "center", justifyContent: "center", backgroundColor: "#fff7df" }}><Star size={18} color="#d99a23" fill="#f7c948" /></View></View><View style={[styles.progressTrack, { height: 9, marginTop: 10 }]}><View style={[styles.progressFill, { width: `${percent}%` }]} /></View><Text style={styles.progressCaption}>{percent}% completed</Text></View> : null}
    <Text style={styles.completionNextTitle}>Next Task</Text>
    {nextTask ? <><Pressable style={styles.completionTask} onPress={() => onOpenTask?.(nextTask.id)}><TaskTypeIcon task={nextTask} /><View style={styles.otherMain}><Text style={styles.room}>{formatLocation(nextTask.roomOrLocation) ?? "Location unavailable"}</Text><Text style={styles.task}>{nextTask.title}</Text><PriorityBadge value={nextTask.priority} /></View><ChevronRight color={colors.textMuted} size={20} /></Pressable><Pressable style={styles.nextButton} onPress={() => onOpenTask?.(nextTask.id)}><Text style={styles.nextButtonText}>GO TO NEXT TASK</Text></Pressable></> : <View style={styles.noNext}><Text style={styles.noNextText}>All assigned tasks are done.</Text><Pressable style={styles.nextButton} onPress={onViewTasks}><Text style={styles.nextButtonText}>VIEW MY TASKS</Text></Pressable></View>}
  </View>;
}

function Confetti() {
  const pieces = [
    { left: "18%", top: 24, color: "#f7c948", rotate: "-18deg" }, { left: "28%", top: 52, color: "#8bd5a5", rotate: "28deg" },
    { left: "38%", top: 14, color: "#7cc7f2", rotate: "-35deg" }, { left: "60%", top: 18, color: "#f7c948", rotate: "22deg" },
    { left: "72%", top: 48, color: "#8bd5a5", rotate: "-22deg" }, { left: "82%", top: 27, color: "#f29b5b", rotate: "35deg" },
    { left: "24%", top: 104, color: "#63b982", rotate: "12deg" }, { left: "76%", top: 103, color: "#f7c948", rotate: "-12deg" },
    { left: "12%", top: 78, color: "#7cc7f2", rotate: "45deg" }, { left: "87%", top: 82, color: "#63b982", rotate: "-30deg" }
  ];
  return <View pointerEvents="none" style={StyleSheet.absoluteFillObject}>{pieces.map((piece, index) => <View key={index} style={{ position: "absolute", left: piece.left as any, top: piece.top, width: 7, height: 12, borderRadius: 2, backgroundColor: piece.color, transform: [{ rotate: piece.rotate }] }} />)}</View>;
}

function FrontlineHome({ currentUser, task, tasks, overview, actionInProgress, taskError, taskLoading, onStartTask, onResumeTask, onOpenTask }: { currentUser: CurrentUserSnapshot | null; task: TaskSummary | null; tasks: TaskSummary[]; overview: TaskBoardOverview; actionInProgress?: boolean; taskError?: string | null; taskLoading?: boolean; onStartTask?: () => void; onResumeTask?: () => void; onOpenTask?: (id: string) => void }) {
  const otherTasks = tasks.filter((item) => item.id !== task?.id && !["COMPLETED", "CANCELLED"].includes(item.status.toUpperCase()));
  const completed = tasks.filter((item) => item.status.toUpperCase() === "COMPLETED").length;
  const total = tasks.length;
  const percent = total > 0 ? Math.round((completed / total) * 100) : 0;
  return <View>{<View style={styles.greetingCard}><Text style={styles.greetingEmoji}>👋</Text><View><Text style={styles.greetingTitle}>Hello {getCurrentUserDisplayName(currentUser).replace("Signed-in user", "there")}</Text><Text style={styles.greetingBody}>Have a great shift.</Text></View></View>}{total > 0 ? <View style={styles.progressCard}><View style={styles.progressHeader}><View><Text style={styles.progressTitle}>Daily Progress</Text><Text style={styles.progressValue}>{completed} / {total}</Text></View><Star size={20} color="#d99a23" fill="#f7c948" /></View><View style={styles.progressTrack}><View style={[styles.progressFill, { width: `${percent}%` }]} /></View><Text style={styles.progressCaption}>{percent}% completed</Text></View> : null}{task ? <NextTaskCard task={task} isActionInProgress={actionInProgress} onContinueTask={() => onOpenTask?.(task.id)} /> : taskError ? <View style={styles.empty}><Text style={styles.emptyTitle}>Unable to load tasks</Text><Text style={styles.emptyBody}>{taskError}</Text></View> : taskLoading ? null : <View style={styles.empty}><Text style={styles.emptyTitle}>No next task</Text><Text style={styles.emptyBody}>Your assigned work will appear here.</Text></View>}{otherTasks.length > 0 ? <View style={styles.other}><View style={styles.otherHeader}><Text style={styles.otherTitle}>Other Tasks</Text><Text style={styles.otherCount}>{otherTasks.length}</Text></View><ScrollView nestedScrollEnabled showsVerticalScrollIndicator={otherTasks.length > 3} style={styles.otherScroll}>{otherTasks.map((item) => <Pressable key={item.id} style={styles.otherRow} onPress={() => onOpenTask?.(item.id)}><TaskTypeIcon task={item} /><View style={styles.otherMain}><Text style={styles.room}>{formatLocation(item.roomOrLocation) ?? "Location unavailable"}</Text><Text style={styles.task}>{item.title}</Text><PriorityBadge value={item.priority} /></View><ChevronRight color={colors.textMuted} size={20} /></Pressable>)}</ScrollView></View> : null}</View>;
}

function formatLocation(value: string | null): string | null { if (!value) return null; if (/^room\s/i.test(value)) return value.toUpperCase(); return /^\d+$/.test(value.trim()) ? `ROOM ${value.trim()}` : value; }
function TaskTypeIcon({ task }: { task: TaskSummary }) { const normalized = `${task.intentType} ${task.title}`.toUpperCase(); const Icon = normalized.includes("MINIBAR") ? ShoppingBasket : normalized.includes("TECHNICAL") || normalized.includes("REPAIR") ? Wrench : BedDouble; return <View style={styles.taskIcon}><Icon size={20} color={colors.green} /></View>; }
function PriorityBadge({ value }: { value: string }) { const normalized = value.toUpperCase(); const color = normalized.includes("HIGH") || normalized.includes("URGENT") ? colors.red : normalized.includes("LOW") ? colors.green : "#b96b00"; return <Text style={[styles.priorityBadge, { color, backgroundColor: `${color}18` }]}>{value}</Text>; }

function SupervisorHome({ currentUser, tasks, overview, onOpenTask, onAssignTask }: Omit<Props, "mode" | "homeTask" | "actionInProgress" | "onStartTask" | "onResumeTask">) {
  const unassigned = tasks.filter((task) => !task.assignmentLabel && !["COMPLETED", "CANCELLED"].includes(task.status.toUpperCase()));
  const inspections = tasks.filter((task) => task.awaitingInspection);
  const active = tasks.filter((task) => ["STARTED", "IN_PROGRESS"].includes(task.status.toUpperCase()));
  const canAssign = hasPermission(currentUser, "TASK_ASSIGN");
  return <View style={styles.wrap}>
    <SectionTitle icon={Users} title="Team control" subtitle="What needs attention right now" />
    <Metrics items={[["Visible Tasks", tasks.length], ["Completed", tasks.filter((t) => t.status.toUpperCase() === "COMPLETED").length], ["Overdue", tasks.filter((t) => t.status.toUpperCase() === "OVERDUE").length], ["Needs Assignment", unassigned.length], ["Inspection Required", inspections.length]]} />
    <TaskQueue title="Needs Assignment" tasks={unassigned} empty="No unassigned tasks" actionLabel={canAssign ? "Assign" : undefined} onAction={onAssignTask} onOpenTask={onOpenTask} />
    <TaskQueue title="Inspection Required" tasks={inspections} empty="No inspections waiting" onOpenTask={onOpenTask} />
    <TaskQueue title="Active Team Work" tasks={active} empty="No active team work" onOpenTask={onOpenTask} />
  </View>;
}

function ManagerHome({ tasks, overview, onOpenTask }: Omit<Props, "mode" | "homeTask" | "currentUser" | "actionInProgress" | "onStartTask" | "onResumeTask" | "onAssignTask">) {
  const overdue = tasks.filter((t) => t.status.toUpperCase() === "OVERDUE");
  const urgent = tasks.filter((t) => ["HIGH", "URGENT", "CRITICAL", "P1", "P2"].some((p) => t.priority.toUpperCase().includes(p)) && !["COMPLETED", "CANCELLED"].includes(t.status.toUpperCase()));
  const inspections = tasks.filter((t) => t.awaitingInspection);
  const unassigned = tasks.filter((t) => !t.assignmentLabel && !["COMPLETED", "CANCELLED"].includes(t.status.toUpperCase()));
  return <View style={styles.wrap}>
    <SectionTitle icon={ClipboardCheck} title="Hotel overview" subtitle="Exceptions and decisions" />
    <Metrics items={[["Active Tasks", overview.taskCount], ["Urgent", overview.urgentCount], ["Completed", `${overview.completionPercent}%`], ["Overdue", overdue.length]]} />
    <TaskQueue title="Requires Attention" tasks={[...overdue, ...urgent, ...inspections, ...unassigned].filter((task, index, all) => all.findIndex((candidate) => candidate.id === task.id) === index)} empty="No exceptions require attention" onOpenTask={onOpenTask} />
    <View style={styles.assistantCard}><Text style={styles.queueTitle}>OpAI Manager Assistant</Text><Text style={styles.assistantHint}>Ask about the operational data currently available.</Text>{["Which tasks are overdue?", "Which rooms are waiting for inspection?", "Where is the largest backlog?"] .map((prompt) => <View key={prompt} style={styles.prompt}><Text style={styles.promptText}>{prompt}</Text></View>)}</View>
  </View>;
}

function SectionTitle({ icon: Icon, title, subtitle }: { icon: typeof Users; title: string; subtitle: string }) { return <View style={styles.sectionTitle}><Icon size={20} color={colors.green} /><View><Text style={styles.title}>{title}</Text><Text style={styles.subtitle}>{subtitle}</Text></View></View>; }
function Metrics({ items }: { items: Array<[string, string | number]> }) { return <View style={styles.metrics}>{items.map(([label, value]) => <View key={label} style={styles.metric}><Text style={styles.metricValue}>{value}</Text><Text style={styles.metricLabel}>{label}</Text></View>)}</View>; }
function TaskQueue({ title, tasks, empty, actionLabel, onAction, onOpenTask }: { title: string; tasks: TaskSummary[]; empty: string; actionLabel?: string; onAction?: (id: string) => void; onOpenTask?: (id: string) => void }) { return <View style={styles.queue}><Text style={styles.queueTitle}>{title}</Text>{tasks.length === 0 ? <Text style={styles.emptyBody}>{empty}</Text> : tasks.slice(0, 6).map((task) => <View key={task.id} style={styles.row}><Pressable style={styles.rowMain} onPress={() => onOpenTask?.(task.id)}><Text style={styles.room}>{task.roomOrLocation ?? "Location unavailable"}</Text><Text style={styles.task} numberOfLines={1}>{task.title}</Text><Text style={styles.meta}>{task.assignmentLabel ?? "Unassigned"} · {task.status}</Text></Pressable>{actionLabel ? <Pressable style={styles.action} onPress={() => onAction?.(task.id)}><Text style={styles.actionText}>{actionLabel}</Text></Pressable> : <AlertTriangle size={16} color={task.awaitingInspection ? colors.red : colors.textMuted} />}</View>)}</View>; }

const styles = StyleSheet.create({ wrap: { paddingHorizontal: 14, paddingTop: 12, gap: 12 }, greetingCard: { marginHorizontal: 14, marginTop: 8, padding: 14, flexDirection: "row", alignItems: "center", gap: 10, borderRadius: radius.lg, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.cardBorder }, greetingEmoji: { fontSize: 25 }, greetingTitle: { color: colors.text, fontSize: 16, fontWeight: "900" }, greetingBody: { color: colors.textMuted, fontSize: 12, marginTop: 2 }, progressCard: { marginHorizontal: 14, marginTop: 2, padding: 12, borderRadius: radius.lg, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.cardBorder }, progressHeader: { flexDirection: "row", justifyContent: "space-between" }, progressTitle: { color: colors.text, fontWeight: "900", fontSize: 14 }, progressValue: { color: colors.green, fontWeight: "900", fontSize: 18 }, progressTrack: { height: 8, marginTop: 8, borderRadius: radius.pill, backgroundColor: "#e6efe9", overflow: "hidden" }, progressFill: { height: "100%", borderRadius: radius.pill, backgroundColor: colors.green }, progressCaption: { marginTop: 5, color: colors.textMuted, fontSize: 11 }, other: { margin: 14, padding: 12, borderRadius: radius.lg, backgroundColor: colors.surface, flex: 1, minHeight: 150 }, otherHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" }, otherScroll: { maxHeight: 222 }, otherTitle: { color: colors.text, fontSize: 15, fontWeight: "900" }, otherCount: { color: colors.green, fontWeight: "900" }, otherRow: { flexDirection: "row", alignItems: "center", paddingVertical: 10, borderTopWidth: 1, borderTopColor: colors.divider }, otherMain: { flex: 1, minWidth: 0, marginLeft: 9 }, taskIcon: { width: 38, height: 38, alignItems: "center", justifyContent: "center", borderRadius: radius.pill, backgroundColor: "#e8f5ed" }, priorityBadge: { alignSelf: "flex-start", marginTop: 4, paddingHorizontal: 6, paddingVertical: 2, borderRadius: radius.pill, fontSize: 10, fontWeight: "900" }, sectionTitle: { flexDirection: "row", alignItems: "center", gap: 9 }, title: { color: colors.text, fontSize: typography.title, fontWeight: "900" }, subtitle: { color: colors.textMuted, fontSize: typography.caption, marginTop: 2 }, metrics: { flexDirection: "row", flexWrap: "wrap", gap: 7 }, metric: { flexBasis: "30%", flexGrow: 1, minWidth: 100, padding: 10, borderRadius: radius.lg, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.cardBorder, ...shadow.soft }, metricValue: { color: colors.text, fontSize: 20, fontWeight: "900" }, metricLabel: { color: colors.textMuted, fontSize: 10, fontWeight: "800" }, queue: { padding: 11, borderRadius: radius.lg, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.cardBorder, ...shadow.soft }, queueTitle: { color: colors.text, fontSize: 14, fontWeight: "900", marginBottom: 7 }, row: { flexDirection: "row", alignItems: "center", borderTopWidth: 1, borderTopColor: colors.divider, paddingVertical: 9 }, rowMain: { flex: 1, minWidth: 0 }, room: { color: colors.text, fontWeight: "900", fontSize: 13 }, task: { color: colors.text, fontSize: 12, marginTop: 2 }, meta: { color: colors.textMuted, fontSize: 10, marginTop: 2 }, action: { paddingHorizontal: 10, paddingVertical: 8, borderRadius: radius.pill, backgroundColor: colors.green }, actionText: { color: "#fff", fontSize: 11, fontWeight: "900" }, empty: { margin: 14, padding: 20, alignItems: "center", borderRadius: radius.lg, backgroundColor: colors.surface }, emptyTitle: { color: colors.text, fontSize: 18, fontWeight: "900" }, emptyBody: { color: colors.textMuted, fontSize: 13, marginTop: 4 }, assistantCard: { padding: 12, borderRadius: radius.lg, backgroundColor: "#f2f7ff", borderWidth: 1, borderColor: "#dbe8fb" }, assistantHint: { color: colors.textMuted, fontSize: 12, marginBottom: 7 }, prompt: { paddingVertical: 9, paddingHorizontal: 10, marginTop: 5, borderRadius: radius.pill, backgroundColor: colors.surface }, promptText: { color: colors.text, fontSize: 12, fontWeight: "700" }, completionScreen: { paddingBottom: 24 }, celebration: { alignItems: "center", paddingVertical: 34, backgroundColor: colors.green }, successCircle: { width: 72, height: 72, borderRadius: 36, alignItems: "center", justifyContent: "center", backgroundColor: "#2f9e62" }, greatTitle: { color: "#fff", fontSize: 28, fontWeight: "900", marginTop: 10 }, completedText: { color: "#e9fff0", fontSize: 14, fontWeight: "700", marginTop: 4 }, xpBlock: { alignItems: "center", paddingVertical: 16 }, xpValue: { color: colors.green, fontSize: 24, fontWeight: "900" }, xpLabel: { color: colors.textMuted, fontSize: 12, fontWeight: "800" }, completionNextTitle: { marginHorizontal: 14, marginTop: 6, color: colors.text, fontSize: 16, fontWeight: "900" }, completionTask: { marginHorizontal: 14, marginTop: 8, flexDirection: "row", alignItems: "center", padding: 12, borderRadius: radius.lg, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.cardBorder }, nextButton: { margin: 14, minHeight: 52, alignItems: "center", justifyContent: "center", borderRadius: radius.lg, backgroundColor: colors.green }, nextButtonText: { color: "#fff", fontSize: 13, fontWeight: "900" }, noNext: { marginTop: 8 }, noNextText: { marginHorizontal: 14, color: colors.textMuted, fontSize: 14 }
});
