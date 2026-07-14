import assert from "node:assert/strict";
import test from "node:test";

import { normalizeDraft } from "./assistantDraftNormalizer.ts";

test("assistant draft normalizes restored sending state to safe manual retry state", () => {
  const draft = normalizeDraft({
    schemaVersion: 1,
    conversationId: "conversation-1",
    text: "Room 502 sink is leaking",
    attachments: [
      {
        id: "att-1",
        type: "IMAGE",
        originalFileName: "sink.png",
        mimeType: "image/png",
        sizeBytes: 1234,
        localReference: "local://sink.png",
        storageStatus: "LOCAL_METADATA_ONLY",
        state: "sending"
      }
    ],
    voiceTranscript: {
      transcript: "Room 502 sink is leaking",
      languageCode: "en",
      durationMs: 4200,
      source: "CLIENT_TRANSCRIPT",
      state: "sending"
    },
    imageObservations: [
      {
        id: "obs-1",
        attachmentId: "att-1",
        text: "Water visible under the sink",
        source: "USER_PROVIDED",
        state: "sending"
      }
    ]
  });

  assert.equal(draft.text, "Room 502 sink is leaking");
  assert.equal(draft.attachments[0].state, "selected");
  assert.equal(draft.attachments[0].storageStatus, "LOCAL_METADATA_ONLY");
  assert.equal(draft.attachments[0].localReference, "local://sink.png");
  assert.equal(draft.voiceTranscript.source, "CLIENT_TRANSCRIPT");
  assert.equal(draft.voiceTranscript.state, "selected");
  assert.equal(draft.imageObservations[0].source, "USER_PROVIDED");
  assert.equal(draft.imageObservations[0].state, "selected");
});

test("assistant draft preserves ordering and failed state remains manually retryable", () => {
  const draft = normalizeDraft({
    schemaVersion: 1,
    conversationId: "conversation-1",
    text: "",
    attachments: [
      {
        id: "att-1",
        type: "IMAGE",
        originalFileName: "a.png",
        mimeType: "image/png",
        sizeBytes: 1,
        storageStatus: "LOCAL_METADATA_ONLY",
        state: "failed"
      },
      {
        id: "att-2",
        type: "IMAGE",
        originalFileName: "b.png",
        mimeType: "image/png",
        sizeBytes: 1,
        storageStatus: "LOCAL_METADATA_ONLY",
        state: "selected"
      }
    ],
    voiceTranscript: null,
    imageObservations: [
      { id: "obs-1", attachmentId: "att-1", text: "one", source: "USER_PROVIDED", state: "failed" },
      { id: "obs-2", attachmentId: "att-2", text: "two", source: "USER_PROVIDED", state: "selected" }
    ]
  });

  assert.deepEqual(draft.attachments.map((attachment) => attachment.id), ["att-1", "att-2"]);
  assert.deepEqual(draft.imageObservations.map((observation) => observation.id), ["obs-1", "obs-2"]);
  assert.equal(draft.attachments[0].state, "failed");
  assert.equal(draft.imageObservations[0].state, "failed");
});
