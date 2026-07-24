import assert from "node:assert/strict";
import { registerHooks } from "node:module";
import test from "node:test";

registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier === "react-native") {
      return {
        url: "data:text/javascript,export const Platform = { OS: 'web' };",
        shortCircuit: true
      };
    }
    try {
      return nextResolve(specifier, context);
    } catch (error) {
      if (specifier.startsWith(".") && !specifier.match(/\.[cm]?[jt]sx?$/)) {
        return nextResolve(`${specifier}.ts`, context);
      }
      throw error;
    }
  }
});

const { DashboardService } = await import("./DashboardService.ts");

test("dashboard summary refreshes expired access token and retries once", async () => {
  const originalFetch = globalThis.fetch;
  const authorizations = [];
  globalThis.fetch = async (_url, init) => {
    authorizations.push(new Headers(init?.headers).get("Authorization"));
    if (authorizations.length === 1) {
      return new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), { status: 401 });
    }
    return new Response(JSON.stringify(summaryResponse()), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  };

  try {
    let refreshCalls = 0;
    const service = new DashboardService(
      () => "expired-dashboard-token",
      async () => {
        refreshCalls += 1;
        return "fresh-dashboard-token";
      }
    );

    const summary = await service.getSummary();

    assert.equal(summary.hotelId, "hotel-1");
    assert.equal(refreshCalls, 1);
    assert.deepEqual(authorizations, ["Bearer expired-dashboard-token", "Bearer fresh-dashboard-token"]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

function summaryResponse() {
  return {
    hotelId: "hotel-1",
    range: "TODAY",
    generatedAt: "2026-07-15T12:00:00Z",
    tasks: {
      createdInRange: {
        total: 1,
        statusCounts: statusCounts()
      },
      currentSnapshot: {
        active: 1,
        urgent: 0,
        unassigned: 0,
        completionPercent: 0,
        statusCounts: statusCounts()
      }
    },
    sla: {
      dueSoon: 0,
      overdue: 0,
      breached: 0
    },
    notifications: {
      unread: 0,
      recent: []
    },
    workload: {
      assignedToUser: 1,
      assignedToRole: 0,
      unassigned: 0
    }
  };
}

function statusCounts() {
  return {
    created: 1,
    assigned: 0,
    started: 0,
    inProgress: 0,
    waiting: 0,
    overdue: 0,
    completed: 0,
    cancelled: 0
  };
}
