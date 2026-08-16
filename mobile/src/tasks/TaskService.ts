import { appApiBaseUrl } from "../config/appConfig";
import { HttpTaskApi, TaskListFilters } from "../api/task/TaskApi";
import { MobileHotelOpAiClient } from "../api/hotelOpAiClient";
import {
  TaskDetail,
  AssignmentCandidate,
  TaskFilterState,
  taskAttachmentFromResponse,
  taskDetailFromResponse,
  taskSummariesFromListResponse,
  TaskSummary
} from "./types";

export class TaskService {
  private readonly taskApi: HttpTaskApi;
  private readonly accessTokenProvider: () => string | null;

  constructor(accessTokenProvider: () => string | null, refreshAccessToken?: () => Promise<string | null>) {
    this.accessTokenProvider = accessTokenProvider;
    this.taskApi = new HttpTaskApi(
      new MobileHotelOpAiClient({
        baseUrl: appApiBaseUrl,
        accessTokenProvider,
        refreshAccessToken
      })
    );
  }

  async submitOfflineOperation(operation:{clientOperationId:string;type:string;resourceId:string}):Promise<void>{
    const token=this.accessTokenProvider();if(!token)throw new Error("Authentication required");
    const response=await fetch(`${appApiBaseUrl}/api/v1/internal/offline-operations`,{method:"POST",headers:{Authorization:`Bearer ${token}`,"Content-Type":"application/json"},body:JSON.stringify(operation)});
    if(!response.ok){const error=new Error(`Offline sync failed with ${response.status}`) as Error & {status?:number};error.status=response.status;throw error}
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

  async assignmentCandidates(taskId: string): Promise<AssignmentCandidate[]> {
    return this.authorizedJson<AssignmentCandidate[]>(`/api/v1/tasks/${taskId}/assignment-candidates`, { method: "GET" });
  }

  async assignTask(taskId: string, candidate: AssignmentCandidate): Promise<TaskDetail> {
    const response = await this.authorizedJson<import("../api/task/TaskDtos").TaskResponseDto>(
      `/api/v1/tasks/${taskId}/assign`,
      {
        method: "POST",
        body: JSON.stringify({ assigneeType: "USER", assigneeId: candidate.assigneeId, displayName: candidate.displayName })
      }
    );
    return taskDetailFromResponse(response);
  }

  private async authorizedJson<T>(path: string, init: RequestInit): Promise<T> {
    const token = this.accessTokenProvider();
    if (!token) throw new Error("Authentication required");
    const response = await fetch(`${appApiBaseUrl}${path}`, {
      ...init,
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", ...(init.headers ?? {}) }
    });
    if (!response.ok) throw new Error(`Assignment request failed with ${response.status}`);
    return response.json() as Promise<T>;
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
