import assert from "node:assert/strict";
import test from "node:test";

import {
  buildAttachmentRegistrationRequest,
  splitAssistantMessageAttachments
} from "./assistantAttachmentRequestMapper.ts";

test("registration request contains declared metadata only", () => {
  const request = buildAttachmentRegistrationRequest(registeredLocalAttachment());

  assert.deepEqual(request, {
    type: "IMAGE",
    originalFileName: "sink.jpg",
    mimeType: "image/jpeg",
    sizeBytes: 100,
    widthPx: 80,
    heightPx: 60
  });
  assert.equal(JSON.stringify(request).includes("localUri"), false);
  assert.equal(JSON.stringify(request).includes("localReference"), false);
  assert.equal(JSON.stringify(request).includes("storageReference"), false);
  assert.equal(JSON.stringify(request).includes("storageStatus"), false);
  assert.equal(JSON.stringify(request).includes("base64"), false);
  assert.equal(JSON.stringify(request).includes("hotelId"), false);
  assert.equal(JSON.stringify(request).includes("userId"), false);
});

test("registered attachments are sent by server attachmentIds only", () => {
  const mapped = splitAssistantMessageAttachments([registeredLocalAttachment()]);

  assert.deepEqual(mapped.registeredAttachmentIds, ["server-1"]);
  assert.deepEqual(mapped.localMetadataAttachments, []);
  assert.equal(JSON.stringify(mapped).includes("file://local/sink.jpg"), false);
  assert.equal(JSON.stringify(mapped).includes("localReference"), false);
  assert.equal(JSON.stringify(mapped).includes("storageReference"), false);
  assert.equal(JSON.stringify(mapped).includes("base64"), false);
});

test("multiple registered attachment IDs preserve order", () => {
  const first = registeredLocalAttachment("server-1", "local-1");
  const second = registeredLocalAttachment("server-2", "local-2");
  const mapped = splitAssistantMessageAttachments([first, second]);

  assert.deepEqual(mapped.registeredAttachmentIds, ["server-1", "server-2"]);
});

test("legacy LOCAL_METADATA_ONLY attachments remain functional", () => {
  const mapped = splitAssistantMessageAttachments([{
    id: "local-1",
    localId: "local-1",
    type: "IMAGE",
    originalFileName: "sink.jpg",
    mimeType: "image/jpeg",
    sizeBytes: 100,
    widthPx: 80,
    heightPx: 60,
    localReference: "local://sink.jpg",
    storageStatus: "LOCAL_METADATA_ONLY",
    state: "LOCAL_SELECTED"
  }]);

  assert.deepEqual(mapped.registeredAttachmentIds, []);
  assert.equal(mapped.localMetadataAttachments[0].storageStatus, "LOCAL_METADATA_ONLY");
});

function registeredLocalAttachment(serverId = "server-1", localId = "local-1") {
  return {
    id: serverId,
    localId,
    serverAttachmentId: serverId,
    type: "IMAGE",
    originalFileName: "sink.jpg",
    mimeType: "image/jpeg",
    sizeBytes: 100,
    widthPx: 80,
    heightPx: 60,
    localUri: "file://local/sink.jpg",
    storageStatus: "REGISTERED",
    storageReference: null,
    createdAt: "2026-07-15T10:00:00Z",
    state: "REGISTERED"
  };
}
