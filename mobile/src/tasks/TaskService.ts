import { appApiBaseUrl } from "../config/appConfig";
import { FetchApiClient } from "../api/client/FetchApiClient";
import { HttpTaskApi } from "../api/task/TaskApi";
import { TaskDetail, taskDetailFromResponse, taskSummaryFromResponse, TaskSummary } from "./types";

export class TaskService {
  private readonly taskApi: HttpTaskApi;

  constructor(accessTokenProvider: () => string | null) {
    this.taskApi = new HttpTaskApi(
      new FetchApiClient({
        baseUrl: appApiBaseUrl,
        accessTokenProvider
      })
    );
  }

  async listTasks(): Promise<TaskSummary[]> {
    const tasks = await this.taskApi.listTasks();
    return tasks.map(taskSummaryFromResponse);
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
