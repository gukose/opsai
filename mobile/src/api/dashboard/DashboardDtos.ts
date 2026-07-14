export type DashboardTaskStatusCountsDto = {
  created: number;
  assigned: number;
  started: number;
  inProgress: number;
  waiting: number;
  overdue: number;
  completed: number;
  cancelled: number;
};

export type DashboardSummaryDto = {
  hotelId: string;
  range: string;
  generatedAt: string;
  tasks: {
    createdInRange: {
      total: number;
      statusCounts: DashboardTaskStatusCountsDto;
    };
    currentSnapshot: {
      active: number;
      urgent: number;
      unassigned: number;
      completionPercent: number;
      statusCounts: DashboardTaskStatusCountsDto;
    };
  };
  sla: {
    dueSoon: number;
    overdue: number;
    breached: number;
  };
  notifications: {
    unread: number;
    recent: Array<{
      id: string;
      type: string;
      title: string;
      body: string;
      createdAt: string;
      sourceTaskId: string | null;
    }>;
  };
  workload: {
    assignedToUser: number;
    assignedToRole: number;
    unassigned: number;
  };
};
