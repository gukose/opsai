import type {
  LocalAttachmentMetadata,
  LocalImageObservationMetadata,
  LocalVoiceTranscriptMetadata
} from "./types";

export const ASSISTANT_DRAFT_SCHEMA_VERSION = 1;

export type AssistantDraftSnapshot = {
  schemaVersion: typeof ASSISTANT_DRAFT_SCHEMA_VERSION;
  conversationId?: string | null;
  text: string;
  attachments: LocalAttachmentMetadata[];
  voiceTranscript: LocalVoiceTranscriptMetadata | null;
  imageObservations: LocalImageObservationMetadata[];
};

export type AssistantDraftInput = Omit<AssistantDraftSnapshot, "schemaVersion">;

export function normalizeDraft(draft: Partial<AssistantDraftSnapshot> | null | undefined): AssistantDraftSnapshot {
  return {
    schemaVersion: ASSISTANT_DRAFT_SCHEMA_VERSION,
    conversationId: typeof draft?.conversationId === "string" ? draft.conversationId : null,
    text: typeof draft?.text === "string" ? draft.text : "",
    attachments: Array.isArray(draft?.attachments)
      ? draft.attachments.map(normalizeDraftAttachment)
      : [],
    voiceTranscript: draft?.voiceTranscript
      ? {
          ...draft.voiceTranscript,
          source: "CLIENT_TRANSCRIPT",
          state: draft.voiceTranscript.state === "failed" ? "failed" : "selected"
        }
      : null,
    imageObservations: Array.isArray(draft?.imageObservations)
      ? draft.imageObservations.map((observation) => ({
          ...observation,
          source: "USER_PROVIDED",
          state: observation.state === "failed" ? "failed" : "selected"
        }))
      : []
  };
}

function normalizeDraftAttachment(attachment: LocalAttachmentMetadata): LocalAttachmentMetadata {
  const isRegistered = attachment.storageStatus === "REGISTERED" && Boolean(attachment.serverAttachmentId ?? attachment.id);
  return {
    ...attachment,
    localId: attachment.localId ?? attachment.id,
    serverAttachmentId: isRegistered ? attachment.serverAttachmentId ?? attachment.id : undefined,
    storageStatus: isRegistered ? "REGISTERED" : "LOCAL_METADATA_ONLY",
    storageReference: isRegistered ? null : undefined,
    state: isRegistered ? "REGISTERED" : normalizeDraftAttachmentState(attachment.state)
  };
}

function normalizeDraftAttachmentState(state: LocalAttachmentMetadata["state"] | string): LocalAttachmentMetadata["state"] {
  switch (state) {
    case "REGISTRATION_FAILED":
      return "REGISTRATION_FAILED";
    case "REGISTERING":
      return "REGISTRATION_FAILED";
    case "MESSAGE_SENDING":
    case "MESSAGE_SENT":
    case "sending":
    case "sent":
    case "selected":
    case "LOCAL_SELECTED":
    default:
      return "LOCAL_SELECTED";
  }
}

export function hasAssistantDraftContent(draft: AssistantDraftInput): boolean {
  return Boolean(
    draft.text.trim() ||
      draft.attachments.length > 0 ||
      draft.voiceTranscript ||
      draft.imageObservations.length > 0
  );
}
