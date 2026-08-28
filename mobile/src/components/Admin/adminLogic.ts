export const ADMIN_VIEW_PERMISSIONS = [
  "HOTEL_VIEW",
  "BUILDING_VIEW",
  "FLOOR_VIEW",
  "ROOM_VIEW",
  "DEPARTMENT_VIEW",
  "USER_VIEW",
  "ROLE_VIEW",
  "SKILL_VIEW",
  "SHIFT_VIEW",
] as const;
export const ONBOARDING_STEPS = [
  "Hotel",
  "Departments",
  "Buildings / Floors",
  "Rooms",
  "Roles & Permissions",
  "Skills",
  "Employees",
  "Shifts",
  "Review & Create",
] as const;
export type DraftDepartment = { code: string; name: string };
export type DraftBuilding = { code: string; name: string; floors: number[] };
export type DraftRoom = {
  buildingCode: string;
  floorNumber: number;
  roomNumber: string;
  roomType: string;
};
export type DraftSkill = { code: string; name: string; description: string };
export type DraftShift = {
  code: string;
  name: string;
  startTime: string;
  endTime: string;
};
export type OnboardingDraft = {
  code: string;
  name: string;
  timezone: string;
  address: string;
  departments: DraftDepartment[];
  buildings: DraftBuilding[];
  rooms: DraftRoom[];
  skills: DraftSkill[];
  shifts: DraftShift[];
  administratorUserId: string;
};
export const emptyOnboardingDraft = (
  administratorUserId = "",
): OnboardingDraft => ({
  code: "",
  name: "",
  timezone: "Europe/Berlin",
  address: "",
  departments: [],
  buildings: [],
  rooms: [],
  skills: [],
  shifts: [],
  administratorUserId,
});

export function canOpenAdministration(permissions: readonly string[]): boolean {
  const normalized = new Set(permissions.map((p) => p.trim().toUpperCase()));
  return ADMIN_VIEW_PERMISSIONS.some((p) => normalized.has(p));
}
export function validateOnboardingStep(
  step: number,
  draft: OnboardingDraft,
): string[] {
  switch (step) {
    case 0:
      return [
        !draft.name.trim() && "Hotel name is required",
        !draft.code.trim().match(/^[A-Z0-9_]{2,32}$/) &&
          "Hotel code must use 2–32 uppercase letters, numbers, or underscores",
        !draft.timezone.trim() && "Timezone is required",
      ].filter(Boolean) as string[];
    case 1:
      return draft.departments.length?duplicateCodes(draft.departments, "department"):["Add at least one department"];
    case 2:
      return [
        ...(draft.buildings.length?[]:["Add at least one building"]),
        ...duplicateCodes(draft.buildings, "building"),
        ...draft.buildings.flatMap((b) =>
          b.floors.length === 0
            ? [`${b.code || "Building"} needs at least one floor`]
            : [],
        ),
      ];
    case 3:
      return [
        ...(draft.rooms.length?[]:["Add at least one room"]),
        ...duplicateValues(
          draft.rooms.map((r) => r.roomNumber),
          "room number",
        ),
        ...draft.rooms.flatMap((r) =>
          draft.buildings.some(
            (b) =>
              b.code === r.buildingCode && b.floors.includes(r.floorNumber),
          )
            ? []
            : [
                `Room ${r.roomNumber || "?"} references an unknown building/floor`,
              ],
        ),
      ];
    case 5:
      return duplicateCodes(draft.skills, "skill");
    case 6:
      return draft.administratorUserId
        ? []
        : ["An initial hotel administrator is required"];
    case 7:
      return duplicateCodes(draft.shifts, "shift");
    default:
      return [];
  }
}
function duplicateCodes(items: { code: string }[], label: string) {
  return [
    ...new Set(
      items
        .map((i) => i.code.trim().toUpperCase())
        .filter((v, i, a) => !v || a.indexOf(v) !== i),
    ),
  ].map((v) =>
    v
      ? `Duplicate ${label} code: ${v}`
      : `${label.charAt(0).toUpperCase() + label.slice(1)} code is required`,
  );
}
function duplicateValues(values: string[], label: string) {
  return [
    ...new Set(
      values.map((v) => v.trim()).filter((v, i, a) => !v || a.indexOf(v) !== i),
    ),
  ].map((v) =>
    v
      ? `Duplicate ${label}: ${v}`
      : `${label.charAt(0).toUpperCase() + label.slice(1)} is required`,
  );
}

