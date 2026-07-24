import type { components } from "@hotelopai/api-client";

export type AssistantInputTypeDto = components["schemas"]["SendAssistantMessageRequest"]["inputType"];
export type AssistantMessageRoleDto = components["schemas"]["ConversationMessageDto"]["role"];
export type AssistantAttachmentTypeDto = NonNullable<components["schemas"]["MessageAttachmentDto"]["type"]>;
export type AssistantAttachmentStorageStatusDto = NonNullable<components["schemas"]["MessageAttachmentDto"]["storageStatus"]>;
export type AssistantVoiceTranscriptSourceDto = NonNullable<components["schemas"]["VoiceTranscriptDto"]["source"]>;
export type AssistantImageObservationSourceDto = NonNullable<components["schemas"]["ImageObservationDto"]["source"]>;

export type AssistantStartConversationRequestDto = components["schemas"]["StartConversationRequest"];
export type AssistantSendMessageRequestDto = components["schemas"]["SendAssistantMessageRequest"];
export type AssistantConfirmTaskRequestDto = components["schemas"]["ConfirmTaskRequest"];
export type AssistantConversationResponseDto = components["schemas"]["AssistantConversationResponse"];
export type AssistantMissingFieldDto = components["schemas"]["MissingFieldDto"];
export type AssistantFollowUpQuestionDto = components["schemas"]["FollowUpQuestionDto"];
export type AssistantFollowUpOptionDto = components["schemas"]["FollowUpOptionDto"];
export type AssistantTaskPreviewDto = components["schemas"]["TaskPreviewDto"];
export type AssistantTaskCreationRequestDto = components["schemas"]["TaskCreationRequestDto"];
export type AssistantConversationMessageDto = components["schemas"]["ConversationMessageDto"];
export type AssistantAudioMetadataDto = components["schemas"]["AudioMetadataDto"];
export type AssistantVoiceTranscriptDto = components["schemas"]["VoiceTranscriptDto"];
export type AssistantMessageAttachmentDto = components["schemas"]["MessageAttachmentDto"];
export type RegisterAssistantAttachmentRequestDto = components["schemas"]["RegisterAssistantAttachmentRequest"];
export type RegisteredAssistantAttachmentResponseDto =
  components["schemas"]["RegisteredAssistantAttachmentResponse"] & { storageStatus: "REGISTERED" };
export type AssistantImageObservationDto = components["schemas"]["ImageObservationDto"];
