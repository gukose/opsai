import {
  DashboardController_summary,
  DashboardController_taskReport
} from "@hotelopai/api-client";
import { MobileHotelOpAiClient } from "../hotelOpAiClient";
import type { DashboardSummaryDto, TaskReportingDto } from "./DashboardDtos";

export type DashboardRange = "today" | "shift" | "7d";

export interface DashboardApi {
  getSummary(range?: DashboardRange): Promise<DashboardSummaryDto>;
  getTaskReport(range?: DashboardRange): Promise<TaskReportingDto>;
}

export class HttpDashboardApi implements DashboardApi {
  private readonly client: MobileHotelOpAiClient;

  constructor(client: MobileHotelOpAiClient) {
    this.client = client;
  }

  getSummary(range: DashboardRange = "today"): Promise<DashboardSummaryDto> {
    return this.client.call("GET", (sdk, signal) => DashboardController_summary(sdk, { query: { range }, signal }));
  }

  getTaskReport(range: DashboardRange = "today"): Promise<TaskReportingDto> {
    return this.client.call("GET", (sdk, signal) => DashboardController_taskReport(sdk, { query: { range }, signal }));
  }
}
