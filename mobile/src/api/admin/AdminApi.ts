import { MobileHotelOpAiClient } from "../hotelOpAiClient";

export type HotelDto = {
  id: string;
  code: string;
  name: string;
  timezone: string;
  address: string | null;
  active: boolean;
};
export type NamedDto = {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  active: boolean;
};
export type BuildingDto = NamedDto;
export type FloorDto = {
  id: string;
  buildingId: string;
  floorNumber: number;
  name: string | null;
  active: boolean;
};
export type RoomDto = {
  id: string;
  buildingId: string;
  floorId: string;
  roomNumber: string;
  roomType: string | null;
  active: boolean;
};
export type RoomPageDto = {
  items: RoomDto[];
  page: number;
  size: number;
  totalItems: number;
};
export type MembershipDto = {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  departmentId: string | null;
  active: boolean;
};
export type MembershipDetailDto = {
  membership: MembershipDto;
  roles: NamedDto[];
  skills: (NamedDto & { skillLevel: string | null })[];
  shifts: ShiftAssignmentDto[];
};
export type ShiftDto = {
  id: string;
  code: string;
  name: string;
  startTime: string;
  endTime: string;
  active: boolean;
  crossesMidnight: boolean;
};
export type ShiftAssignmentDto = {
  id: string;
  membershipId: string;
  shiftId: string;
  shiftDate: string;
  active: boolean;
};
export type PermissionDto = {
  id: string;
  code: string;
  name: string;
  assigned: boolean;
};
export type HotelAccessDto = {roleCodes:string[];permissionCodes:string[]};
export type RoomImportResultDto = {
  processed: number;
  imported: number;
  duplicates: number;
  invalid: number;
  rows: { row: number; status: string; message?: string }[];
};
export type OnboardingPayload = {
  code: string;
  name: string;
  timezone: string;
  address?: string;
  departments: { code: string; name: string }[];
  buildings: { code: string; name: string; floors: number[] }[];
  rooms: {
    buildingCode: string;
    floorNumber: number;
    roomNumber: string;
    roomType?: string;
  }[];
  skills: { code: string; name: string; description?: string }[];
  shifts: { code: string; name: string; startTime: string; endTime: string }[];
  administratorUserId: string;
};

export class AdminApi {
  private readonly client: MobileHotelOpAiClient;
  constructor(client: MobileHotelOpAiClient) { this.client=client; }
  private call<T>(
    method: "GET" | "POST" | "PUT",
    path: string,
    body?: unknown,
    query?: Record<string, unknown>,
  ): Promise<T> {
    return this.client.call(method, (sdk, signal) =>
      sdk.request<T>({ method, path, auth: true, body, query, signal }),
    );
  }
  hotels() {
    return this.call<HotelDto[]>("GET", "/api/v1/internal/admin/hotels");
  }
  access(hotelId:string){return this.call<HotelAccessDto>("GET",`/api/v1/internal/admin/hotels/${hotelId}/access`)}
  createHotel(body: {
    code: string;
    name: string;
    timezone: string;
    address?: string;
  }) {
    return this.call<HotelDto>("POST", "/api/v1/internal/admin/hotels", body);
  }
  onboard(body: OnboardingPayload) {
    return this.call<{ hotel: HotelDto }>(
      "POST",
      "/api/v1/internal/admin/hotels/onboard",
      body,
    );
  }
  list<T>(hotelId: string, resource: string, query?: Record<string, unknown>) {
    return this.call<T>(
      "GET",
      `/api/v1/internal/admin/hotels/${hotelId}/${resource}`,
      undefined,
      query,
    );
  }
  create<T>(hotelId: string, resource: string, body: unknown) {
    return this.call<T>(
      "POST",
      `/api/v1/internal/admin/hotels/${hotelId}/${resource}`,
      body,
    );
  }
  update<T>(hotelId: string, resource: string, id: string, body: unknown) {
    return this.call<T>(
      "PUT",
      `/api/v1/internal/admin/hotels/${hotelId}/${resource}/${id}`,
      body,
    );
  }
  membership(hotelId: string, id: string) {
    return this.call<MembershipDetailDto>(
      "GET",
      `/api/v1/internal/admin/hotels/${hotelId}/memberships/${id}`,
    );
  }
  updateMembership(
    hotelId: string,
    id: string,
    body: {
      displayName?: string;
      departmentId: string | null;
      active: boolean;
    },
  ) {
    return this.call<MembershipDetailDto>(
      "PUT",
      `/api/v1/internal/admin/hotels/${hotelId}/memberships/${id}`,
      body,
    );
  }
  replaceMembershipRoles(hotelId: string, id: string, roleIds: string[]) {
    return this.call<MembershipDetailDto>(
      "PUT",
      `/api/v1/internal/admin/hotels/${hotelId}/memberships/${id}/roles`,
      { roleIds },
    );
  }
  replaceMembershipSkills(
    hotelId: string,
    id: string,
    skills: Record<string, string | null>,
  ) {
    return this.call<MembershipDetailDto>(
      "PUT",
      `/api/v1/internal/admin/hotels/${hotelId}/memberships/${id}/skills`,
      { skills },
    );
  }
  rolePermissions(hotelId: string, roleId: string) {
    return this.call<PermissionDto[]>(
      "GET",
      `/api/v1/internal/admin/hotels/${hotelId}/roles/${roleId}/permissions`,
    );
  }
  saveRolePermissions(
    hotelId: string,
    roleId: string,
    permissionIds: string[],
  ) {
    return this.call<void>(
      "PUT",
      `/api/v1/internal/admin/hotels/${hotelId}/roles/${roleId}/permissions`,
      { permissionIds },
    );
  }
  importRooms(hotelId: string, csv: string) {
    return this.call<RoomImportResultDto>(
      "POST",
      `/api/v1/internal/admin/hotels/${hotelId}/rooms/import`,
      csv,
    );
  }
}
