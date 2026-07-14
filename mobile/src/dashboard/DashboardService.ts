import { FetchApiClient } from "../api/client/FetchApiClient";
import { HttpDashboardApi } from "../api/dashboard/DashboardApi";
import { appApiBaseUrl } from "../config/appConfig";
import { DashboardSummary, TaskReportingSummary, dashboardSummaryFromResponse, taskReportingFromResponse } from "./types";

export class DashboardService {
  private readonly dashboardApi: HttpDashboardApi;

  constructor(accessTokenProvider: () => string | null) {
    this.dashboardApi = new HttpDashboardApi(
      new FetchApiClient({
        baseUrl: appApiBaseUrl,
        accessTokenProvider
      })
    );
  }

  async getSummary(): Promise<DashboardSummary> {
    return dashboardSummaryFromResponse(await this.dashboardApi.getSummary("today"));
  }

  async getTaskReport(range: "today" | "shift" | "7d" = "today"): Promise<TaskReportingSummary> {
    return taskReportingFromResponse(await this.dashboardApi.getTaskReport(range));
  }
}
