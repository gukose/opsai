import { appApiBaseUrl } from "../config/appConfig";
import { HttpTaskApi, TaskListFilters } from "../api/task/TaskApi";
import { MobileHotelOpAiClient } from "../api/hotelOpAiClient";
import {
  TaskDetail,
  TaskFilterState,
  taskAttachmentFromResponse,
  taskDetailFromResponse,
  taskSummariesFromListResponse,
  TaskSummary
} from "./types";

export class TaskService {
  private readonly taskApi: HttpTaskApi;

  constructor(accessTokenProvider: () => string | null, refreshAccessToken?: () => Promise<string | null>) {
    this.taskApi = new HttpTaskApi(
      new MobileHotelOpAiClient({
        baseUrl: appApiBaseUrl,
        accessTokenProvider,
        refreshAccessToken
      })
    );
  }

  async listTasks(filters?: TaskFilterState): Promise<TaskSummary[]> {
    const response = await this.taskApi.listTasks(filters ? toApiFilters(filters) : undefined);
    return taskSummariesFromListResponse(response);
  }

  async getTask(taskId: string): Promise<TaskDetail> {
    const task = taskDetailFromResponse(await this.taskApi.getTask(taskId));
    try {
      const attachments = await this.taskApi.getTaskAttachments(taskId);
      return {
        ...task,
        attachments: attachments.map(taskAttachmentFromResponse)
      };
    } catch {
      return task;
    }
  }

  async startTask(taskId: string): Promise<TaskDetail> {
    return taskDetailFromResponse(await this.taskApi.startTask(taskId));
  }

  async pauseTask(taskId: string): Promise<TaskDetail> {
    return taskDetailFromResponse(await this.taskApi.pauseTask(taskId));
  }

  async resumeTask(taskId: string): Promise<TaskDetail> {
    return taskDetailFromResponse(await this.taskApi.resumeTask(taskId));
  }

  async completeTask(taskId: string): Promise<TaskDetail> {
    return taskDetailFromResponse(await this.taskApi.completeTask(taskId));
  }

  async cancelTask(taskId: string): Promise<TaskDetail> {
    return taskDetailFromResponse(await this.taskApi.cancelTask(taskId));
  }
}

function toApiFilters(filters: TaskFilterState): TaskListFilters {
  return {
    q: filters.q,
    status: filters.status,
    priority: filters.priority,
    assignment: filters.assignment,
    createdFrom: filters.createdFrom,
    createdTo: filters.createdTo
  };
}
