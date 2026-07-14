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

export type DashboardReportBucketDto = {
  key: string;
  count: number;
};

export type TaskReportingDto = {
  hotelId: string;
  range: string;
  generatedAt: string;
  window: {
    startInclusive: string;
    endExclusive: string;
    timeBasis: string;
  };
  createdInRange: {
    total: number;
    byType: DashboardReportBucketDto[];
    byStatus: DashboardReportBucketDto[];
    byPriority: DashboardReportBucketDto[];
    sla: {
      completedOnTime: number;
      completedLate: number;
      openWithinSla: number;
      openOverdue: number;
      cancelled: number;
      breached: number;
    };
  };
  currentSnapshot: {
    active: number;
    byStatus: DashboardReportBucketDto[];
    byPriority: DashboardReportBucketDto[];
    sla: {
      dueSoon: number;
      overdue: number;
    };
  };
};
