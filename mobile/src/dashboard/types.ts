import type { DashboardReportBucketDto, DashboardSummaryDto, TaskReportingDto } from "../api/dashboard/DashboardDtos";
import type { TaskBoardOverview } from "../tasks/taskBoardSelectors";

export type DashboardRecentNotification = {
  id: string;
  type: string;
  title: string;
  body: string;
  createdAt: string;
  sourceTaskId: string | null;
};

export type DashboardOverview = TaskBoardOverview & {
  unreadNotificationCount: number;
  dueSoonCount: number;
};

export type DashboardSummary = {
  hotelId: string;
  range: string;
  generatedAt: string;
  overview: DashboardOverview;
  recentNotifications: DashboardRecentNotification[];
};

export type ReportBucket = {
  key: string;
  label: string;
  count: number;
};

export type TaskReportingSummary = {
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
    byType: ReportBucket[];
    byStatus: ReportBucket[];
    byPriority: ReportBucket[];
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
    byStatus: ReportBucket[];
    byPriority: ReportBucket[];
    sla: {
      dueSoon: number;
      overdue: number;
    };
  };
};

export function dashboardSummaryFromResponse(response: DashboardSummaryDto): DashboardSummary {
  return {
    hotelId: response.hotelId,
    range: response.range,
    generatedAt: response.generatedAt,
    overview: {
      taskCount: response.tasks.currentSnapshot.active,
      urgentCount: response.tasks.currentSnapshot.urgent,
      completionPercent: response.tasks.currentSnapshot.completionPercent,
      unreadNotificationCount: response.notifications.unread,
      dueSoonCount: response.sla.dueSoon
    },
    recentNotifications: response.notifications.recent.map((notification) => ({
      id: notification.id,
      type: notification.type,
      title: notification.title,
      body: notification.body,
      createdAt: notification.createdAt,
      sourceTaskId: notification.sourceTaskId ?? null
    }))
  };
}

export function taskReportingFromResponse(response: TaskReportingDto): TaskReportingSummary {
  return {
    hotelId: response.hotelId,
    range: response.range,
    generatedAt: response.generatedAt,
    window: {
      startInclusive: response.window.startInclusive,
      endExclusive: response.window.endExclusive,
      timeBasis: response.window.timeBasis
    },
    createdInRange: {
      total: response.createdInRange.total,
      byType: mapBuckets(response.createdInRange.byType),
      byStatus: mapBuckets(response.createdInRange.byStatus),
      byPriority: mapBuckets(response.createdInRange.byPriority),
      sla: { ...response.createdInRange.sla }
    },
    currentSnapshot: {
      active: response.currentSnapshot.active,
      byStatus: mapBuckets(response.currentSnapshot.byStatus),
      byPriority: mapBuckets(response.currentSnapshot.byPriority),
      sla: { ...response.currentSnapshot.sla }
    }
  };
}

function mapBuckets(buckets: DashboardReportBucketDto[] | undefined): ReportBucket[] {
  return (buckets ?? []).map((bucket) => ({
    key: bucket.key,
    label: humanize(bucket.key),
    count: bucket.count
  }));
}

function humanize(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}
