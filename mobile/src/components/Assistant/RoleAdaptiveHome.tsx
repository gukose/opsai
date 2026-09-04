// StyleSheet is extended below with the supervisor-only dashboard tokens.
// @ts-nocheck
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View, useWindowDimensions } from "react-native";
import { AlertTriangle, BedDouble, Bell, ClipboardCheck, ChevronRight, Filter, Search, ShoppingBasket, Star, Users, Wrench, Check, CheckCircle2, Clock3, UserPlus, Activity, Gauge, UserRound, CalendarDays, Sparkles, MessageCircle, BarChart3, Utensils, HeartPulse } from "lucide-react-native";
import { useMemo, useState } from "react";

import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";
import { hasPermission } from "../../auth/currentUserHelpers";
import { getCurrentUserDisplayName } from "../../auth/currentUserHelpers";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { TaskDetail, TaskSummary } from "../../tasks/types";
import { isHomeActionable, TaskBoardOverview } from "../../tasks/taskBoardSelectors";
import { UserExperienceMode } from "../../auth/experienceMode";
import { NextTaskCard } from "./NextTaskCard";
import { taskCompletionXp } from "../../tasks/taskRewardPolicy";
import { getManagerDashboardData } from "../../dashboard/managerDashboardData";

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
  onOpenTasks?: () => void;
  onAssignTask?: (taskId: string) => void;
};

export function RoleAdaptiveHome({ mode, currentUser, tasks, homeTask, overview, actionInProgress, taskError, taskLoading, onStartTask, onResumeTask, onOpenTask, onOpenTasks, onAssignTask }: Props) {
  if (mode === "FRONTLINE_SIMPLE") {
    return <FrontlineHome currentUser={currentUser} task={homeTask} tasks={tasks} overview={overview} actionInProgress={actionInProgress} taskError={taskError} taskLoading={taskLoading} onStartTask={onStartTask} onResumeTask={onResumeTask} onOpenTask={onOpenTask} />;
  }
  if (mode === "SUPERVISOR") {
    return <SupervisorHome currentUser={currentUser} tasks={tasks} overview={overview} onOpenTask={onOpenTask} onOpenTasks={onOpenTasks} onAssignTask={onAssignTask} />;
  }
  return <ManagerDashboard tasks={tasks} currentUser={currentUser} onOpenTask={onOpenTask} />;
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
  const otherTasks = tasks.filter((item) => item.id !== task?.id && isHomeActionable(item));
  const completed = tasks.filter((item) => item.status.toUpperCase() === "COMPLETED").length;
  const total = tasks.length;
  const percent = total > 0 ? Math.round((completed / total) * 100) : 0;
  return <View>{<View style={styles.greetingCard}><Text style={styles.greetingEmoji}>👋</Text><View><Text style={styles.greetingTitle}>Hello {getCurrentUserDisplayName(currentUser).replace("Signed-in user", "there")}</Text><Text style={styles.greetingBody}>Have a great shift.</Text></View></View>}{total > 0 ? <View style={styles.progressCard}><View style={styles.progressHeader}><View><Text style={styles.progressTitle}>Daily Progress</Text><Text style={styles.progressValue}><Text style={styles.progressCompleted}>{completed}</Text><Text style={styles.progressTotal}> / {total}</Text></Text></View><Star size={20} color="#d99a23" fill="#f7c948" /></View><View style={styles.progressTrack}><View style={[styles.progressFill, { width: `${percent}%` }]} /></View><Text style={styles.progressCaption}>{percent}% completed</Text></View> : null}{task ? <NextTaskCard task={task} isActionInProgress={actionInProgress} onContinueTask={() => onOpenTask?.(task.id)} /> : taskError ? <View style={styles.empty}><Text style={styles.emptyTitle}>Unable to load tasks</Text><Text style={styles.emptyBody}>{taskError}</Text></View> : taskLoading ? null : <View style={styles.empty}><Text style={styles.emptyTitle}>No next task</Text><Text style={styles.emptyBody}>Your assigned work will appear here.</Text></View>}{otherTasks.length > 0 ? <View style={styles.other}><View style={styles.otherHeader}><Text style={styles.otherTitle}>Other Tasks</Text><Text style={styles.otherCount}>{otherTasks.length}</Text></View><ScrollView nestedScrollEnabled showsVerticalScrollIndicator={otherTasks.length > 3} style={styles.otherScroll}>{otherTasks.map((item) => <Pressable key={item.id} style={styles.otherRow} onPress={() => onOpenTask?.(item.id)}><TaskTypeIcon task={item} /><View style={styles.otherMain}><Text style={styles.room}>{formatLocation(item.roomOrLocation) ?? "Location unavailable"}</Text><Text style={styles.task}>{item.title}</Text><PriorityBadge value={item.priority} /></View><ChevronRight color={colors.textMuted} size={20} /></Pressable>)}</ScrollView></View> : null}</View>;
}

