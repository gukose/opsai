import assert from "node:assert/strict";
import test from "node:test";

import { HttpTaskApi } from "../api/task/TaskApi.ts";
import { taskAttachmentFromResponse } from "./types.ts";

test("task attachment API requests selected task attachment endpoint", async () => {
  const calls = [];
  const api = new HttpTaskApi({
    async get(path) {
      calls.push(path);
      return [];
    },
    async post() {
      throw new Error("unexpected post");
    },
    async put() {
      throw new Error("unexpected put");
    },
    async patch() {
      throw new Error("unexpected patch");
    },
    async delete() {
      throw new Error("unexpected delete");
    }
  });

  const response = await api.getTaskAttachments("task-1");

  assert.deepEqual(response, []);
  assert.deepEqual(calls, ["/api/v1/tasks/task-1/attachments"]);
});

test("task attachment response maps metadata and provenance without unsafe media fields", () => {
  const mapped = taskAttachmentFromResponse({
    attachmentId: "attachment-1",
    conversationId: "conversation-1",
    type: "IMAGE",
    originalFileName: "sink.jpg",
    declaredMimeType: "image/jpeg",
    declaredSizeBytes: 100,
    widthPx: 80,
    heightPx: 60,
    storageStatus: "REGISTERED",
    sourceType: "VISION_ANALYSIS",
    analysisId: "analysis-1",
    analysisImportId: "import-1",
    createdAt: "2026-07-15T10:00:00Z"
  });

  assert.equal(mapped.originalFileName, "sink.jpg");
  assert.equal(mapped.storageStatus, "REGISTERED");
  assert.equal(mapped.sourceType, "VISION_ANALYSIS");
  assert.equal(mapped.analysisId, "analysis-1");
  assert.equal(mapped.analysisImportId, "import-1");
  assert.equal(JSON.stringify(mapped).includes("storageReference"), false);
  assert.equal(JSON.stringify(mapped).includes("downloadUrl"), false);
  assert.equal(JSON.stringify(mapped).includes("base64"), false);
});

test("assistant message provenance maps without vision fields", () => {
  const mapped = taskAttachmentFromResponse({
    attachmentId: "attachment-1",
    conversationId: "conversation-1",
    type: "IMAGE",
    originalFileName: "sink.jpg",
    declaredMimeType: "image/jpeg",
    declaredSizeBytes: 100,
    widthPx: null,
    heightPx: null,
    storageStatus: "REGISTERED",
    sourceType: "ASSISTANT_MESSAGE",
    analysisId: null,
    analysisImportId: null,
    createdAt: "2026-07-15T10:00:00Z"
  });

  assert.equal(mapped.sourceType, "ASSISTANT_MESSAGE");
  assert.equal(mapped.analysisId, null);
  assert.equal(mapped.analysisImportId, null);
});
