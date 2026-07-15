import assert from "node:assert/strict";
import test from "node:test";

import { createLocalAttachmentMetadata } from "./attachmentMetadata.ts";

test("camera gallery and web selections create LOCAL_SELECTED image metadata", () => {
  const camera = createSelection("camera", "image/jpeg");
  const gallery = createSelection("gallery", "image/png");
  const web = createSelection("web", "image/webp");

  assert.equal(camera.state, "LOCAL_SELECTED");
  assert.equal(gallery.state, "LOCAL_SELECTED");
  assert.equal(web.state, "LOCAL_SELECTED");
  assert.equal(camera.localUri, "file://camera/image");
});

test("supported image MIME types are accepted", () => {
  assert.equal(createSelection("jpeg", "image/jpeg").type, "IMAGE");
  assert.equal(createSelection("png", "image/png").type, "IMAGE");
  assert.equal(createSelection("webp", "image/webp").type, "IMAGE");
});

test("invalid image selection metadata is rejected before registration", () => {
  const invalid = [
    { id: "a", originalFileName: "a.gif", mimeType: "image/gif", sizeBytes: 1, widthPx: 1, heightPx: 1 },
    { id: "a", originalFileName: "a.jpg", mimeType: "image/jpeg", sizeBytes: 0, widthPx: 1, heightPx: 1 },
    { id: "a", originalFileName: "a.jpg", mimeType: "image/jpeg", sizeBytes: 10_000_001, widthPx: 1, heightPx: 1 },
    { id: "a", originalFileName: "a.jpg", mimeType: "image/jpeg", sizeBytes: 1, widthPx: 0, heightPx: 1 },
    { id: "a", originalFileName: "a.jpg", mimeType: "image/jpeg", sizeBytes: 1, widthPx: 1, heightPx: 10_001 }
  ];

  for (const candidate of invalid) {
    assert.throws(() => createLocalAttachmentMetadata(candidate));
  }
});

test("missing filename and duplicate local IDs are handled deterministically", () => {
  assert.throws(() => createLocalAttachmentMetadata({
    id: "a",
    originalFileName: "",
    mimeType: "image/jpeg",
    sizeBytes: 1,
    widthPx: 1,
    heightPx: 1
  }));

  const first = createSelection("dup", "image/jpeg");
  assert.throws(() => createSelection("dup", "image/jpeg", [first]));
});

test("selection preserves order and does not imply backend work", () => {
  const first = createSelection("first", "image/jpeg");
  const second = createSelection("second", "image/png", [first]);

  assert.deepEqual([first, second].map((attachment) => attachment.originalFileName), ["first.jpg", "second.png"]);
  assert.equal(first.storageStatus, "LOCAL_METADATA_ONLY");
  assert.equal(second.storageStatus, "LOCAL_METADATA_ONLY");
});

function createSelection(id, mimeType, existing = []) {
  const extension = mimeType === "image/png" ? "png" : mimeType === "image/webp" ? "webp" : "jpg";
  return createLocalAttachmentMetadata({
    id,
    originalFileName: `${id}.${extension}`,
    mimeType,
    sizeBytes: 100,
    widthPx: 80,
    heightPx: 60,
    localUri: `file://${id}/image`
  }, existing);
}