export type PermissionItem = {
  id: string;
  code: string;
  name: string;
  assigned: boolean;
};
export type PermissionGroup = {
  name: string;
  platform: boolean;
  items: PermissionItem[];
};
export function groupPermissions(items: PermissionItem[]): PermissionGroup[] {
  const groups = new Map<string, PermissionItem[]>();
  for (const item of items) {
    const name = permissionGroup(item.code);
    groups.set(name, [...(groups.get(name) ?? []), item]);
  }
  return [...groups.entries()]
    .map(([name, group]) => ({
      name,
      platform: name === "Platform",
      items: group.sort((a, b) => a.code.localeCompare(b.code)),
    }))
    .sort(
      (a, b) =>
        Number(b.platform) - Number(a.platform) || a.name.localeCompare(b.name),
    );
}
function permissionGroup(code: string): string {
  if (code.startsWith("PLATFORM_")) return "Platform";
  if (code.startsWith("TASK_")) return "Tasks";
  if (code.startsWith("HOUSEKEEPING_")) return "Housekeeping";
  if (
    code.startsWith("INVENTORY_") ||
    code.startsWith("MINIBAR_") ||
    code.startsWith("DAMAGE_")
  )
    return "Inventory & Damage";
  if (
    code.startsWith("USER_") ||
    code.startsWith("EMPLOYEE_") ||
    code.startsWith("SHIFT_")
  )
    return "Employees & Shifts";
  if (
    code.startsWith("HOTEL_") ||
    code.startsWith("BUILDING_") ||
    code.startsWith("FLOOR_") ||
    code.startsWith("ROOM_") ||
    code.startsWith("DEPARTMENT_") ||
    code.startsWith("SKILL_")
  )
    return "Hotel Master Data";
  if (code.startsWith("ROLE_") || code.startsWith("AUTH_"))
    return "Administration";
  if (code.startsWith("PMS_") || code.startsWith("RESERVATION_"))
    return "PMS & Reservations";
  return (code.split("_")[0] ?? "Other").replace(/\b\w/g, (c) =>
    c.toUpperCase(),
  );
}

export type CsvPreviewRow = {
  line: number;
  building: string;
  floor: string;
  roomNumber: string;
  roomType: string;
  valid: boolean;
  errors: string[];
};
export type CsvPreview = {
  validHeader: boolean;
  rows: CsvPreviewRow[];
  validCount: number;
  invalidCount: number;
};
export function parseRoomCsv(csv: string): CsvPreview {
  const lines = csv.split(/\r?\n/).filter((line) => line.trim());
  const header = (lines.shift() ?? "")
    .split(",")
    .map((v) => v.trim().toLowerCase());
  const validHeader = header.join(",") === "building,floor,roomnumber,roomtype";
  const rows = lines.map((line, index) => {
    const cells = parseCsvLine(line);
    const errors: string[] = [];
    if (cells.length !== 4) errors.push("Expected 4 columns");
    if (!cells[0]?.trim()) errors.push("Building is required");
    if (!/^-?\d+$/.test(cells[1]?.trim() ?? ""))
      errors.push("Floor must be a number");
    if (!cells[2]?.trim()) errors.push("Room number is required");
    return {
      line: index + 2,
      building: cells[0]?.trim() ?? "",
      floor: cells[1]?.trim() ?? "",
      roomNumber: cells[2]?.trim() ?? "",
      roomType: cells[3]?.trim() ?? "",
      valid: validHeader && errors.length === 0,
      errors: validHeader ? errors : ["Invalid CSV header"],
    };
  });
  return {
    validHeader,
    rows,
    validCount: rows.filter((r) => r.valid).length,
    invalidCount: rows.filter((r) => !r.valid).length,
  };
}
function parseCsvLine(line: string): string[] {
  const values: string[] = [];
  let value = "",
    quoted = false;
  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    if (char === '"') {
      if (quoted && line[i + 1] === '"') {
        value += '"';
        i++;
      } else quoted = !quoted;
    } else if (char === "," && !quoted) {
      values.push(value);
      value = "";
    } else value += char;
  }
  values.push(value);
  return values;
}
export function csvPreviewRowCount(csv: string): number {
  return parseRoomCsv(csv).rows.length;
}
export function availableFloors<T extends { buildingId: string }>(
  floors: T[],
  buildingId: string,
): T[] {
  return floors.filter((f) => f.buildingId === buildingId);
}
export function shouldApplyHotelResponse(requestGeneration:number,currentGeneration:number):boolean{return requestGeneration===currentGeneration}
export function canConfirmCsvImport(preview:CsvPreview):boolean{return preview.validHeader&&preview.rows.length>0&&preview.invalidCount===0}
export function selectCsvFile(fileName:string,csv:string){return{fileName,csv,preview:parseRoomCsv(csv),confirmed:false as const}}
