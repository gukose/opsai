import type { TaskListResponseDto } from "../api/task/TaskApi";
import type { TaskAttachmentResponseDto, TaskResponseDto } from "../api/task/TaskDtos";

export type TaskFilterState = {
  q: string;
  status: string[];
  priority: string[];
  assignment: string | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  page?: number | null;
  size?: number | null;
};

export type TaskSummary = {
  id: string;
  title: string;
  description: string;
  status: string;
  priority: string;
  slaDeadline: string;
  roomOrLocation: string | null;
  assignmentLabel: string | null;
  updatedAt: string;
  intentType: string;
  source: string;
};

export type TaskDetail = TaskSummary & {
  hotelId: string;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  overdueAt: string | null;
  assigneeType: string | null;
  assigneeId: string | null;
  assignedAt: string | null;
  attachments?: TaskAttachmentMetadata[];
};

export type TaskAttachmentMetadata = {
  attachmentId: string;
  conversationId: string;
  type: "IMAGE" | "PDF" | "DOCUMENT";
  originalFileName: string;
  declaredMimeType: string;
  declaredSizeBytes: number;
  widthPx: number | null;
  heightPx: number | null;
  storageStatus: "REGISTERED";
  sourceType: "ASSISTANT_MESSAGE" | "VISION_ANALYSIS";
  analysisId: string | null;
  analysisImportId: string | null;
  createdAt: string;
};

export function taskSummaryFromResponse(task: TaskResponseDto): TaskSummary {
  return {
    id: task.id,
    title: task.title,
    description: task.description,
    status: task.status,
    priority: task.priority,
    slaDeadline: task.slaDeadline,
    roomOrLocation: extractRoomOrLocation(task.title, task.description),
    assignmentLabel: task.assignment?.displayName ?? null,
    updatedAt: task.updatedAt,
    intentType: task.intentType,
    source: task.source
  };
}

export function taskDetailFromResponse(task: TaskResponseDto): TaskDetail {
  const summary = taskSummaryFromResponse(task);

  return {
    ...summary,
    hotelId: task.hotelId,
    createdAt: task.createdAt,
    startedAt: task.startedAt,
    completedAt: task.completedAt,
    cancelledAt: task.cancelledAt,
    overdueAt: task.overdueAt,
    assigneeType: task.assignment?.assigneeType ?? null,
    assigneeId: task.assignment?.assigneeId ?? null,
    assignedAt: task.assignment?.assignedAt ?? null,
    attachments: []
  };
}

export function taskAttachmentFromResponse(attachment: TaskAttachmentResponseDto): TaskAttachmentMetadata {
  return {
    attachmentId: attachment.attachmentId,
    conversationId: attachment.conversationId,
    type: attachment.type,
    originalFileName: attachment.originalFileName,
    declaredMimeType: attachment.declaredMimeType,
    declaredSizeBytes: attachment.declaredSizeBytes,
    widthPx: attachment.widthPx ?? null,
    heightPx: attachment.heightPx ?? null,
    storageStatus: attachment.storageStatus,
    sourceType: attachment.sourceType,
    analysisId: attachment.analysisId ?? null,
    analysisImportId: attachment.analysisImportId ?? null,
    createdAt: attachment.createdAt
  };
}

export function taskSummariesFromListResponse(response: TaskListResponseDto): TaskSummary[] {
  const tasks = Array.isArray(response) ? response : response.items;
  return tasks.map(taskSummaryFromResponse);
}

export function emptyTaskFilters(): TaskFilterState {
  return {
    q: "",
    status: [],
    priority: [],
    assignment: null,
    createdFrom: null,
    createdTo: null
  };
}

export function hasActiveTaskFilters(filters: TaskFilterState): boolean {
  return Boolean(
    filters.q.trim() ||
      filters.status.length > 0 ||
      filters.priority.length > 0 ||
      filters.assignment ||
      filters.createdFrom ||
      filters.createdTo
  );
}

export function shouldClearVisibleTasksBeforeLoad(filters: TaskFilterState): boolean {
  return !hasActiveTaskFilters(filters);
}

function extractRoomOrLocation(title: string, description: string): string | null {
  const text = `${title} ${description}`.trim();
  const roomMatch = text.match(/room\s+([0-9]{1,4}[a-zA-Z]?)/i);
  if (roomMatch?.[1]) {
    return `Room ${roomMatch[1]}`;
  }

  const floorMatch = text.match(/floor\s+([0-9]{1,2})/i);
  if (floorMatch?.[1]) {
    return `Floor ${floorMatch[1]}`;
  }

  return null;
}
