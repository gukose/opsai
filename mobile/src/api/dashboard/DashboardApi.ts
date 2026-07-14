import { ApiClient } from "../client/ApiClient";
import { DashboardSummaryDto } from "./DashboardDtos";

export type DashboardRange = "today" | "shift" | "7d";

export interface DashboardApi {
  getSummary(range?: DashboardRange): Promise<DashboardSummaryDto>;
}

export class HttpDashboardApi implements DashboardApi {
  constructor(private readonly client: ApiClient) {}

  getSummary(range: DashboardRange = "today"): Promise<DashboardSummaryDto> {
    return this.client.get(`/api/v1/dashboard/summary?range=${encodeURIComponent(range)}`);
  }
}
