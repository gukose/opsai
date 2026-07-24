import assert from "node:assert/strict";
import test from "node:test";

import { buildTaskListQuery } from "../api/task/TaskApi.ts";
import { emptyTaskFilters, shouldClearVisibleTasksBeforeLoad, taskSummariesFromListResponse } from "./types.ts";

const baseTask = {
  id: "task-1",
  hotelId: "hotel-1",
  intentType: "MAINTENANCE",
  source: "MANUAL",
  title: "Fix AC",
  description: "Room 401 AC is warm",
  priority: "HIGH",
  status: "CREATED",
  slaDeadline: "2026-07-14T12:00:00Z",
  assignment: null,
  createdAt: "2026-07-14T09:00:00Z",
  updatedAt: "2026-07-14T10:00:00Z",
  startedAt: null,
  completedAt: null,
  cancelledAt: null,
  overdueAt: null
};

test("empty filter state produces existing no-query task request", () => {
  assert.deepEqual(buildTaskListQuery(emptyTaskFilters()), {});
});

test("task filters map to API query parameters", () => {
  const query = buildTaskListQuery({
    q: " ac ",
    status: ["CREATED", "STARTED"],
    priority: ["HIGH", "URGENT"],
    assignment: "mine",
    createdFrom: "2026-07-14T00:00:00Z",
    createdTo: "2026-07-15T00:00:00Z"
  });

  assert.deepEqual(query, {
    q: "ac",
    status: "CREATED,STARTED",
    priority: "HIGH,URGENT",
    assignment: "mine",
    createdFrom: "2026-07-14T00:00:00Z",
    createdTo: "2026-07-15T00:00:00Z"
  });
});

test("filtered backend paged response maps into task summaries", () => {
  const summaries = taskSummariesFromListResponse({
    items: [baseTask],
    page: 0,
    size: 20,
    totalItems: 1,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false
  });

  assert.equal(summaries.length, 1);
  assert.equal(summaries[0]?.title, "Fix AC");
});

test("existing unfiltered array response still maps into task summaries", () => {
  const summaries = taskSummariesFromListResponse([baseTask]);

  assert.equal(summaries.length, 1);
  assert.equal(summaries[0]?.priority, "HIGH");
});

test("empty filtered result is handled", () => {
  const summaries = taskSummariesFromListResponse({
    items: [],
    page: 0,
    size: 20,
    totalItems: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false
  });

  assert.deepEqual(summaries, []);
});

test("filtered request failure preserves visible task list policy and clear restores unfiltered", () => {
  assert.equal(shouldClearVisibleTasksBeforeLoad(emptyTaskFilters()), true);
  assert.equal(shouldClearVisibleTasksBeforeLoad({ ...emptyTaskFilters(), q: "ac" }), false);
  assert.deepEqual(buildTaskListQuery({ ...emptyTaskFilters(), q: "" }), {});
});
