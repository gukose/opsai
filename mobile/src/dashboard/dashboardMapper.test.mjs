import assert from "node:assert/strict";
import test from "node:test";

import { dashboardSummaryFromResponse } from "./types.ts";

test("maps dashboard summary into overview widget fields", () => {
  const summary = dashboardSummaryFromResponse({
    hotelId: "hotel-1",
    range: "TODAY",
    generatedAt: "2026-07-14T12:00:00Z",
    tasks: {
      createdInRange: {
        total: 8,
        statusCounts: statusCounts()
      },
      currentSnapshot: {
        active: 5,
        urgent: 2,
        unassigned: 1,
        completionPercent: 38,
        statusCounts: statusCounts()
      }
    },
    sla: {
      dueSoon: 3,
      overdue: 1,
      breached: 2
    },
    notifications: {
      unread: 4,
      recent: [
        {
          id: "notification-1",
          type: "TASK_CREATED",
          title: "Task created",
          body: "AC not working was created.",
          createdAt: "2026-07-14T11:59:00Z",
          sourceTaskId: "task-1"
        }
      ]
    },
    workload: {
      assignedToUser: 2,
      assignedToRole: 3,
      unassigned: 1
    }
  });

  assert.equal(summary.overview.taskCount, 5);
  assert.equal(summary.overview.urgentCount, 2);
  assert.equal(summary.overview.completionPercent, 38);
  assert.equal(summary.overview.unreadNotificationCount, 4);
  assert.equal(summary.overview.dueSoonCount, 3);
  assert.equal(summary.recentNotifications[0].id, "notification-1");
});

function statusCounts() {
  return {
    created: 1,
    assigned: 1,
    started: 1,
    inProgress: 1,
    waiting: 0,
    overdue: 1,
    completed: 3,
    cancelled: 0
  };
}
