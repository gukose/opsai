import {
  TaskController_cancelTask,
  TaskController_completeTask,
  TaskController_getTask,
  TaskController_getTaskAttachments,
  TaskController_listTasks,
  TaskController_pauseTask,
  TaskController_resumeTask,
  TaskController_startTask
} from "@hotelopai/api-client";
import { MobileHotelOpAiClient } from "../hotelOpAiClient";
import type { TaskAttachmentResponseDto, TaskPageResponseDto, TaskResponseDto } from "./TaskDtos";

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
  getTaskAttachments(taskId: string): Promise<TaskAttachmentResponseDto[]>;
  startTask(taskId: string): Promise<TaskResponseDto>;
  pauseTask(taskId: string): Promise<TaskResponseDto>;
  resumeTask(taskId: string): Promise<TaskResponseDto>;
  completeTask(taskId: string): Promise<TaskResponseDto>;
  cancelTask(taskId: string): Promise<TaskResponseDto>;
}

export class HttpTaskApi implements TaskApi {
  private readonly client: MobileHotelOpAiClient;

  constructor(client: MobileHotelOpAiClient) {
    this.client = client;
  }

  listTasks(filters?: TaskListFilters): Promise<TaskListResponseDto> {
    return this.client.call("GET", (sdk, signal) => TaskController_listTasks(sdk, { query: buildTaskListQuery(filters), signal }));
  }

  getTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.call("GET", (sdk, signal) => TaskController_getTask(sdk, { pathParams: { taskId }, signal }));
  }

  getTaskAttachments(taskId: string): Promise<TaskAttachmentResponseDto[]> {
    return this.client.call("GET", (sdk, signal) => TaskController_getTaskAttachments(sdk, { pathParams: { taskId }, signal }));
  }

  startTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.call("POST", (sdk, signal) => TaskController_startTask(sdk, { pathParams: { taskId }, signal }));
  }

  pauseTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.call("POST", (sdk, signal) => TaskController_pauseTask(sdk, { pathParams: { taskId }, signal }));
  }

  resumeTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.call("POST", (sdk, signal) => TaskController_resumeTask(sdk, { pathParams: { taskId }, signal }));
  }

  completeTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.call("POST", (sdk, signal) => TaskController_completeTask(sdk, { pathParams: { taskId }, signal }));
  }

  cancelTask(taskId: string): Promise<TaskResponseDto> {
    return this.client.call("POST", (sdk, signal) => TaskController_cancelTask(sdk, { pathParams: { taskId }, signal }));
  }
}

export type TaskListQuery = {
  q?: string;
  status?: string;
  priority?: string;
  assignment?: string;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
};

export function buildTaskListQuery(filters?: TaskListFilters): TaskListQuery {
  const query: TaskListQuery = {};
  const text = filters?.q?.trim();
  if (text) {
    query.q = text;
  }
  if (filters?.status?.length) {
    query.status = filters.status.join(",");
  }
  if (filters?.priority?.length) {
    query.priority = filters.priority.join(",");
  }
  if (filters?.assignment) {
    query.assignment = filters.assignment;
  }
  if (filters?.createdFrom) {
    query.createdFrom = filters.createdFrom;
  }
  if (filters?.createdTo) {
    query.createdTo = filters.createdTo;
  }
  if (typeof filters?.page === "number") {
    query.page = filters.page;
  }
  if (typeof filters?.size === "number") {
    query.size = filters.size;
  }
  return query;
}
