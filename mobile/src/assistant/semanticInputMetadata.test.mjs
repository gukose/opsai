import assert from "node:assert/strict";
import test from "node:test";

import { createLocalAttachmentMetadata } from "./attachmentMetadata.ts";
import {
  createLocalImageObservationMetadata,
  createLocalVoiceTranscriptMetadata,
  MAX_IMAGE_OBSERVATION_CHARS,
  MAX_VOICE_TRANSCRIPT_CHARS
} from "./semanticInputMetadata.ts";

test("client transcript metadata trims and preserves semantic fields only", () => {
  const transcript = createLocalVoiceTranscriptMetadata({
    transcript: " Room 502 sink is leaking ",
    languageCode: "en-US",
    durationMs: 4200
  });

  assert.equal(transcript.transcript, "Room 502 sink is leaking");
  assert.equal(transcript.languageCode, "en-US");
  assert.equal(transcript.durationMs, 4200);
  assert.equal(transcript.source, "CLIENT_TRANSCRIPT");
  assert.equal(transcript.state, "selected");
});

test("client transcript validation rejects blank, oversized, invalid language and invalid duration", () => {
  assert.throws(() => createLocalVoiceTranscriptMetadata({ transcript: " " }));
  assert.throws(() => createLocalVoiceTranscriptMetadata({ transcript: "x".repeat(MAX_VOICE_TRANSCRIPT_CHARS + 1) }));
  assert.throws(() => createLocalVoiceTranscriptMetadata({ transcript: "ok", languageCode: "english-US" }));
  assert.throws(() => createLocalVoiceTranscriptMetadata({ transcript: "ok", durationMs: 0 }));
  assert.throws(() => createLocalVoiceTranscriptMetadata({ transcript: "ok", durationMs: 600001 }));
});

test("image observation can only be linked to a selected image attachment", () => {
  const image = createLocalAttachmentMetadata({
    id: "att-1",
    originalFileName: "sink.png",
    mimeType: "image/png",
    sizeBytes: 1234,
    widthPx: 100,
    heightPx: 100,
    localReference: "local://sink.png"
  });

  const observation = createLocalImageObservationMetadata(image, " Water visible under the sink ");

  assert.equal(observation.attachmentId, "att-1");
  assert.equal(observation.text, "Water visible under the sink");
  assert.equal(observation.source, "USER_PROVIDED");
  assert.equal(observation.state, "selected");
});

test("image observation validation rejects non-image attachment and oversized text", () => {
  const document = createLocalAttachmentMetadata({
    id: "doc-1",
    originalFileName: "note.txt",
    mimeType: "text/plain",
    sizeBytes: 12
  });

  assert.throws(() => createLocalImageObservationMetadata(document, "Visible issue"));

  const image = createLocalAttachmentMetadata({
    id: "att-1",
    originalFileName: "sink.png",
    mimeType: "image/png",
    sizeBytes: 1234,
    widthPx: 100,
    heightPx: 100
  });
  assert.throws(() => createLocalImageObservationMetadata(image, "x".repeat(MAX_IMAGE_OBSERVATION_CHARS + 1)));
});
