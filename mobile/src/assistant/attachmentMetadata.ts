import type { LocalAttachmentMetadata } from "./types";

export const MAX_ASSISTANT_ATTACHMENTS = 3;
export const MAX_ASSISTANT_ATTACHMENT_BYTES = 10_000_000;

const IMAGE_MIME_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);
const SUPPORTED_MIME_TYPES = new Set([...IMAGE_MIME_TYPES, "application/pdf", "text/plain"]);

export type AttachmentCandidate = {
  id: string;
  originalFileName: string;
  mimeType: string;
  sizeBytes: number;
  widthPx?: number;
  heightPx?: number;
  localReference?: string;
  localUri?: string;
};

export type RegisteredAttachmentResponse = {
  attachmentId: string;
  conversationId: string;
  type: "IMAGE" | "PDF" | "DOCUMENT";
  originalFileName: string;
  mimeType: string;
  sizeBytes: number;
  widthPx?: number | null;
  heightPx?: number | null;
  storageStatus: "REGISTERED";
  storageReference?: string | null;
  createdAt: string;
};

export function createLocalAttachmentMetadata(
  candidate: AttachmentCandidate,
  existing: LocalAttachmentMetadata[] = []
): LocalAttachmentMetadata {
  const id = candidate.id.trim();
  const originalFileName = candidate.originalFileName.trim();
  const mimeType = candidate.mimeType.trim().toLowerCase();
  const type = attachmentTypeForMimeType(mimeType);

  if (!id) {
    throw new Error("Attachment ID is required.");
  }
  if (existing.some((attachment) => attachment.id === id)) {
    throw new Error("Attachment is already selected.");
  }
  if (existing.length >= MAX_ASSISTANT_ATTACHMENTS) {
    throw new Error("You can attach up to 3 local references.");
  }
  if (!originalFileName) {
    throw new Error("Attachment filename is required.");
  }
  if (originalFileName.length > 180) {
    throw new Error("Attachment filename is too long.");
  }
  if (!SUPPORTED_MIME_TYPES.has(mimeType)) {
    throw new Error("Attachment type is not supported.");
  }
  if (!Number.isFinite(candidate.sizeBytes) || candidate.sizeBytes < 1 || candidate.sizeBytes > MAX_ASSISTANT_ATTACHMENT_BYTES) {
    throw new Error("Attachment size must be between 1 byte and 10 MB.");
  }
  validateDimension(candidate.widthPx, "width");
  validateDimension(candidate.heightPx, "height");
  if (type !== "IMAGE" && (candidate.widthPx != null || candidate.heightPx != null)) {
    throw new Error("Dimensions are only supported for image attachments.");
  }

  return {
    id,
    localId: id,
    type,
    originalFileName,
    mimeType,
    sizeBytes: candidate.sizeBytes,
    widthPx: candidate.widthPx,
    heightPx: candidate.heightPx,
    localReference: candidate.localReference?.trim() || undefined,
    localUri: candidate.localUri?.trim() || candidate.localReference?.trim() || undefined,
    storageStatus: "LOCAL_METADATA_ONLY",
    state: "LOCAL_SELECTED"
  };
}

export function applyRegisteredAttachment(
  attachment: LocalAttachmentMetadata,
  response: RegisteredAttachmentResponse
): LocalAttachmentMetadata {
  if (response.storageStatus !== "REGISTERED") {
    throw new Error("Attachment registration did not return REGISTERED metadata.");
  }
  if (response.storageReference != null) {
    throw new Error("Registered metadata must not include a storage reference.");
  }

  const serverAttachmentId = response.attachmentId.trim();
  if (!serverAttachmentId) {
    throw new Error("Registered attachment ID is required.");
  }

  return {
    ...attachment,
    id: serverAttachmentId,
    serverAttachmentId,
    type: response.type,
    originalFileName: response.originalFileName,
    mimeType: response.mimeType,
    sizeBytes: response.sizeBytes,
    widthPx: response.widthPx ?? undefined,
    heightPx: response.heightPx ?? undefined,
    storageStatus: "REGISTERED",
    storageReference: null,
    createdAt: response.createdAt,
    state: "REGISTERED",
    errorMessage: undefined
  };
}

export function normalizeAttachmentState(attachment: LocalAttachmentMetadata): LocalAttachmentMetadata {
  const isRegistered = attachment.storageStatus === "REGISTERED" && Boolean(attachment.serverAttachmentId ?? attachment.id);
  const state = isRegistered ? "REGISTERED" : normalizeState(attachment.state);

  return {
    ...attachment,
    localId: attachment.localId ?? attachment.id,
    serverAttachmentId: isRegistered ? attachment.serverAttachmentId ?? attachment.id : undefined,
    storageStatus: isRegistered ? "REGISTERED" : "LOCAL_METADATA_ONLY",
    storageReference: isRegistered ? null : undefined,
    state
  };
}

export function formatAttachmentSize(sizeBytes: number): string {
  if (sizeBytes >= 1_000_000) {
    return `${(sizeBytes / 1_000_000).toFixed(1)} MB`;
  }
  if (sizeBytes >= 1_000) {
    return `${Math.round(sizeBytes / 1_000)} KB`;
  }
  return `${sizeBytes} B`;
}

export function sampleLocalImageAttachment(existing: LocalAttachmentMetadata[]): LocalAttachmentMetadata {
  const next = existing.length + 1;
  return createLocalAttachmentMetadata(
    {
      id: `local-attachment-${Date.now()}-${next}`,
      originalFileName: `local-reference-${next}.jpg`,
      mimeType: "image/jpeg",
      sizeBytes: 128_000,
      widthPx: 1280,
      heightPx: 720,
      localReference: `local://assistant/local-reference-${next}.jpg`
    },
    existing
  );
}

function attachmentTypeForMimeType(mimeType: string): LocalAttachmentMetadata["type"] {
  if (IMAGE_MIME_TYPES.has(mimeType)) {
    return "IMAGE";
  }
  if (mimeType === "application/pdf") {
    return "PDF";
  }
  if (mimeType === "text/plain") {
    return "DOCUMENT";
  }
  throw new Error("Attachment type is not supported.");
}

function validateDimension(value: number | undefined, label: string) {
  if (value == null) {
    return;
  }
  if (!Number.isFinite(value) || value < 1 || value > 10_000) {
    throw new Error(`Attachment ${label} must be between 1 and 10000 pixels.`);
  }
}

function normalizeState(state: LocalAttachmentMetadata["state"] | string): LocalAttachmentMetadata["state"] {
  switch (state) {
    case "REGISTERED":
      return "REGISTERED";
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
