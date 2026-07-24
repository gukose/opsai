import assert from "node:assert/strict";
import test from "node:test";

import { HttpDashboardApi } from "../api/dashboard/DashboardApi.ts";
import { MobileHotelOpAiClient } from "../api/hotelOpAiClient.ts";
import { taskReportingFromResponse } from "./types.ts";

test("maps task reporting response into reporting summary", () => {
  const report = taskReportingFromResponse({
    hotelId: "hotel-1",
    range: "today",
    generatedAt: "2026-07-14T12:00:00Z",
    window: {
      startInclusive: "2026-07-14T00:00:00Z",
      endExclusive: "2026-07-14T12:00:00Z",
      timeBasis: "UTC"
    },
    createdInRange: {
      total: 42,
      byType: [{ key: "MAINTENANCE", count: 12 }],
      byStatus: [{ key: "COMPLETED", count: 8 }],
      byPriority: [{ key: "URGENT", count: 3 }],
      sla: {
        completedOnTime: 10,
        completedLate: 2,
        openWithinSla: 20,
        openOverdue: 4,
        cancelled: 6,
        breached: 6
      }
    },
    currentSnapshot: {
      active: 18,
      byStatus: [{ key: "IN_PROGRESS", count: 5 }],
      byPriority: [{ key: "HIGH", count: 4 }],
      sla: {
        dueSoon: 3,
        overdue: 4
      }
    }
  });

  assert.equal(report.hotelId, "hotel-1");
  assert.equal(report.range, "today");
  assert.equal(report.window.timeBasis, "UTC");
  assert.equal(report.createdInRange.total, 42);
  assert.equal(report.createdInRange.byType[0].label, "Maintenance");
  assert.equal(report.createdInRange.byStatus[0].count, 8);
  assert.equal(report.createdInRange.byPriority[0].key, "URGENT");
  assert.equal(report.createdInRange.sla.completedOnTime, 10);
  assert.equal(report.createdInRange.sla.completedLate, 2);
  assert.equal(report.createdInRange.sla.openWithinSla, 20);
  assert.equal(report.createdInRange.sla.openOverdue, 4);
  assert.equal(report.createdInRange.sla.cancelled, 6);
  assert.equal(report.createdInRange.sla.breached, 6);
  assert.equal(report.currentSnapshot.active, 18);
  assert.equal(report.currentSnapshot.byStatus[0].label, "In Progress");
  assert.equal(report.currentSnapshot.sla.dueSoon, 3);
  assert.equal(report.currentSnapshot.sla.overdue, 4);
});

test("maps empty and unknown reporting buckets safely", () => {
  const report = taskReportingFromResponse({
    hotelId: "hotel-1",
    range: "shift",
    generatedAt: "2026-07-14T12:00:00Z",
    window: {
      startInclusive: "2026-07-14T04:00:00Z",
      endExclusive: "2026-07-14T12:00:00Z",
      timeBasis: "UTC"
    },
    createdInRange: {
      total: 0,
      byType: [{ key: "CUSTOM_TYPE", count: 1 }],
      byStatus: [],
      byPriority: [],
      sla: {
        completedOnTime: 0,
        completedLate: 0,
        openWithinSla: 0,
        openOverdue: 0,
        cancelled: 0,
        breached: 0
      }
    },
    currentSnapshot: {
      active: 0,
      byStatus: [],
      byPriority: [],
      sla: {
        dueSoon: 0,
        overdue: 0
      }
    }
  });

  assert.equal(report.createdInRange.byType[0].label, "Custom Type");
  assert.deepEqual(report.createdInRange.byStatus, []);
  assert.equal(report.currentSnapshot.active, 0);
});

test("dashboard API requests task reports with the selected range", async () => {
  const calls = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (input, init) => {
    calls.push({
      url: String(input),
      authorization: new Headers(init?.headers).get("Authorization")
    });
    return new Response(JSON.stringify({}), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  };

  const api = new HttpDashboardApi(
    new MobileHotelOpAiClient({
      baseUrl: "http://localhost:8080",
      accessTokenProvider: () => "token"
    })
  );

  try {
    await api.getTaskReport();
    await api.getTaskReport("shift");
    await api.getTaskReport("7d");

    assert.deepEqual(calls, [
      {
        url: "http://localhost:8080/api/v1/dashboard/reports/tasks?range=today",
        authorization: "Bearer token"
      },
      {
        url: "http://localhost:8080/api/v1/dashboard/reports/tasks?range=shift",
        authorization: "Bearer token"
      },
      {
        url: "http://localhost:8080/api/v1/dashboard/reports/tasks?range=7d",
        authorization: "Bearer token"
      }
    ]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
