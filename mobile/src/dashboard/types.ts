import type { DashboardSummaryDto } from "../api/dashboard/DashboardDtos";
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
      sourceTaskId: notification.sourceTaskId
    }))
  };
}
