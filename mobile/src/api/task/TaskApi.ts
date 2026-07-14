import type { ApiClient } from "../client/ApiClient";
import type { TaskPageResponseDto, TaskResponseDto } from "./TaskDtos";

export type TaskListFilters = {
  q?: string;
  status?: string[];
  priority?: string[];
  assignment?: string | null;
  createdFrom?: string | null;
  createdTo?: string | null;
  page?: number;
  size?: number;
};

export type TaskListResponseDto = TaskResponseDto[] | TaskPageResponseDto;

export interface TaskApi {
  listTasks(filters?: TaskListFilters): Promise<TaskListResponseDto>;
  getTask(taskId: string): Promise<TaskResponseDto>;
  startTask(taskId: string): Promise<TaskResponseDto>;
  pauseTask(taskId: string): Promise<TaskResponseDto>;
  resumeTask(taskId: string): Promise<TaskResponseDto>;
  completeTask(taskId: string): Promise<TaskResponseDto>;
  cancelTask(taskId: string): Promise<TaskResponseDto>;
}

export class HttpTaskApi implements TaskApi {
  private readonly client: ApiClient;

  constructor(client: ApiClient) {
    this.client = client;
  }

  listTasks(filters?: TaskListFilters): Promise<TaskListResponseDto> {
    return this.client.get(buildTaskListPath(filters));
  }

  getTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.get(`/api/v1/tasks/${taskId}`);
  }

  startTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.post(`/api/v1/tasks/${taskId}/start`, {});
  }

  pauseTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.post(`/api/v1/tasks/${taskId}/pause`, {});
  }

  resumeTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.post(`/api/v1/tasks/${taskId}/resume`, {});
  }

  completeTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.post(`/api/v1/tasks/${taskId}/complete`, {});
  }

  cancelTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.post(`/api/v1/tasks/${taskId}/cancel`, {});
  }
}

export function buildTaskListPath(filters?: TaskListFilters): string {
  const params = new URLSearchParams();
  const text = filters?.q?.trim();
  if (text) {
    params.set("q", text);
  }
  if (filters?.status?.length) {
    params.set("status", filters.status.join(","));
  }
  if (filters?.priority?.length) {
    params.set("priority", filters.priority.join(","));
  }
  if (filters?.assignment) {
    params.set("assignment", filters.assignment);
  }
  if (filters?.createdFrom) {
    params.set("createdFrom", filters.createdFrom);
  }
  if (filters?.createdTo) {
    params.set("createdTo", filters.createdTo);
  }
  if (typeof filters?.page === "number") {
    params.set("page", String(filters.page));
  }
  if (typeof filters?.size === "number") {
    params.set("size", String(filters.size));
  }

  const query = params.toString();
  return query ? `/api/v1/tasks?${query}` : "/api/v1/tasks";
}