function formatLocation(value: string | null): string | null { if (!value) return null; if (/^room\s/i.test(value)) return value.toUpperCase(); return /^\d+$/.test(value.trim()) ? `ROOM ${value.trim()}` : value; }
function TaskTypeIcon({ task }: { task: TaskSummary }) { const normalized = `${task.intentType} ${task.title}`.toUpperCase(); const Icon = normalized.includes("MINIBAR") ? ShoppingBasket : normalized.includes("TECHNICAL") || normalized.includes("REPAIR") ? Wrench : BedDouble; return <View style={styles.taskIcon}><Icon size={20} color={colors.green} /></View>; }
function PriorityBadge({ value }: { value: string }) { const normalized = value.toUpperCase(); const color = normalized.includes("HIGH") || normalized.includes("URGENT") ? colors.red : normalized.includes("LOW") ? colors.green : "#b96b00"; return <Text style={[styles.priorityBadge, { color, backgroundColor: `${color}18` }]}>{value}</Text>; }

function SupervisorHome({ currentUser, tasks, overview, onOpenTask, onOpenTasks, onAssignTask }: Omit<Props, "mode" | "homeTask" | "actionInProgress" | "onStartTask" | "onResumeTask">) {
  const { width } = useWindowDimensions();
  const [category, setCategory] = useState("ALL");
  const [floor, setFloor] = useState("ALL");
  const [query, setQuery] = useState("");
  const canAssign = hasPermission(currentUser, "TASK_ASSIGN");
  const scoped = useMemo(() => tasks.filter((task) => {
    const text = `${task.title} ${task.roomOrLocation ?? ""} ${task.intentType}`.toLowerCase();
    const matchesQuery = !query.trim() || text.includes(query.trim().toLowerCase());
    const intent = task.intentType.toUpperCase();
    const matchesCategory = category === "ALL"
      || (category === "HOUSEKEEPING" && intent.includes("HOUSEKEEP"))
      || (category === "MAINTENANCE" && (intent.includes("TECHNICAL") || intent.includes("MAINTENANCE")))
      || (category === "GUEST_REQUEST" && intent.includes("GUEST"))
      || (category === "MINIBAR" && intent.includes("MINIBAR"));
    const room = task.roomOrLocation?.match(/(\d{3})/)?.[1];
    const taskFloor = room ? String(Math.floor(Number(room) / 100)) : null;
    return matchesQuery && matchesCategory && (floor === "ALL" || taskFloor === floor);
  }), [tasks, category, floor, query]);
  const unassigned = scoped.filter((task) => !task.assignmentLabel && !["COMPLETED", "CANCELLED"].includes(task.status.toUpperCase()));
  const inspections = scoped.filter((task) => task.awaitingInspection);
  const active = scoped.filter((task) => ["STARTED", "IN_PROGRESS", "PAUSED"].includes(task.status.toUpperCase()));
  const completed = scoped.filter((t) => t.status.toUpperCase() === "COMPLETED").length;
  const overdue = scoped.filter(isOverdue).length;
  const openFirst = (items: TaskSummary[]) => { if (items[0]) onOpenTask?.(items[0].id); };
  return <View style={styles.supervisorBoard}>
    <View style={[styles.greetingCard, styles.supervisorGreeting]}><Text style={styles.greetingEmoji}>👋</Text><View><Text style={styles.greetingTitle}>Hello {getCurrentUserDisplayName(currentUser).replace("Signed-in user", "there")}</Text><Text style={styles.greetingBody}>Have a great shift.</Text></View></View>
    <View style={[styles.kpiGrid, width >= 700 && { flexWrap: "nowrap" }]}>
      <Kpi icon={ClipboardCheck} label="Total Tasks" value={scoped.length} tone="#3267a8" />
      <Kpi icon={CheckCircle2} label="Completed" value={completed} tone={colors.green} />
      <Kpi icon={Clock3} label="Overdue" value={overdue} tone={colors.red} />
      <Kpi icon={UserPlus} label="Unassigned" value={unassigned.length} tone="#c87916" />
    </View>
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>{[["ALL", "All"], ["HOUSEKEEPING", "Housekeeping"], ["MAINTENANCE", "Technical Service"], ["GUEST_REQUEST", "Guest Request"], ["MINIBAR", "Minibar"]].map(([key, label]) => <Pressable key={key} onPress={() => setCategory(key)} style={[styles.chip, category === key && styles.chipActive]}><Text style={[styles.chipText, category === key && styles.chipTextActive]}>{label}</Text></Pressable>)}</ScrollView>
    <View style={styles.searchRow}><View style={styles.searchBox}><Search size={17} color={colors.textMuted} /><TextInput value={query} onChangeText={setQuery} placeholder="Search tasks..." placeholderTextColor={colors.textMuted} style={styles.searchInput} /></View><View style={styles.filterLabel}><Filter size={16} color={colors.textMuted} /><Text style={styles.filterText}>Filter</Text></View></View>
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chipRow}>{["ALL", "1", "2", "3", "4", "5"].map((value) => <Pressable key={value} onPress={() => setFloor(value)} style={[styles.floorChip, floor === value && styles.chipActive]}><Text style={[styles.chipText, floor === value && styles.chipTextActive]}>{value === "ALL" ? "All floors" : `Floor ${value}`}</Text></Pressable>)}</ScrollView>
    <SupervisorQueue title={`Needs Assignment${unassigned.length ? ` (${unassigned.length})` : ""}`} tasks={unassigned} empty="No unassigned tasks" actionLabel={canAssign ? "Assign" : undefined} onAction={onAssignTask} onOpenTask={onOpenTask} />
    <SupervisorQueue title={`Inspection Required${inspections.length ? ` (${inspections.length})` : ""}`} tasks={inspections} empty="No inspections waiting" onOpenTask={onOpenTask} onAction={onOpenTask} actionLabel="Inspect" />
    <ActiveTasksSection tasks={active} onOpenTask={onOpenTask} onOpenTasks={onOpenTasks} />
    <View style={styles.quickCard}><Text style={styles.queueTitle}>Quick Actions</Text><Pressable style={styles.quickAction} onPress={() => openFirst(unassigned)}><UserPlus size={17} color={colors.green} /><Text style={styles.quickText}>Assign Task</Text></Pressable><Pressable style={styles.quickAction} onPress={() => openFirst(inspections)}><ClipboardCheck size={17} color={colors.green} /><Text style={styles.quickText}>Inspection Required</Text><Text style={styles.quickCount}>{inspections.length}</Text></Pressable></View>
    {overdue > 0 ? <View style={styles.exceptionCard}><Text style={styles.queueTitle}>Exceptions</Text><Text style={styles.exceptionText}>{overdue} overdue task{overdue === 1 ? "" : "s"}</Text></View> : null}
  </View>;
}

