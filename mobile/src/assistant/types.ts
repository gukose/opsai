export type ConversationItem =
  | TextMessage
  | VoiceMessage
  | AttachmentMessage
  | IntentBadgeMessage
  | ActionQuestion
  | TaskPreviewMessage
  | StatusMessage;

export type MessageAuthor = "assistant" | "user";

export type TextMessage = {
  id: string;
  type: "text";
  author: MessageAuthor;
  text: string;
  timestamp?: string;
};

export type VoiceMessage = {
  id: string;
  type: "voice";
  author: "user";
  transcript: string;
  duration: string;
  audioMetadata?: AudioMetadata;
  timestamp?: string;
};

export type AudioMetadata = {
  originalFileName?: string;
  mimeType?: string;
  durationMs?: number;
  sizeBytes?: number;
};

export type AttachmentMessage = {
  id: string;
  type: "attachment";
  author: MessageAuthor;
  attachment: ConversationAttachment;
  timestamp?: string;
};

export type ConversationAttachment = {
  id: string;
  type?: "IMAGE" | "PDF" | "DOCUMENT";
  filename: string;
  size: string;
  mimeType?: string;
  imageUri?: string;
  widthPx?: number;
  heightPx?: number;
  localReference?: string;
  storageStatus?: "LOCAL_METADATA_ONLY" | "REGISTERED";
};

export type LocalAttachmentState =
  | "LOCAL_SELECTED"
  | "REGISTERING"
  | "REGISTERED"
  | "REGISTRATION_FAILED"
  | "MESSAGE_SENDING"
  | "MESSAGE_SENT";

export type LocalAttachmentMetadata = {
  id: string;
  localId?: string;
  serverAttachmentId?: string;
  type: "IMAGE" | "PDF" | "DOCUMENT";
  originalFileName: string;
  mimeType: string;
  sizeBytes: number;
  widthPx?: number;
  heightPx?: number;
  localReference?: string;
  localUri?: string;
  storageStatus: "LOCAL_METADATA_ONLY" | "REGISTERED";
  storageReference?: null;
  createdAt?: string;
  state: LocalAttachmentState;
  errorMessage?: string;
};

export type LocalVoiceTranscriptMetadata = {
  transcript: string;
  languageCode?: string;
  durationMs?: number;
  source: "CLIENT_TRANSCRIPT";
  state: "selected" | "sending" | "sent" | "failed";
};

export type LocalImageObservationMetadata = {
  id: string;
  attachmentId: string;
  text: string;
  source: "USER_PROVIDED";
  state: "selected" | "sending" | "sent" | "failed";
};

export type IntentBadgeMessage = {
  id: string;
  type: "intent";
  label: string;
  tone: "guest" | "maintenance" | "housekeeping" | "lostFound";
};

export type ActionQuestion = {
  id: string;
  type: "question";
  actions: Array<{
    id: string;
    label: string;
    value?: string;
    variant: "confirm" | "secondary";
  }>;
};

export type TaskPreviewMessage = {
  id: string;
  type: "taskPreview";
  task: {
    intent: string;
    type: string;
    room: string;
    description: string;
    assignedTo: string;
    priority: string;
    sla: string;
  };
};

export type StatusMessage = {
  id: string;
  type: "status";
  text: string;
  timestamp?: string;
};

export type AssignedTask = {
  id: string;
  title: string;
  room: string;
  priority: string;
  slaRemaining: string;
};
