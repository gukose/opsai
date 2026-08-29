import { MobileHotelOpAiClient } from "../hotelOpAiClient";

export type InspectionAnswer = { checklistItemId: string; passed: boolean; note?: string | null };
export type InspectionItem = { id: string; code: string; label?: string | null; required?: boolean; mandatory?: boolean };
export type InspectionTemplate = { id: string; name?: string; items: InspectionItem[] };
export type InspectionWorkflow = { id: string; taskId: string; roomNumber: string; status: string; type?: string; inspectionRequired?: boolean; updatedAt?: string; createdAt?: string };
export type InspectionHistory = { id: string; attempt: number; result: string; qualityScore?: number | null; rejectionReason?: string | null; inspectorUserId: string; inspectedAt?: string };
export type InspectionDetail = { workflow: InspectionWorkflow; task?: { assignment?: { displayName?: string | null } | null } | null; template?: InspectionTemplate | null; history: InspectionHistory[] };

export class InspectionApi {
  constructor(private readonly client: MobileHotelOpAiClient) {}
  private call<T>(method: "GET" | "POST", path: string, body?: unknown) {
    return this.client.call(method, (sdk, signal) => sdk.request<T>({ method, path, auth: true, body, signal }));
  }
  pending() { return this.call<InspectionWorkflow[]>("GET", "/api/v1/internal/housekeeping/inspections/pending"); }
  detail(id: string) { return this.call<InspectionDetail>("GET", `/api/v1/internal/housekeeping/${id}/inspection`); }
  decide(id: string, result: "PASS" | "REJECT", answers: InspectionAnswer[], rejectionReason?: string) {
    return this.call<InspectionWorkflow>("POST", `/api/v1/internal/housekeeping/${id}/inspect`, { result, answers, rejectionReason });
  }
}