function Kpi({ icon: Icon, label, value, tone }: { icon: typeof ClipboardCheck; label: string; value: number; tone: string }) { return <View style={styles.kpi}><Icon size={18} color={tone} /><Text style={styles.kpiLabel}>{label}</Text><Text style={[styles.kpiValue, { color: tone }]}>{value}</Text></View>; }
function isOverdue(task: TaskSummary): boolean {
  if (task.status.toUpperCase() === "OVERDUE") return true;
  if (["COMPLETED", "CANCELLED"].includes(task.status.toUpperCase()) || !task.slaDeadline) return false;
  const deadline = Date.parse(task.slaDeadline);
  return Number.isFinite(deadline) && deadline < Date.now();
}
function ActiveTasksSection({ tasks, onOpenTask, onOpenTasks }: { tasks: TaskSummary[]; onOpenTask?: (id: string) => void; onOpenTasks?: () => void }) {
  return <View style={styles.supervisorQueue}>
    <View style={styles.queueHeader}><Text style={styles.queueTitle}>Active Tasks ({tasks.length})</Text>{tasks.length > 3 ? <Pressable onPress={onOpenTasks}><Text style={styles.viewAll}>View All</Text></Pressable> : null}</View>
    {tasks.length === 0 ? <Text style={styles.emptyBody}>No active tasks right now.</Text> : tasks.slice(0, 3).map((task) => <ActiveTaskRow key={task.id} task={task} onPress={() => onOpenTask?.(task.id)} />)}
    {tasks.length > 3 ? <Pressable onPress={onOpenTasks} style={styles.moreTasks}><Text style={styles.meta}>+{tasks.length - 3} more tasks</Text></Pressable> : null}
  </View>;
}
function ActiveTaskRow({ task, onPress }: { task: TaskSummary; onPress: () => void }) {
  const employee = task.assignmentLabel?.trim() || "Assigned employee";
  const initials = employee.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toUpperCase() || "?";
  const room = task.roomOrLocation?.match(/(\d{3,4}[A-Za-z]?)/)?.[1];
  const location = room ? `ROOM ${room}` : (task.roomOrLocation ?? "Location unavailable");
  const floor = room ? `Floor ${Math.floor(Number(room) / 100)}` : null;
  const worked = Math.max(0, Math.floor(task.actualWorkingDurationSeconds ?? 0));
  const duration = worked > 0 ? formatMinutes(worked) : null;
  const target = Number(task.slaTargetSeconds);
  const progress = target > 0 && worked > 0 ? Math.min(1, worked / target) : null;
  const overdue = isOverdue(task);
  return <Pressable onPress={onPress} style={styles.activeTaskRow}>
    <View style={styles.activeIdentity}><View style={styles.initials}><Text style={styles.initialsText}>{initials}</Text></View><View style={{ flex: 1 }}><Text style={styles.activeEmployee} numberOfLines={1}>{employee}</Text><Text style={styles.meta}>{floor ? `Housekeeper · ${floor}` : "Assigned employee"}</Text></View></View>
    <View style={styles.activeTaskInfo}><Text style={styles.room}>{location}</Text><Text style={styles.task} numberOfLines={1}>{task.title}</Text>{progress !== null ? <View style={styles.activeProgressTrack}><View style={[styles.activeProgressFill, { width: `${progress * 100}%`, backgroundColor: overdue ? colors.red : colors.green }]} /></View> : null}</View>
    <View style={styles.activeDuration}>{duration ? <Text style={[styles.durationText, overdue && { color: colors.red }]}>{duration}</Text> : <Text style={styles.statusText}>{task.status}</Text>}<ChevronRight size={17} color={colors.textMuted} /></View>
  </Pressable>;
}
function formatMinutes(seconds: number): string { const minutes = Math.floor(seconds / 60); return `${minutes} min`; }
function SupervisorQueue({ title, tasks, empty, actionLabel, onAction, onOpenTask }: { title: string; tasks: TaskSummary[]; empty: string; actionLabel?: string; onAction?: (id: string) => void; onOpenTask?: (id: string) => void }) { return <View style={styles.supervisorQueue}><View style={styles.queueHeader}><Text style={styles.queueTitle}>{title}</Text>{tasks.length > 3 ? <Text style={styles.viewAll}>View All</Text> : null}</View>{tasks.length === 0 ? <Text style={styles.emptyBody}>{empty}</Text> : tasks.slice(0, 3).map((task) => <View key={task.id} style={styles.supervisorRow}><Pressable style={styles.rowMain} onPress={() => onOpenTask?.(task.id)}><View style={styles.rowIdentity}><TaskTypeIcon task={task} /><View style={{ flex: 1 }}><Text style={styles.room}>{formatLocation(task.roomOrLocation) ?? "Location unavailable"}</Text><Text style={styles.task}>{task.title}</Text><Text style={styles.meta}>{task.assignmentLabel ?? "Unassigned"} · {task.status}</Text></View></View></Pressable>{actionLabel ? <Pressable style={styles.action} onPress={() => onAction?.(task.id)}><Text style={styles.actionText}>{actionLabel}</Text></Pressable> : <ChevronRight size={18} color={colors.textMuted} />}</View>)}</View>; }

