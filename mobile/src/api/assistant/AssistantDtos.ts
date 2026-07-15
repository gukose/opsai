export type AssistantInputTypeDto = "TEXT" | "VOICE_TRANSCRIPT" | "VOICE" | "IMAGE" | "MIXED";
export type AssistantMessageRoleDto = "USER" | "ASSISTANT" | "SYSTEM";
export type AssistantAttachmentTypeDto = "IMAGE" | "PDF" | "DOCUMENT";
export type AssistantAttachmentStorageStatusDto = "LOCAL_METADATA_ONLY" | "REGISTERED";
export type AssistantVoiceTranscriptSourceDto = "CLIENT_TRANSCRIPT";
export type AssistantImageObservationSourceDto = "USER_PROVIDED";

export type AssistantStartConversationRequestDto = {
  hotelId: string;
  userId: string;
};

export type AssistantSendMessageRequestDto = {
  text: string;
  inputType: AssistantInputTypeDto;
  transcript?: string | null;
  voiceTranscript?: AssistantVoiceTranscriptDto | null;
  audioMetadata?: AssistantAudioMetadataDto | null;
  attachments?: AssistantMessageAttachmentDto[] | null;
  attachmentIds?: string[];
  imageObservations?: AssistantImageObservationDto[] | null;
};

export type AssistantConfirmTaskRequestDto = {
  idempotencyKey: string;
};

export type AssistantConversationResponseDto = {
  conversationId: string;
  state: string;
  assistantMessage: string;
  intent: string;
  missingFields: AssistantMissingFieldDto[];
  followUpQuestion: AssistantFollowUpQuestionDto | null;
  taskPreview: AssistantTaskPreviewDto | null;
  taskCreationRequest: AssistantTaskCreationRequestDto | null;
  createdTaskId: string | null;
  messages: AssistantConversationMessageDto[];
};

export type AssistantMissingFieldDto = {
  key: string;
  label: string;
  required: boolean;
};

export type AssistantFollowUpQuestionDto = {
  id: string;
  fieldKey: string;
  prompt: string;
  options: AssistantFollowUpOptionDto[];
};

export type AssistantFollowUpOptionDto = {
  id: string;
  label: string;
  value: string;
};

export type AssistantTaskPreviewDto = {
  type: string;
  title: string;
  description: string;
  roomNumber?: string | null;
  publicAreaId?: string | null;
  assetId?: string | null;
  assignedTeam?: string | null;
  priority?: string | null;
  slaMinutes?: number | null;
  requiresPmsUpdate: boolean;
};

export type AssistantTaskCreationRequestDto = {
  conversationId: string;
  draftId: string;
  draftVersion: number;
  idempotencyKey: string;
  preview: AssistantTaskPreviewDto;
};

export type AssistantConversationMessageDto = {
  id: string;
  role: AssistantMessageRoleDto;
  inputType: AssistantInputTypeDto;
  text: string | null;
  voiceTranscript: string | null;
  voiceTranscriptMetadata?: AssistantVoiceTranscriptDto | null;
  audioMetadata: AssistantAudioMetadataDto | null;
  attachments: AssistantMessageAttachmentDto[];
  imageObservations: AssistantImageObservationDto[];
  attachmentIds: string[];
  createdAt: string;
};

export type AssistantAudioMetadataDto = {
  originalFileName?: string | null;
  mimeType?: string | null;
  durationMs?: number | null;
  sizeBytes?: number | null;
};

export type AssistantVoiceTranscriptDto = {
  transcript: string;
  languageCode?: string | null;
  durationMs?: number | null;
  source: AssistantVoiceTranscriptSourceDto;
};

export type AssistantMessageAttachmentDto = {
  id: string;
  type?: AssistantAttachmentTypeDto | null;
  originalFileName?: string | null;
  mimeType?: string | null;
  sizeBytes?: number | null;
  widthPx?: number | null;
  heightPx?: number | null;
  localReference?: string | null;
  storageStatus?: AssistantAttachmentStorageStatusDto | null;
};

export type RegisterAssistantAttachmentRequestDto = {
  type: AssistantAttachmentTypeDto;
  originalFileName: string;
  mimeType: string;
  sizeBytes: number;
  widthPx?: number | null;
  heightPx?: number | null;
};

export type RegisteredAssistantAttachmentResponseDto = {
  attachmentId: string;
  conversationId: string;
  type: AssistantAttachmentTypeDto;
  originalFileName: string;
  mimeType: string;
  sizeBytes: number;
  widthPx?: number | null;
  heightPx?: number | null;
  storageStatus: "REGISTERED";
  storageReference?: string | null;
  createdAt: string;
};

export type AssistantImageObservationDto = {
  id?: string | null;
  attachmentId?: string | null;
  text?: string | null;
  source?: AssistantImageObservationSourceDto | null;
  description?: string | null;
  confidence?: number | null;
};
