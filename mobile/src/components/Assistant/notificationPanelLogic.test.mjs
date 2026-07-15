import assert from "node:assert/strict";
import test from "node:test";

import {
  formatUnreadBadge,
  shouldShowUnreadBadge,
  visibleRecentNotifications
} from "./notificationPanelLogic.ts";

test("notification badge renders only for positive unread count", () => {
  assert.equal(shouldShowUnreadBadge(4), true);
  assert.equal(shouldShowUnreadBadge(0), false);
  assert.equal(shouldShowUnreadBadge(undefined), false);
});

test("notification badge formats compact count", () => {
  assert.equal(formatUnreadBadge(4), "4");
  assert.equal(formatUnreadBadge(101), "99+");
  assert.equal(formatUnreadBadge(0), "");
});

test("notification panel filters inaccessible malformed recent items from rendering", () => {
  const visible = visibleRecentNotifications([
    notification("visible", "Task created"),
    notification("", "Missing id"),
    notification("missing-title", "")
  ]);

  assert.deepEqual(visible.map((item) => item.id), ["visible"]);
});

function notification(id, title) {
  return {
    id,
    type: "TASK_CREATED",
    title,
    body: `${title} body`,
    createdAt: "2026-07-14T12:00:00Z",
    sourceTaskId: null
  };
}
