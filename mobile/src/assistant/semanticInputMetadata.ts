import type {
  LocalAttachmentMetadata,
  LocalImageObservationMetadata,
  LocalVoiceTranscriptMetadata
} from "./types";

export const MAX_VOICE_TRANSCRIPT_CHARS = 4000;
export const MAX_IMAGE_OBSERVATION_CHARS = 2000;
const LANGUAGE_CODE_PATTERN = /^[a-zA-Z]{2,3}(-[a-zA-Z]{2})?$/;

export function createLocalVoiceTranscriptMetadata(input: {
  transcript: string;
  languageCode?: string;
  durationMs?: number;
}): LocalVoiceTranscriptMetadata {
  const transcript = input.transcript.trim();
  if (!transcript) {
    throw new Error("Client transcript is required.");
  }
  if (transcript.length > MAX_VOICE_TRANSCRIPT_CHARS) {
    throw new Error("Client transcript is too long.");
  }

  const languageCode = input.languageCode?.trim();
  if (languageCode && !LANGUAGE_CODE_PATTERN.test(languageCode)) {
    throw new Error("Language code is invalid.");
  }
  if (input.durationMs != null && (input.durationMs < 1 || input.durationMs > 600000)) {
    throw new Error("Transcript duration is invalid.");
  }

  return {
    transcript,
    languageCode: languageCode || undefined,
    durationMs: input.durationMs,
    source: "CLIENT_TRANSCRIPT",
    state: "selected"
  };
}

export function createLocalImageObservationMetadata(
  attachment: LocalAttachmentMetadata,
  text: string,
  existing: LocalImageObservationMetadata[] = []
): LocalImageObservationMetadata {
  if (attachment.type !== "IMAGE") {
    throw new Error("Image notes require an image attachment.");
  }

  const normalizedText = text.trim();
  if (!normalizedText) {
    throw new Error("Image note is required.");
  }
  if (normalizedText.length > MAX_IMAGE_OBSERVATION_CHARS) {
    throw new Error("Image note is too long.");
  }

  const id = `obs-${attachment.id}-${existing.length + 1}`;
  if (existing.some((observation) => observation.id === id)) {
    throw new Error("Duplicate image note identifier.");
  }

  return {
    id,
    attachmentId: attachment.id,
    text: normalizedText,
    source: "USER_PROVIDED",
    state: "selected"
  };
}
