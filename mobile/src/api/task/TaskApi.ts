import { ApiClient } from "../client/ApiClient";
import { TaskResponseDto } from "./TaskDtos";

export interface TaskApi {
  listTasks(): Promise<TaskResponseDto[]>;
  getTask(taskId: string): Promise<TaskResponseDto>;
  startTask(taskId: string): Promise<TaskResponseDto>;
  pauseTask(taskId: string): Promise<TaskResponseDto>;
  resumeTask(taskId: string): Promise<TaskResponseDto>;
  completeTask(taskId: string): Promise<TaskResponseDto>;
  cancelTask(taskId: string): Promise<TaskResponseDto>;
}

export class HttpTaskApi implements TaskApi {
  constructor(private readonly client: ApiClient) {}

  listTasks(): Promise<TaskResponseDto[]> {
    return this.client.get("/api/v1/tasks");
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
