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

export function normalizeDraft(draft: AssistantDraftSnapshot): AssistantDraftSnapshot {
  return {
    schemaVersion: ASSISTANT_DRAFT_SCHEMA_VERSION,
    conversationId: typeof draft.conversationId === "string" ? draft.conversationId : null,
    text: typeof draft.text === "string" ? draft.text : "",
    attachments: Array.isArray(draft.attachments)
      ? draft.attachments.map((attachment) => ({
          ...attachment,
          storageStatus: "LOCAL_METADATA_ONLY",
          state: attachment.state === "failed" ? "failed" : "selected"
        }))
      : [],
    voiceTranscript: draft.voiceTranscript
      ? {
          ...draft.voiceTranscript,
          source: "CLIENT_TRANSCRIPT",
          state: draft.voiceTranscript.state === "failed" ? "failed" : "selected"
        }
      : null,
    imageObservations: Array.isArray(draft.imageObservations)
      ? draft.imageObservations.map((observation) => ({
          ...observation,
          source: "USER_PROVIDED",
          state: observation.state === "failed" ? "failed" : "selected"
        }))
      : []
  };
}