function ManagerDashboard({ tasks, currentUser, onOpenTask }: { tasks: TaskSummary[]; currentUser: CurrentUserSnapshot | null; onOpenTask?: (id: string) => void }) {
  const active = tasks.filter((task) => !["COMPLETED", "CANCELLED"].includes(task.status.toUpperCase()));
  const overdue = tasks.filter((task) => task.status.toUpperCase() === "OVERDUE");
  const urgent = tasks.filter((task) => ["HIGH", "URGENT", "CRITICAL", "P1", "P2"].some((p) => task.priority.toUpperCase().includes(p)) && !["COMPLETED", "CANCELLED"].includes(task.status.toUpperCase())).slice(0, 3);
  const data = getManagerDashboardData(tasks);
  return <View style={styles.managerDashboard}>
    <View style={styles.managerGreeting}><Text style={styles.greetingEmoji}>👋</Text><View><Text style={styles.greetingTitle}>Hello {getCurrentUserDisplayName(currentUser).replace("Signed-in user", "there")}</Text><Text style={styles.greetingBody}>Have a great shift.</Text></View></View>
    <View style={styles.periodRow}><View style={styles.periodPill}><CalendarDays size={15} color={colors.green} /><Text style={styles.periodText}>Today</Text></View><Text style={styles.periodCompare}>Daily operations</Text></View>
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.managerKpiRow}>{[[Activity, "Active Tasks", data.activeTasks.value, "#3267a8"], [Clock3, "Overdue Tasks", data.overdueTasks.value, colors.red], [Gauge, "Room Ready Rate", `${data.roomReadyRate.value}%`, colors.green], [BarChart3, "Avg Resolution", `${data.averageResolutionMinutes.value} min`, "#7658b8"], [UserRound, "At-Risk Guests", data.atRiskGuests.value, "#c87916"]].map(([Icon, label, value, tone]) => <View key={String(label)} style={styles.managerKpi}><View style={[styles.dashboardIcon, { backgroundColor: `${String(tone)}18` }]}><Icon size={17} color={String(tone)} /></View><Text style={styles.managerKpiLabel}>{String(label)}</Text><Text style={[styles.managerKpiValue, { color: String(tone) }]}>{String(value)}</Text></View>)}</ScrollView>
    <ManagerSection title="OpAI Daily Summary" icon={Sparkles}>{data.summary.map((item) => <Text key={item} style={styles.managerBody}>• {item}</Text>)}</ManagerSection>
    <ManagerSection title="Action Required" icon={AlertTriangle}>{(urgent.length ? urgent.map((task) => `${formatLocation(task.roomOrLocation) ?? "Task"} · ${task.title}`) : data.exceptions).map((item, index) => <Pressable key={item} style={styles.managerActionRow} onPress={() => urgent[index] && onOpenTask?.(urgent[index].id)}><Text style={styles.managerBody}>{item}</Text><Text style={styles.managerLink}>Review</Text></Pressable>)}</ManagerSection>
    <ManagerSection title="Department Health" icon={Users}>{data.departments.map((department) => <View key={department.name} style={styles.departmentRow}><DashboardIcon department={department.name} /><View style={styles.departmentCopy}><Text style={styles.departmentName}>{department.name}</Text><Text style={styles.departmentSub}>{departmentSubtitle(department.name)}</Text></View><Text style={styles.departmentValue}>{department.value}% <Text style={styles.departmentTrend}>{department.trend}</Text></Text></View>)}</ManagerSection>
    <ManagerSection title="Performance Trends · 7 Days" icon={BarChart3}><View style={styles.trendBars}>{data.departments.slice(0, 4).map((department) => <View key={department.name} style={styles.trendBar}><View style={[styles.trendFill, { height: `${department.value}%` }]} /><Text style={styles.trendLabel}>{department.name.split(" ")[0]}</Text></View>)}</View></ManagerSection>
    <ManagerSection title="Guest Experience" icon={MessageCircle}>{data.guestExperience.map((item) => <View key={item.label} style={styles.departmentRow}><DashboardIcon guest={item.label} /><Text style={[styles.departmentName, styles.departmentCopy]}>{item.label}</Text><Text style={styles.managerKpiValue}>{item.value}</Text></View>)}</ManagerSection>
    <View style={styles.managerAssistant}><View style={styles.managerSectionTitle}><Sparkles size={17} color="#7658b8" /><Text style={styles.queueTitle}>Ask OpAI</Text></View><Text style={styles.assistantHint}>Voice-first management insights</Text><Text style={styles.promptText}>Why are tasks delayed today?</Text><Text style={styles.promptText}>Which department has the highest SLA risk?</Text></View>
  </View>;
}
function ManagerSection({ title, icon: Icon, children }: { title: string; icon: typeof Users; children: React.ReactNode }) { return <View style={styles.managerSection}><View style={styles.managerSectionTitle}><Icon size={17} color={colors.green} /><Text style={styles.queueTitle}>{title}</Text></View>{children}</View>; }
function DashboardIcon({ department, guest }: { department?: string; guest?: string }) {
  const key = (department ?? guest ?? "").toUpperCase();
  const Icon = guest ? (key.includes("COMPLAINT") ? MessageCircle : key.includes("RECOVERY") ? HeartPulse : UserRound) : key.includes("TECHNICAL") ? Wrench : key.includes("FRONT") ? Bell : key.includes("F&B") || key.includes("F&B") ? Utensils : key.includes("MINIBAR") ? ShoppingBasket : BedDouble;
  const tone = guest ? "#7658b8" : key.includes("TECHNICAL") ? "#c87916" : key.includes("FRONT") ? "#168b87" : key.includes("MINIBAR") ? "#7658b8" : colors.green;
  return <View style={[styles.dashboardIcon, { backgroundColor: `${tone}18` }]}><Icon size={16} color={tone} /></View>;
}
function departmentSubtitle(name: string): string { const key = name.toUpperCase(); if (key.includes("TECHNICAL")) return "Repair & Maintenance"; if (key.includes("FRONT")) return "Guest Operations"; if (key.includes("F&B")) return "Food & Beverage"; if (key.includes("MINIBAR")) return "Minibar Operations"; return "Room Cleaning"; }

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

