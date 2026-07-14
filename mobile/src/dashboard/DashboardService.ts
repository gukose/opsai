import { FetchApiClient } from "../api/client/FetchApiClient";
import { HttpDashboardApi } from "../api/dashboard/DashboardApi";
import { appApiBaseUrl } from "../config/appConfig";
import { DashboardSummary, dashboardSummaryFromResponse } from "./types";

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
}
