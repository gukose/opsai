import { TaskSummary } from "./types";

export type HomeTaskActionKind = "start" | "resume" | "continue" | "none";

export type HomeTaskPresentation = {
  bannerLabel: string;
  statusLabel: string;
  actionLabel: string;
  actionKind: HomeTaskActionKind;
};

export type TaskBoardOverview = {
  taskCount: number;
  urgentCount: number;
  completionPercent: number;
};

const ACTIVE_STATUS_RANK: Record<string, number> = {
  STARTED: 0,
  IN_PROGRESS: 0,
  WAITING: 1,
  OVERDUE: 2,
  ASSIGNED: 3,
  CREATED: 3
};

const TERMINAL_STATUSES = new Set(["COMPLETED", "CANCELLED"]);

export function selectHomeTask(tasks: TaskSummary[]): TaskSummary | null {
  const candidates = tasks.filter((task) => !TERMINAL_STATUSES.has(normalizeStatus(task.status)));
  if (candidates.length === 0) {
    return null;
  }

  return [...candidates].sort(compareHomeTasks)[0] ?? null;
}

export function getHomeTaskPresentation(task: TaskSummary | null): HomeTaskPresentation | null {
  if (!task) {
    return null;
  }

  const status = normalizeStatus(task.status);
  if (status === "STARTED" || status === "IN_PROGRESS") {
    return {
      bannerLabel: "CURRENT TASK",
      statusLabel: status,
      actionLabel: "Continue",
      actionKind: "continue"
    };
  }

  if (status === "WAITING") {
    return {
      bannerLabel: "WAITING TASK",
      statusLabel: status,
      actionLabel: "Resume Task",
      actionKind: "resume"
    };
  }

  if (status === "ASSIGNED" || status === "CREATED" || status === "OVERDUE") {
    return {
      bannerLabel: status === "OVERDUE" ? "NEXT TASK" : "NEXT TASK",
      statusLabel: status,
      actionLabel: "Start Task",
      actionKind: "start"
    };
  }

  return {
    bannerLabel: "NEXT TASK",
    statusLabel: status,
    actionLabel: "Open Task",
    actionKind: "none"
  };
}

export function buildTaskBoardOverview(tasks: TaskSummary[]): TaskBoardOverview {
  const activeTasks = tasks.filter((task) => !TERMINAL_STATUSES.has(normalizeStatus(task.status)));
  const completedCount = tasks.filter((task) => normalizeStatus(task.status) === "COMPLETED").length;
  const actionableCount = activeTasks.length;
  const trackedTotal = actionableCount + completedCount;
  const taskCount = actionableCount;
  const urgentCount = activeTasks.filter((task) => isUrgent(task)).length;
  const completionPercent = trackedTotal === 0 ? 0 : Math.round((completedCount / trackedTotal) * 100);

  return {
    taskCount,
    urgentCount,
    completionPercent
  };
}

function compareHomeTasks(left: TaskSummary, right: TaskSummary): number {
  const statusDelta = getStatusRank(left.status) - getStatusRank(right.status);
  if (statusDelta !== 0) {
    return statusDelta;
  }

  const priorityDelta = getPriorityRank(left.priority) - getPriorityRank(right.priority);
  if (priorityDelta !== 0) {
    return priorityDelta;
  }

  const deadlineDelta = getDeadlineRank(left.slaDeadline) - getDeadlineRank(right.slaDeadline);
  if (deadlineDelta !== 0) {
    return deadlineDelta;
  }

  const updatedDelta = getUpdatedRank(left.updatedAt) - getUpdatedRank(right.updatedAt);
  if (updatedDelta !== 0) {
    return updatedDelta;
  }

  return left.id.localeCompare(right.id);
}

function getStatusRank(status: string): number {
  const normalized = normalizeStatus(status);
  return ACTIVE_STATUS_RANK[normalized] ?? 4;
}

function getPriorityRank(priority: string): number {
  const normalized = priority.toUpperCase();

  if (normalized.includes("URGENT") || normalized.includes("CRITICAL") || normalized.includes("P1")) {
    return 0;
  }

  if (normalized.includes("HIGH") || normalized.includes("P2")) {
    return 1;
  }

  if (normalized.includes("MEDIUM") || normalized.includes("P3")) {
    return 2;
  }

  if (normalized.includes("LOW") || normalized.includes("P4")) {
    return 3;
  }

  return 4;
}

function getDeadlineRank(value: string): number {
  const deadline = new Date(value).getTime();
  return Number.isFinite(deadline) ? deadline : Number.POSITIVE_INFINITY;
}

function getUpdatedRank(value: string): number {
  const updated = new Date(value).getTime();
  return Number.isFinite(updated) ? updated : Number.POSITIVE_INFINITY;
}

function isUrgent(task: TaskSummary): boolean {
  const status = normalizeStatus(task.status);
  return !TERMINAL_STATUSES.has(status) && (status === "OVERDUE" || getPriorityRank(task.priority) <= 1);
}

function normalizeStatus(value: string): string {
  return value.trim().toUpperCase();
}
