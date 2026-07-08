export type AssistantInputTypeDto = "TEXT" | "VOICE_TRANSCRIPT" | "VOICE" | "IMAGE" | "MIXED";
export type AssistantMessageRoleDto = "USER" | "ASSISTANT" | "SYSTEM";

export type AssistantStartConversationRequestDto = {
  hotelId: string;
  userId: string;
};

export type AssistantSendMessageRequestDto = {
  text: string;
  inputType: AssistantInputTypeDto;
  transcript?: string | null;
  audioMetadata?: AssistantAudioMetadataDto | null;
  attachments?: AssistantMessageAttachmentDto[] | null;
  attachmentIds?: string[];
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

export type AssistantMessageAttachmentDto = {
  id: string;
  originalFileName?: string | null;
  mimeType?: string | null;
  sizeBytes?: number | null;
  widthPx?: number | null;
  heightPx?: number | null;
};

export type AssistantImageObservationDto = {
  attachmentId?: string | null;
  description: string;
  confidence?: number | null;
};
