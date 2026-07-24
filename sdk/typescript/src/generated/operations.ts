/* Auto-generated from docs/api/openapi-v1.yaml. Do not edit manually. */
import type { ApiResponse, HotelOpAiClient } from "../client.js";
import type { paths } from "./schema.js";

type HttpMethodKey = "get" | "put" | "post" | "delete" | "patch" | "head" | "options" | "trace";
type Operation<Path extends keyof paths, Method extends HttpMethodKey> = Method extends keyof paths[Path] ? paths[Path][Method] : never;
type JsonContent<Content> = Content extends { "application/json": infer Json } ? Json : Content extends { "*/*": infer Any } ? Any : unknown;
type RequestBody<Op> = Op extends { requestBody: { content: infer Content } } ? JsonContent<Content> : never;
type PathParams<Op> = Op extends { parameters: { path: infer Params } } ? Params : never;
type QueryParams<Op> = Op extends { parameters: { query?: infer Params } } ? Params : never;
type SuccessResponse<Responses> = 200 extends keyof Responses ? Responses[200] : 201 extends keyof Responses ? Responses[201] : 202 extends keyof Responses ? Responses[202] : 204 extends keyof Responses ? Responses[204] : never;
type ResponseBody<Op> = Op extends { responses: infer Responses } ? SuccessResponse<Responses> extends { content: infer Content } ? JsonContent<Content> : undefined : never;

