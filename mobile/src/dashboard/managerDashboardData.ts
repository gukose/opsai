import { TaskSummary } from "../tasks/types";

export type ManagerMetric = { value: string | number; source: "REAL" | "DEMO_FALLBACK" };
export type ManagerDashboardData = {
  activeTasks: ManagerMetric;
  overdueTasks: ManagerMetric;
  roomReadyRate: ManagerMetric;
  averageResolutionMinutes: ManagerMetric;
  atRiskGuests: ManagerMetric;
  summary: string[];
  exceptions: string[];
  departments: Array<{ name: string; value: number; trend: string }>;
  guestExperience: Array<{ label: string; value: number }>;
};

const managerDashboardFallback = {
  roomReadyRate: 92,
  averageResolutionMinutes: 28,
  atRiskGuests: 3,
  summary: ["Housekeeping performance is within the expected operating range.", "Room readiness and service queues are ready for review."],
  exceptions: ["Review the oldest overdue operational tasks."],
  departments: [{ name: "Housekeeping", value: 94, trend: "↑6%" }, { name: "Technical Service", value: 76, trend: "↓8%" }, { name: "Front Office", value: 91, trend: "↑3%" }, { name: "F&B", value: 89, trend: "↑2%" }, { name: "Minibar", value: 96, trend: "↑5%" }],
  guestExperience: [{ label: "At-Risk Guests", value: 3 }, { label: "Open Complaints", value: 2 }, { label: "Recovery In Progress", value: 1 }]
};

export function getManagerDashboardData(tasks: TaskSummary[]): ManagerDashboardData {
  const terminal = new Set(["COMPLETED", "CANCELLED"]);
  const active = tasks.filter((task) => !terminal.has(task.status.toUpperCase()));
  const overdue = tasks.filter((task) => task.status.toUpperCase() === "OVERDUE").length;
  return {
    activeTasks: { value: active.length, source: "REAL" },
    overdueTasks: { value: overdue, source: "REAL" },
    roomReadyRate: { value: managerDashboardFallback.roomReadyRate, source: "DEMO_FALLBACK" },
    averageResolutionMinutes: { value: managerDashboardFallback.averageResolutionMinutes, source: "DEMO_FALLBACK" },
    atRiskGuests: { value: managerDashboardFallback.atRiskGuests, source: "DEMO_FALLBACK" },
    summary: managerDashboardFallback.summary,
    exceptions: overdue > 0 ? [`${overdue} task${overdue === 1 ? "" : "s"} currently exceed the available SLA.`] : managerDashboardFallback.exceptions,
    departments: managerDashboardFallback.departments,
    guestExperience: managerDashboardFallback.guestExperience
  };
}
