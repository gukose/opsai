import type { ApiClient } from "../client/ApiClient";
import type { DashboardSummaryDto, TaskReportingDto } from "./DashboardDtos";

export type DashboardRange = "today" | "shift" | "7d";

export interface DashboardApi {
  getSummary(range?: DashboardRange): Promise<DashboardSummaryDto>;
  getTaskReport(range?: DashboardRange): Promise<TaskReportingDto>;
}

export class HttpDashboardApi implements DashboardApi {
  private readonly client: ApiClient;

  constructor(client: ApiClient) {
    this.client = client;
  }

  getSummary(range: DashboardRange = "today"): Promise<DashboardSummaryDto> {
    return this.client.get(`/api/v1/dashboard/summary?range=${encodeURIComponent(range)}`);
  }

  getTaskReport(range: DashboardRange = "today"): Promise<TaskReportingDto> {
    return this.client.get(`/api/v1/dashboard/reports/tasks?range=${encodeURIComponent(range)}`);
  }
}