Object.assign(styles, { supervisorBoard: { padding: 14, gap: 12 }, kpiGrid: { flexDirection: "row", flexWrap: "wrap", gap: 8 }, kpi: { flexBasis: "47%", flexGrow: 1, minWidth: 130, padding: 11, borderRadius: radius.lg, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.cardBorder }, kpiLabel: { color: colors.textMuted, fontSize: 11, fontWeight: "800", marginTop: 5 }, kpiValue: { fontSize: 24, fontWeight: "900", marginTop: 2 }, chipRow: { gap: 7 }, chip: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.cardBorder, backgroundColor: colors.surface }, chipActive: { backgroundColor: "#e8f5ed", borderColor: colors.green }, chipText: { color: colors.textMuted, fontSize: 11, fontWeight: "800" }, chipTextActive: { color: colors.green }, searchRow: { flexDirection: "row", alignItems: "center", gap: 8 }, searchBox: { flex: 1, minHeight: 42, flexDirection: "row", alignItems: "center", gap: 7, paddingHorizontal: 11, borderWidth: 1, borderColor: colors.cardBorder, borderRadius: radius.md, backgroundColor: colors.surface }, searchInput: { flex: 1, color: colors.text, fontSize: 13 }, filterLabel: { minHeight: 42, paddingHorizontal: 10, flexDirection: "row", alignItems: "center", gap: 4, borderWidth: 1, borderColor: colors.cardBorder, borderRadius: radius.md }, filterText: { color: colors.textMuted, fontSize: 11, fontWeight: "800" }, floorChip: { paddingHorizontal: 11, paddingVertical: 7, borderRadius: radius.pill, backgroundColor: "#f6f8fa" }, supervisorQueue: { padding: 12, borderRadius: radius.lg, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.cardBorder }, queueHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" }, viewAll: { color: colors.green, fontSize: 11, fontWeight: "900" }, supervisorRow: { flexDirection: "row", alignItems: "center", paddingVertical: 10, borderTopWidth: 1, borderTopColor: colors.divider }, rowIdentity: { flexDirection: "row", alignItems: "center", flex: 1 }, quickCard: { padding: 12, borderRadius: radius.lg, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.cardBorder }, quickAction: { flexDirection: "row", alignItems: "center", gap: 8, paddingVertical: 10, borderTopWidth: 1, borderTopColor: colors.divider }, quickText: { color: colors.text, fontSize: 13, fontWeight: "800", flex: 1 }, quickCount: { color: colors.green, fontWeight: "900" }, exceptionCard: { padding: 12, borderRadius: radius.lg, backgroundColor: "#fff7f5", borderWidth: 1, borderColor: "#f0d5cf" }, exceptionText: { color: colors.red, fontSize: 13, fontWeight: "800" } });

Object.assign(styles, { moreTasks: { alignItems: "center", paddingTop: 10 }, activeTaskRow: { flexDirection: "row", alignItems: "center", gap: 9, paddingVertical: 11, borderTopWidth: 1, borderTopColor: colors.divider }, activeIdentity: { width: 108, flexDirection: "row", alignItems: "center", gap: 7 }, initials: { width: 30, height: 30, borderRadius: 15, alignItems: "center", justifyContent: "center", backgroundColor: "#e8f5ed" }, initialsText: { color: colors.green, fontSize: 10, fontWeight: "900" }, activeEmployee: { color: colors.text, fontSize: 11, fontWeight: "900" }, activeTaskInfo: { flex: 1, minWidth: 0 }, activeProgressTrack: { height: 5, marginTop: 6, borderRadius: radius.pill, backgroundColor: "#e6efe9", overflow: "hidden" }, activeProgressFill: { height: "100%", borderRadius: radius.pill }, activeDuration: { width: 50, alignItems: "flex-end", gap: 3 }, durationText: { color: colors.text, fontSize: 11, fontWeight: "900" }, statusText: { color: colors.textMuted, fontSize: 9, fontWeight: "800" } });

Object.assign(styles, { supervisorGreeting: { marginHorizontal: 0, marginTop: 0, width: "100%" } });
Object.assign(styles, { progressCompleted: { color: colors.green, fontSize: 24, fontWeight: "900" }, progressTotal: { color: colors.textMuted, fontSize: 16, fontWeight: "700" } });
Object.assign(styles, { managerDashboard: { padding: 14, gap: 12 }, managerGreeting: { padding: 14, flexDirection: "row", alignItems: "center", gap: 10, borderRadius: radius.lg, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.cardBorder }, periodRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" }, periodPill: { flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: 10, paddingVertical: 7, borderRadius: radius.pill, backgroundColor: "#e8f5ed" }, periodText: { color: colors.green, fontSize: 12, fontWeight: "900" }, periodCompare: { color: colors.textMuted, fontSize: 11 }, managerKpiRow: { gap: 8 }, managerKpi: { width: 132, padding: 11, borderRadius: radius.lg, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.cardBorder }, managerKpiLabel: { color: colors.textMuted, fontSize: 10, fontWeight: "800", marginTop: 6 }, managerKpiValue: { color: colors.text, fontSize: 22, fontWeight: "900", marginTop: 3 }, managerSection: { padding: 12, borderRadius: radius.lg, backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.cardBorder }, managerSectionTitle: { flexDirection: "row", alignItems: "center", gap: 7, marginBottom: 8 }, managerBody: { color: colors.textMuted, fontSize: 12, lineHeight: 18, marginTop: 4 }, managerActionRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingVertical: 7, borderTopWidth: 1, borderTopColor: colors.divider }, managerLink: { color: colors.green, fontSize: 11, fontWeight: "900" }, departmentRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingVertical: 8, borderTopWidth: 1, borderTopColor: colors.divider }, departmentName: { color: colors.text, fontSize: 12, fontWeight: "800" }, departmentValue: { color: colors.text, fontSize: 12, fontWeight: "900" }, departmentTrend: { color: colors.green, fontSize: 11 }, trendBars: { height: 90, flexDirection: "row", alignItems: "flex-end", justifyContent: "space-around", paddingTop: 8 }, trendBar: { height: "100%", alignItems: "center", justifyContent: "flex-end", gap: 4 }, trendFill: { width: 22, maxHeight: 75, borderRadius: 5, backgroundColor: colors.green }, trendLabel: { color: colors.textMuted, fontSize: 9 }, managerAssistant: { padding: 12, borderRadius: radius.lg, backgroundColor: "#f2f7ff", borderWidth: 1, borderColor: "#dbe8fb" } });
Object.assign(styles, { dashboardIcon: { width: 32, height: 32, borderRadius: 16, alignItems: "center", justifyContent: "center" }, departmentCopy: { flex: 1, marginLeft: 9 }, departmentSub: { color: colors.textMuted, fontSize: 10, marginTop: 2 } });
