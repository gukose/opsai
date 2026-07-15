export type TaskAssignmentResponseDto = {
  assigneeType: string;
  assigneeId: string;
  displayName: string;
  assignedAt: string;
};

export type TaskResponseDto = {
  id: string;
  hotelId: string;
  intentType: string;
  source: string;
  title: string;
  description: string;
  priority: string;
  status: string;
  slaDeadline: string;
  assignment: TaskAssignmentResponseDto | null;
  createdAt: string;
  updatedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  overdueAt: string | null;
};

export type TaskPageResponseDto = {
  items: TaskResponseDto[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
};

export type TaskAttachmentResponseDto = {
  attachmentId: string;
  conversationId: string;
  type: "IMAGE" | "PDF" | "DOCUMENT";
  originalFileName: string;
  declaredMimeType: string;
  declaredSizeBytes: number;
  widthPx?: number | null;
  heightPx?: number | null;
  storageStatus: "REGISTERED";
  sourceType: "ASSISTANT_MESSAGE" | "VISION_ANALYSIS";
  analysisId?: string | null;
  analysisImportId?: string | null;
  createdAt: string;
};
