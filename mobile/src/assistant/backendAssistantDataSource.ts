import type { CurrentUserSnapshot } from "../session/sessionTypes";
import { HttpAssistantApi } from "../api/assistant/AssistantApi";
import type { ApiClient } from "../api/client/ApiClient";
import { AppApiError } from "../api/client/AppApiError";
import {
  assistantInterpretationFailureMessage,
  isAssistantInterpretationFailureResponse,
  mapAssistantConversationResponseToHomeState
} from "../api/assistant/assistantMapper";
import type { AssistantDataSource } from "./assistantDataSource";
import type { AssistantHomeState } from "./homeState";
import {
  LocalAttachmentMetadata,
  LocalImageObservationMetadata,
  LocalVoiceTranscriptMetadata
} from "./types";
import type { RegisteredAttachmentResponse } from "./attachmentMetadata";
import {
  buildAttachmentRegistrationRequest,
  splitAssistantMessageAttachments
} from "./assistantAttachmentRequestMapper";

export class BackendAssistantDataSource implements AssistantDataSource {
  private readonly api: HttpAssistantApi;
  private readonly currentUserProvider: () => CurrentUserSnapshot | null;

  constructor(client: ApiClient, currentUserProvider: () => CurrentUserSnapshot | null) {
    this.api = new HttpAssistantApi(client);
    this.currentUserProvider = currentUserProvider;
  }

  async loadHomeState(): Promise<AssistantHomeState> {
    const currentUser = this.currentUserProvider();
    if (!currentUser?.hotelId || !currentUser.userId) {
      throw new Error("Current user session is missing hotel or user context.");
    }

    const response = await this.api.startConversation({
      hotelId: currentUser.hotelId,
      userId: currentUser.userId
    });

    return mapAssistantConversationResponseToHomeState(response);
  }

  async sendTextMessage(
    conversationId: string,
    text: string,
    attachments: LocalAttachmentMetadata[] = [],
    voiceTranscript?: LocalVoiceTranscriptMetadata | null,
    imageObservations: LocalImageObservationMetadata[] = []
  ): Promise<AssistantHomeState> {
    const { registeredAttachmentIds, localMetadataAttachments } = splitAssistantMessageAttachments(attachments);
    const response = await this.api.sendMessage(conversationId, {
      text,
      inputType: attachments.length > 0 || voiceTranscript || imageObservations.length > 0 ? "MIXED" : "TEXT",
      voiceTranscript: voiceTranscript
        ? {
            transcript: voiceTranscript.transcript,
            languageCode: voiceTranscript.languageCode ?? null,
            durationMs: voiceTranscript.durationMs ?? null,
            source: "CLIENT_TRANSCRIPT"
          }
        : null,
      attachments: localMetadataAttachments,
      attachmentIds: registeredAttachmentIds,
      imageObservations: imageObservations.map((observation) => ({
        id: observation.id,
        attachmentId: observation.attachmentId,
        text: observation.text,
        source: "USER_PROVIDED"
      }))
    });
    throwIfInterpretationFailed(response);
    return mapAssistantConversationResponseToHomeState(response);
  }

  async registerAttachment(
    conversationId: string,
    attachment: LocalAttachmentMetadata
  ): Promise<RegisteredAttachmentResponse> {
    return this.api.registerAttachment(conversationId, buildAttachmentRegistrationRequest(attachment));
  }

  async sendVoiceMessage(
    conversationId: string,
    transcript: string,
    audioMetadata?: {
      originalFileName?: string;
      mimeType?: string;
      durationMs?: number;
      sizeBytes?: number;
    }
  ): Promise<AssistantHomeState> {
    const response = await this.api.sendMessage(conversationId, {
      text: transcript,
      voiceTranscript: {
        transcript,
        languageCode: null,
        durationMs: audioMetadata?.durationMs ?? null,
        source: "CLIENT_TRANSCRIPT"
      },
      audioMetadata,
      inputType: "VOICE",
      attachmentIds: []
    });
    throwIfInterpretationFailed(response);
    return mapAssistantConversationResponseToHomeState(response);
  }

  async confirmTask(
    conversationId: string,
    idempotencyKey: string
  ): Promise<AssistantHomeState> {
    const response = await this.api.confirmTask(conversationId, { idempotencyKey });

    return mapAssistantConversationResponseToHomeState(response);
  }

  async resetConversation(conversationId: string): Promise<AssistantHomeState> {
    const response = await this.api.resetConversation(conversationId);

    return mapAssistantConversationResponseToHomeState(response);
  }
}

function throwIfInterpretationFailed(
  response: Parameters<typeof mapAssistantConversationResponseToHomeState>[0]
) {
  if (!isAssistantInterpretationFailureResponse(response)) {
    return;
  }

  throw new AppApiError(assistantInterpretationFailureMessage(response), {
    kind: "unknown"
  });
}
