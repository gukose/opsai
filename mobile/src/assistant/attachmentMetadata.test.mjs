import assert from "node:assert/strict";
import test from "node:test";

import {
  createLocalAttachmentMetadata,
  formatAttachmentSize,
  sampleLocalImageAttachment
} from "./attachmentMetadata.ts";

test("supported attachment MIME types create local metadata", () => {
  const cases = [
    ["image/jpeg", "IMAGE", "photo.jpg"],
    ["image/png", "IMAGE", "photo.png"],
    ["image/webp", "IMAGE", "photo.webp"],
    ["application/pdf", "PDF", "report.pdf"],
    ["text/plain", "DOCUMENT", "notes.txt"]
  ];

  for (const [mimeType, type, filename] of cases) {
    const attachment = createLocalAttachmentMetadata({
      id: `att-${type}`,
      originalFileName: filename,
      mimeType,
      sizeBytes: 100,
      widthPx: type === "IMAGE" ? 100 : undefined,
      heightPx: type === "IMAGE" ? 100 : undefined,
      localReference: `local://${filename}`
    });

    assert.equal(attachment.type, type);
    assert.equal(attachment.originalFileName, filename);
    assert.equal(attachment.mimeType, mimeType);
    assert.equal(attachment.storageStatus, "LOCAL_METADATA_ONLY");
  }
});

test("invalid attachment metadata is rejected before submission", () => {
  const invalid = [
    { id: "", originalFileName: "a.jpg", mimeType: "image/jpeg", sizeBytes: 1 },
    { id: "a", originalFileName: "", mimeType: "image/jpeg", sizeBytes: 1 },
    { id: "a", originalFileName: "a.exe", mimeType: "application/octet-stream", sizeBytes: 1 },
    { id: "a", originalFileName: "a.jpg", mimeType: "image/jpeg", sizeBytes: 0 },
    { id: "a", originalFileName: "a.jpg", mimeType: "image/jpeg", sizeBytes: 10_000_001 },
    { id: "a", originalFileName: "a.jpg", mimeType: "image/jpeg", sizeBytes: 1, widthPx: 0 },
    { id: "a", originalFileName: "a.jpg", mimeType: "image/jpeg", sizeBytes: 1, heightPx: 10_001 },
    { id: "a", originalFileName: "a.pdf", mimeType: "application/pdf", sizeBytes: 1, widthPx: 100 }
  ];

  for (const candidate of invalid) {
    assert.throws(() => createLocalAttachmentMetadata(candidate));
  }
});

test("attachment count and duplicate IDs are enforced", () => {
  const existing = [
    createLocalAttachmentMetadata({ id: "a1", originalFileName: "a.jpg", mimeType: "image/jpeg", sizeBytes: 1 }),
    createLocalAttachmentMetadata({ id: "a2", originalFileName: "b.jpg", mimeType: "image/jpeg", sizeBytes: 1 }),
    createLocalAttachmentMetadata({ id: "a3", originalFileName: "c.jpg", mimeType: "image/jpeg", sizeBytes: 1 })
  ];

  assert.throws(() =>
    createLocalAttachmentMetadata({ id: "a4", originalFileName: "d.jpg", mimeType: "image/jpeg", sizeBytes: 1 }, existing)
  );
  assert.throws(() =>
    createLocalAttachmentMetadata({ id: "a1", originalFileName: "again.jpg", mimeType: "image/jpeg", sizeBytes: 1 }, existing.slice(0, 1))
  );
});

test("sample local image attachment and formatted size are deterministic enough for composer state", () => {
  const attachment = sampleLocalImageAttachment([]);

  assert.equal(attachment.type, "IMAGE");
  assert.equal(attachment.state, "selected");
  assert.equal(attachment.storageStatus, "LOCAL_METADATA_ONLY");
  assert.equal(formatAttachmentSize(128_000), "128 KB");
});