type AssistantConversationController_startConversationOperation = Operation<"/api/v1/assistant/conversations", "post">;
export type AssistantConversationController_startConversationResponse = ResponseBody<AssistantConversationController_startConversationOperation>;
export type AssistantConversationController_startConversationRequest = RequestBody<AssistantConversationController_startConversationOperation>;
export type AssistantConversationController_startConversationOptions = {
  readonly body: AssistantConversationController_startConversationRequest;
  readonly signal?: AbortSignal;
};
export function AssistantConversationController_startConversation(client: HotelOpAiClient, options: AssistantConversationController_startConversationOptions): Promise<ApiResponse<AssistantConversationController_startConversationResponse>> {
  return client.request<AssistantConversationController_startConversationResponse, AssistantConversationController_startConversationRequest, undefined, undefined>({
    method: "POST",
    path: "/api/v1/assistant/conversations",
    auth: true,
    body: options.body,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type AssistantConversationController_registerAttachmentOperation = Operation<"/api/v1/assistant/conversations/{conversationId}/attachments", "post">;
export type AssistantConversationController_registerAttachmentResponse = ResponseBody<AssistantConversationController_registerAttachmentOperation>;
export type AssistantConversationController_registerAttachmentRequest = RequestBody<AssistantConversationController_registerAttachmentOperation>;
export type AssistantConversationController_registerAttachmentPathParams = PathParams<AssistantConversationController_registerAttachmentOperation>;
export type AssistantConversationController_registerAttachmentOptions = {
  readonly pathParams: AssistantConversationController_registerAttachmentPathParams;
  readonly body: AssistantConversationController_registerAttachmentRequest;
  readonly signal?: AbortSignal;
};
export function AssistantConversationController_registerAttachment(client: HotelOpAiClient, options: AssistantConversationController_registerAttachmentOptions): Promise<ApiResponse<AssistantConversationController_registerAttachmentResponse>> {
  return client.request<AssistantConversationController_registerAttachmentResponse, AssistantConversationController_registerAttachmentRequest, undefined, AssistantConversationController_registerAttachmentPathParams>({
    method: "POST",
    path: "/api/v1/assistant/conversations/{conversationId}/attachments",
    auth: true,
    pathParams: options.pathParams,
    body: options.body,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type AssistantConversationController_confirmTaskOperation = Operation<"/api/v1/assistant/conversations/{conversationId}/confirm", "post">;
export type AssistantConversationController_confirmTaskResponse = ResponseBody<AssistantConversationController_confirmTaskOperation>;
export type AssistantConversationController_confirmTaskRequest = RequestBody<AssistantConversationController_confirmTaskOperation>;
export type AssistantConversationController_confirmTaskPathParams = PathParams<AssistantConversationController_confirmTaskOperation>;
export type AssistantConversationController_confirmTaskOptions = {
  readonly pathParams: AssistantConversationController_confirmTaskPathParams;
  readonly body: AssistantConversationController_confirmTaskRequest;
  readonly signal?: AbortSignal;
};
export function AssistantConversationController_confirmTask(client: HotelOpAiClient, options: AssistantConversationController_confirmTaskOptions): Promise<ApiResponse<AssistantConversationController_confirmTaskResponse>> {
  return client.request<AssistantConversationController_confirmTaskResponse, AssistantConversationController_confirmTaskRequest, undefined, AssistantConversationController_confirmTaskPathParams>({
    method: "POST",
    path: "/api/v1/assistant/conversations/{conversationId}/confirm",
    auth: true,
    pathParams: options.pathParams,
    body: options.body,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type AssistantConversationController_sendMessageOperation = Operation<"/api/v1/assistant/conversations/{conversationId}/messages", "post">;
export type AssistantConversationController_sendMessageResponse = ResponseBody<AssistantConversationController_sendMessageOperation>;
export type AssistantConversationController_sendMessageRequest = RequestBody<AssistantConversationController_sendMessageOperation>;
export type AssistantConversationController_sendMessagePathParams = PathParams<AssistantConversationController_sendMessageOperation>;
export type AssistantConversationController_sendMessageOptions = {
  readonly pathParams: AssistantConversationController_sendMessagePathParams;
  readonly body: AssistantConversationController_sendMessageRequest;
  readonly signal?: AbortSignal;
};
export function AssistantConversationController_sendMessage(client: HotelOpAiClient, options: AssistantConversationController_sendMessageOptions): Promise<ApiResponse<AssistantConversationController_sendMessageResponse>> {
  return client.request<AssistantConversationController_sendMessageResponse, AssistantConversationController_sendMessageRequest, undefined, AssistantConversationController_sendMessagePathParams>({
    method: "POST",
    path: "/api/v1/assistant/conversations/{conversationId}/messages",
    auth: true,
    pathParams: options.pathParams,
    body: options.body,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type AssistantConversationController_resetConversationOperation = Operation<"/api/v1/assistant/conversations/{conversationId}/reset", "post">;
export type AssistantConversationController_resetConversationResponse = ResponseBody<AssistantConversationController_resetConversationOperation>;
export type AssistantConversationController_resetConversationPathParams = PathParams<AssistantConversationController_resetConversationOperation>;
export type AssistantConversationController_resetConversationOptions = {
  readonly pathParams: AssistantConversationController_resetConversationPathParams;
  readonly signal?: AbortSignal;
};
export function AssistantConversationController_resetConversation(client: HotelOpAiClient, options: AssistantConversationController_resetConversationOptions): Promise<ApiResponse<AssistantConversationController_resetConversationResponse>> {
  return client.request<AssistantConversationController_resetConversationResponse, undefined, undefined, AssistantConversationController_resetConversationPathParams>({
    method: "POST",
    path: "/api/v1/assistant/conversations/{conversationId}/reset",
    auth: true,
    pathParams: options.pathParams,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type VisionAnalysisImportController_importVisionAnalysisOperation = Operation<"/api/v1/assistant/conversations/{conversationId}/vision-analyses/{analysisId}/import", "post">;
export type VisionAnalysisImportController_importVisionAnalysisResponse = ResponseBody<VisionAnalysisImportController_importVisionAnalysisOperation>;
export type VisionAnalysisImportController_importVisionAnalysisRequest = RequestBody<VisionAnalysisImportController_importVisionAnalysisOperation>;
export type VisionAnalysisImportController_importVisionAnalysisPathParams = PathParams<VisionAnalysisImportController_importVisionAnalysisOperation>;
export type VisionAnalysisImportController_importVisionAnalysisOptions = {
  readonly pathParams: VisionAnalysisImportController_importVisionAnalysisPathParams;
  readonly body?: VisionAnalysisImportController_importVisionAnalysisRequest;
  readonly signal?: AbortSignal;
};
export function VisionAnalysisImportController_importVisionAnalysis(client: HotelOpAiClient, options: VisionAnalysisImportController_importVisionAnalysisOptions): Promise<ApiResponse<VisionAnalysisImportController_importVisionAnalysisResponse>> {
  return client.request<VisionAnalysisImportController_importVisionAnalysisResponse, VisionAnalysisImportController_importVisionAnalysisRequest, undefined, VisionAnalysisImportController_importVisionAnalysisPathParams>({
    method: "POST",
    path: "/api/v1/assistant/conversations/{conversationId}/vision-analyses/{analysisId}/import",
    auth: true,
    pathParams: options.pathParams,
    ...(options.body !== undefined ? { body: options.body } : {}),
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type AuthController_loginOperation = Operation<"/api/v1/auth/login", "post">;
export type AuthController_loginResponse = ResponseBody<AuthController_loginOperation>;
export type AuthController_loginRequest = RequestBody<AuthController_loginOperation>;
export type AuthController_loginOptions = {
  readonly body: AuthController_loginRequest;
  readonly signal?: AbortSignal;
};
export function AuthController_login(client: HotelOpAiClient, options: AuthController_loginOptions): Promise<ApiResponse<AuthController_loginResponse>> {
  return client.request<AuthController_loginResponse, AuthController_loginRequest, undefined, undefined>({
    method: "POST",
    path: "/api/v1/auth/login",
    auth: false,
    body: options.body,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type AuthController_logoutOperation = Operation<"/api/v1/auth/logout", "post">;
export type AuthController_logoutResponse = ResponseBody<AuthController_logoutOperation>;
export type AuthController_logoutOptions = {
  readonly signal?: AbortSignal;
};
export function AuthController_logout(client: HotelOpAiClient, options: AuthController_logoutOptions = {}): Promise<ApiResponse<AuthController_logoutResponse>> {
  return client.request<AuthController_logoutResponse, undefined, undefined, undefined>({
    method: "POST",
    path: "/api/v1/auth/logout",
    auth: true,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type AuthController_meOperation = Operation<"/api/v1/auth/me", "get">;
export type AuthController_meResponse = ResponseBody<AuthController_meOperation>;
export type AuthController_meOptions = {
  readonly signal?: AbortSignal;
};
export function AuthController_me(client: HotelOpAiClient, options: AuthController_meOptions = {}): Promise<ApiResponse<AuthController_meResponse>> {
  return client.request<AuthController_meResponse, undefined, undefined, undefined>({
    method: "GET",
    path: "/api/v1/auth/me",
    auth: true,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type AuthController_refreshOperation = Operation<"/api/v1/auth/refresh", "post">;
export type AuthController_refreshResponse = ResponseBody<AuthController_refreshOperation>;
export type AuthController_refreshRequest = RequestBody<AuthController_refreshOperation>;
export type AuthController_refreshOptions = {
  readonly body: AuthController_refreshRequest;
  readonly signal?: AbortSignal;
};
export function AuthController_refresh(client: HotelOpAiClient, options: AuthController_refreshOptions): Promise<ApiResponse<AuthController_refreshResponse>> {
  return client.request<AuthController_refreshResponse, AuthController_refreshRequest, undefined, undefined>({
    method: "POST",
    path: "/api/v1/auth/refresh",
    auth: false,
    body: options.body,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type DashboardController_taskReportOperation = Operation<"/api/v1/dashboard/reports/tasks", "get">;
export type DashboardController_taskReportResponse = ResponseBody<DashboardController_taskReportOperation>;
export type DashboardController_taskReportQueryParams = QueryParams<DashboardController_taskReportOperation>;
export type DashboardController_taskReportOptions = {
  readonly query?: DashboardController_taskReportQueryParams;
  readonly signal?: AbortSignal;
};
export function DashboardController_taskReport(client: HotelOpAiClient, options: DashboardController_taskReportOptions = {}): Promise<ApiResponse<DashboardController_taskReportResponse>> {
  return client.request<DashboardController_taskReportResponse, undefined, DashboardController_taskReportQueryParams, undefined>({
    method: "GET",
    path: "/api/v1/dashboard/reports/tasks",
    auth: true,
    ...(options.query !== undefined ? { query: options.query } : {}),
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type DashboardController_summaryOperation = Operation<"/api/v1/dashboard/summary", "get">;
export type DashboardController_summaryResponse = ResponseBody<DashboardController_summaryOperation>;
export type DashboardController_summaryQueryParams = QueryParams<DashboardController_summaryOperation>;
export type DashboardController_summaryOptions = {
  readonly query?: DashboardController_summaryQueryParams;
  readonly signal?: AbortSignal;
};
export function DashboardController_summary(client: HotelOpAiClient, options: DashboardController_summaryOptions = {}): Promise<ApiResponse<DashboardController_summaryResponse>> {
  return client.request<DashboardController_summaryResponse, undefined, DashboardController_summaryQueryParams, undefined>({
    method: "GET",
    path: "/api/v1/dashboard/summary",
    auth: true,
    ...(options.query !== undefined ? { query: options.query } : {}),
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type NotificationController_listNotificationsOperation = Operation<"/api/v1/notifications", "get">;
export type NotificationController_listNotificationsResponse = ResponseBody<NotificationController_listNotificationsOperation>;
export type NotificationController_listNotificationsOptions = {
  readonly signal?: AbortSignal;
};
export function NotificationController_listNotifications(client: HotelOpAiClient, options: NotificationController_listNotificationsOptions = {}): Promise<ApiResponse<NotificationController_listNotificationsResponse>> {
  return client.request<NotificationController_listNotificationsResponse, undefined, undefined, undefined>({
    method: "GET",
    path: "/api/v1/notifications",
    auth: true,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type NotificationController_markReadOperation = Operation<"/api/v1/notifications/{notificationId}/read", "post">;
export type NotificationController_markReadResponse = ResponseBody<NotificationController_markReadOperation>;
export type NotificationController_markReadPathParams = PathParams<NotificationController_markReadOperation>;
export type NotificationController_markReadOptions = {
  readonly pathParams: NotificationController_markReadPathParams;
  readonly signal?: AbortSignal;
};
export function NotificationController_markRead(client: HotelOpAiClient, options: NotificationController_markReadOptions): Promise<ApiResponse<NotificationController_markReadResponse>> {
  return client.request<NotificationController_markReadResponse, undefined, undefined, NotificationController_markReadPathParams>({
    method: "POST",
    path: "/api/v1/notifications/{notificationId}/read",
    auth: true,
    pathParams: options.pathParams,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type TaskController_listTasksOperation = Operation<"/api/v1/tasks", "get">;
export type TaskController_listTasksResponse = ResponseBody<TaskController_listTasksOperation>;
export type TaskController_listTasksQueryParams = QueryParams<TaskController_listTasksOperation>;
export type TaskController_listTasksOptions = {
  readonly query?: TaskController_listTasksQueryParams;
  readonly signal?: AbortSignal;
};
export function TaskController_listTasks(client: HotelOpAiClient, options: TaskController_listTasksOptions = {}): Promise<ApiResponse<TaskController_listTasksResponse>> {
  return client.request<TaskController_listTasksResponse, undefined, TaskController_listTasksQueryParams, undefined>({
    method: "GET",
    path: "/api/v1/tasks",
    auth: true,
    ...(options.query !== undefined ? { query: options.query } : {}),
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type TaskController_createTaskOperation = Operation<"/api/v1/tasks", "post">;
export type TaskController_createTaskResponse = ResponseBody<TaskController_createTaskOperation>;
export type TaskController_createTaskRequest = RequestBody<TaskController_createTaskOperation>;
export type TaskController_createTaskOptions = {
  readonly body: TaskController_createTaskRequest;
  readonly signal?: AbortSignal;
};
export function TaskController_createTask(client: HotelOpAiClient, options: TaskController_createTaskOptions): Promise<ApiResponse<TaskController_createTaskResponse>> {
  return client.request<TaskController_createTaskResponse, TaskController_createTaskRequest, undefined, undefined>({
    method: "POST",
    path: "/api/v1/tasks",
    auth: true,
    body: options.body,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type TaskController_getTaskOperation = Operation<"/api/v1/tasks/{taskId}", "get">;
export type TaskController_getTaskResponse = ResponseBody<TaskController_getTaskOperation>;
export type TaskController_getTaskPathParams = PathParams<TaskController_getTaskOperation>;
export type TaskController_getTaskOptions = {
  readonly pathParams: TaskController_getTaskPathParams;
  readonly signal?: AbortSignal;
};
export function TaskController_getTask(client: HotelOpAiClient, options: TaskController_getTaskOptions): Promise<ApiResponse<TaskController_getTaskResponse>> {
  return client.request<TaskController_getTaskResponse, undefined, undefined, TaskController_getTaskPathParams>({
    method: "GET",
    path: "/api/v1/tasks/{taskId}",
    auth: true,
    pathParams: options.pathParams,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type TaskController_assignTaskOperation = Operation<"/api/v1/tasks/{taskId}/assign", "post">;
export type TaskController_assignTaskResponse = ResponseBody<TaskController_assignTaskOperation>;
export type TaskController_assignTaskRequest = RequestBody<TaskController_assignTaskOperation>;
export type TaskController_assignTaskPathParams = PathParams<TaskController_assignTaskOperation>;
export type TaskController_assignTaskOptions = {
  readonly pathParams: TaskController_assignTaskPathParams;
  readonly body: TaskController_assignTaskRequest;
  readonly signal?: AbortSignal;
};
export function TaskController_assignTask(client: HotelOpAiClient, options: TaskController_assignTaskOptions): Promise<ApiResponse<TaskController_assignTaskResponse>> {
  return client.request<TaskController_assignTaskResponse, TaskController_assignTaskRequest, undefined, TaskController_assignTaskPathParams>({
    method: "POST",
    path: "/api/v1/tasks/{taskId}/assign",
    auth: true,
    pathParams: options.pathParams,
    body: options.body,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type TaskController_getTaskAttachmentsOperation = Operation<"/api/v1/tasks/{taskId}/attachments", "get">;
export type TaskController_getTaskAttachmentsResponse = ResponseBody<TaskController_getTaskAttachmentsOperation>;
export type TaskController_getTaskAttachmentsPathParams = PathParams<TaskController_getTaskAttachmentsOperation>;
export type TaskController_getTaskAttachmentsOptions = {
  readonly pathParams: TaskController_getTaskAttachmentsPathParams;
  readonly signal?: AbortSignal;
};
export function TaskController_getTaskAttachments(client: HotelOpAiClient, options: TaskController_getTaskAttachmentsOptions): Promise<ApiResponse<TaskController_getTaskAttachmentsResponse>> {
  return client.request<TaskController_getTaskAttachmentsResponse, undefined, undefined, TaskController_getTaskAttachmentsPathParams>({
    method: "GET",
    path: "/api/v1/tasks/{taskId}/attachments",
    auth: true,
    pathParams: options.pathParams,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type TaskController_cancelTaskOperation = Operation<"/api/v1/tasks/{taskId}/cancel", "post">;
export type TaskController_cancelTaskResponse = ResponseBody<TaskController_cancelTaskOperation>;
export type TaskController_cancelTaskPathParams = PathParams<TaskController_cancelTaskOperation>;
export type TaskController_cancelTaskOptions = {
  readonly pathParams: TaskController_cancelTaskPathParams;
  readonly signal?: AbortSignal;
};
export function TaskController_cancelTask(client: HotelOpAiClient, options: TaskController_cancelTaskOptions): Promise<ApiResponse<TaskController_cancelTaskResponse>> {
  return client.request<TaskController_cancelTaskResponse, undefined, undefined, TaskController_cancelTaskPathParams>({
    method: "POST",
    path: "/api/v1/tasks/{taskId}/cancel",
    auth: true,
    pathParams: options.pathParams,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type TaskController_completeTaskOperation = Operation<"/api/v1/tasks/{taskId}/complete", "post">;
export type TaskController_completeTaskResponse = ResponseBody<TaskController_completeTaskOperation>;
export type TaskController_completeTaskPathParams = PathParams<TaskController_completeTaskOperation>;
export type TaskController_completeTaskOptions = {
  readonly pathParams: TaskController_completeTaskPathParams;
  readonly signal?: AbortSignal;
};
export function TaskController_completeTask(client: HotelOpAiClient, options: TaskController_completeTaskOptions): Promise<ApiResponse<TaskController_completeTaskResponse>> {
  return client.request<TaskController_completeTaskResponse, undefined, undefined, TaskController_completeTaskPathParams>({
    method: "POST",
    path: "/api/v1/tasks/{taskId}/complete",
    auth: true,
    pathParams: options.pathParams,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type TaskController_overdueTaskOperation = Operation<"/api/v1/tasks/{taskId}/overdue", "post">;
export type TaskController_overdueTaskResponse = ResponseBody<TaskController_overdueTaskOperation>;
export type TaskController_overdueTaskPathParams = PathParams<TaskController_overdueTaskOperation>;
export type TaskController_overdueTaskOptions = {
  readonly pathParams: TaskController_overdueTaskPathParams;
  readonly signal?: AbortSignal;
};
export function TaskController_overdueTask(client: HotelOpAiClient, options: TaskController_overdueTaskOptions): Promise<ApiResponse<TaskController_overdueTaskResponse>> {
  return client.request<TaskController_overdueTaskResponse, undefined, undefined, TaskController_overdueTaskPathParams>({
    method: "POST",
    path: "/api/v1/tasks/{taskId}/overdue",
    auth: true,
    pathParams: options.pathParams,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type TaskController_pauseTaskOperation = Operation<"/api/v1/tasks/{taskId}/pause", "post">;
export type TaskController_pauseTaskResponse = ResponseBody<TaskController_pauseTaskOperation>;
export type TaskController_pauseTaskPathParams = PathParams<TaskController_pauseTaskOperation>;
export type TaskController_pauseTaskOptions = {
  readonly pathParams: TaskController_pauseTaskPathParams;
  readonly signal?: AbortSignal;
};
export function TaskController_pauseTask(client: HotelOpAiClient, options: TaskController_pauseTaskOptions): Promise<ApiResponse<TaskController_pauseTaskResponse>> {
  return client.request<TaskController_pauseTaskResponse, undefined, undefined, TaskController_pauseTaskPathParams>({
    method: "POST",
    path: "/api/v1/tasks/{taskId}/pause",
    auth: true,
    pathParams: options.pathParams,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type TaskController_resumeTaskOperation = Operation<"/api/v1/tasks/{taskId}/resume", "post">;
export type TaskController_resumeTaskResponse = ResponseBody<TaskController_resumeTaskOperation>;
export type TaskController_resumeTaskPathParams = PathParams<TaskController_resumeTaskOperation>;
export type TaskController_resumeTaskOptions = {
  readonly pathParams: TaskController_resumeTaskPathParams;
  readonly signal?: AbortSignal;
};
export function TaskController_resumeTask(client: HotelOpAiClient, options: TaskController_resumeTaskOptions): Promise<ApiResponse<TaskController_resumeTaskResponse>> {
  return client.request<TaskController_resumeTaskResponse, undefined, undefined, TaskController_resumeTaskPathParams>({
    method: "POST",
    path: "/api/v1/tasks/{taskId}/resume",
    auth: true,
    pathParams: options.pathParams,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}

type TaskController_startTaskOperation = Operation<"/api/v1/tasks/{taskId}/start", "post">;
export type TaskController_startTaskResponse = ResponseBody<TaskController_startTaskOperation>;
export type TaskController_startTaskPathParams = PathParams<TaskController_startTaskOperation>;
export type TaskController_startTaskOptions = {
  readonly pathParams: TaskController_startTaskPathParams;
  readonly signal?: AbortSignal;
};
export function TaskController_startTask(client: HotelOpAiClient, options: TaskController_startTaskOptions): Promise<ApiResponse<TaskController_startTaskResponse>> {
  return client.request<TaskController_startTaskResponse, undefined, undefined, TaskController_startTaskPathParams>({
    method: "POST",
    path: "/api/v1/tasks/{taskId}/start",
    auth: true,
    pathParams: options.pathParams,
    ...(options.signal !== undefined ? { signal: options.signal } : {})
  });
}
