import { appApiBaseUrl } from "../config/appConfig";
import { FetchApiClient } from "../api/client/FetchApiClient";
import { HttpTaskApi, TaskListFilters } from "../api/task/TaskApi";
import { TaskDetail, TaskFilterState, taskDetailFromResponse, taskSummariesFromListResponse, TaskSummary } from "./types";

export class TaskService {
  private readonly taskApi: HttpTaskApi;

  constructor(accessTokenProvider: () => string | null, refreshAccessToken?: () => Promise<string | null>) {
    this.taskApi = new HttpTaskApi(
      new FetchApiClient({
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
    return taskDetailFromResponse(await this.taskApi.getTask(taskId));
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
