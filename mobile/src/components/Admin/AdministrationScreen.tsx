import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  LayoutChangeEvent,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from "react-native";
import {
  AdminApi,
  BuildingDto,
  FloorDto,
  HotelDto,
  MembershipDto,
  NamedDto,
  RoomDto,
  RoomImportResultDto,
  RoomPageDto,
  ShiftDto,
} from "../../api/admin/AdminApi";
import { createMobileHotelOpAiClient } from "../../api/hotelOpAiClient";
import { hasPermission } from "../../auth/currentUserHelpers";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { colors, radius, spacing, typography } from "../../theme/tokens";
import { availableFloors, shouldApplyHotelResponse } from "./adminLogic";
import { AdminButton, AdminCard, AdminModal, adminStyles } from "./AdminUi";
import { EmployeeEditor } from "./EmployeeEditor";
import { HotelOnboardingWizard } from "./HotelOnboardingWizard";
import { RolePermissionEditor } from "./RolePermissionEditor";
import { RoomCsvImportPanel } from "./RoomCsvImportPanel";
import { adminLayoutForWidth } from "./adminResponsive";

type Section =
  | "hotels"
  | "departments"
  | "buildings"
  | "floors"
  | "rooms"
  | "employees"
  | "roles"
  | "skills"
  | "shifts";
type Data = {
  permissionCodes:string[];
  departments: NamedDto[];
  buildings: BuildingDto[];
  floors: FloorDto[];
  rooms: RoomDto[];
  employees: MembershipDto[];
  roles: NamedDto[];
  skills: NamedDto[];
  shifts: ShiftDto[];
};
const emptyData = (): Data => ({
  permissionCodes:[],
  departments: [],
  buildings: [],
  floors: [],
  rooms: [],
  employees: [],
  roles: [],
  skills: [],
  shifts: [],
});
const sections: Section[] = [
  "hotels",
  "departments",
  "buildings",
  "floors",
  "rooms",
  "employees",
  "roles",
  "skills",
  "shifts",
];
const viewPermission: Record<Section, string> = {
  hotels: "HOTEL_VIEW",
  departments: "DEPARTMENT_VIEW",
  buildings: "BUILDING_VIEW",
  floors: "FLOOR_VIEW",
  rooms: "ROOM_VIEW",
  employees: "USER_VIEW",
  roles: "ROLE_VIEW",
  skills: "SKILL_VIEW",
  shifts: "SHIFT_VIEW",
};
const managePermission: Record<Section, string> = {
  hotels: "PLATFORM_HOTEL_MANAGE",
  departments: "DEPARTMENT_MANAGE",
  buildings: "BUILDING_MANAGE",
  floors: "FLOOR_MANAGE",
  rooms: "ROOM_CREATE",
  employees: "USER_CREATE",
  roles: "ROLE_MANAGE",
  skills: "SKILL_MANAGE",
  shifts: "SHIFT_MANAGE",
};

export function AdministrationScreen({
  accessToken,
  currentUser,
  refreshAccessToken,
}: {
  accessToken: string | null;
  currentUser: CurrentUserSnapshot | null;
  refreshAccessToken?: () => Promise<string | null>;
}) {
  const { width } = useWindowDimensions();
  const layout = adminLayoutForWidth(width);
  const api = useMemo(
    () =>
      new AdminApi(
        createMobileHotelOpAiClient({
          accessTokenProvider: () => accessToken,
          refreshAccessToken,
        }),
      ),
    [accessToken, refreshAccessToken],
  );
  const [hotels, setHotels] = useState<HotelDto[]>([]),
    [hotelId, setHotelId] = useState(currentUser?.hotelId ?? ""),
    [section, setSection] = useState<Section>("hotels"),
    [data, setData] = useState<Data>(emptyData),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<string | null>(null),
    [wizard, setWizard] = useState(false),
    [addOpen, setAddOpen] = useState(false),
    [selectedRole, setSelectedRole] = useState<NamedDto | null>(null),
    [selectedEmployee, setSelectedEmployee] = useState<MembershipDto | null>(
      null,
    ),
    [selectedRoom, setSelectedRoom] = useState<RoomDto | null>(null);
  const [search, setSearch] = useState(""),
    [buildingFilter, setBuildingFilter] = useState(""),
    [floorFilter, setFloorFilter] = useState("");
  const requestRef = useRef(0);
  const sectionNavigationRef = useRef<ScrollView>(null);
  const sectionTabLayouts = useRef<Record<Section, { x: number; width: number }>>(
    {} as Record<Section, { x: number; width: number }>,
  );
  const platform = hasPermission(currentUser, "PLATFORM_HOTEL_MANAGE");
  const can = (permission: string) => platform || data.permissionCodes.includes(permission);
  const selectedHotel = hotels.find((h) => h.id === hotelId);
  const visibleSections = sections.filter(
    (s) => platform || can(viewPermission[s]),
  );
  const loadHotels = useCallback(async () => {
    const value = await api.hotels();
    setHotels(value);
    setHotelId((current) =>
      current && value.some((h) => h.id === current)
        ? current
        : (value[0]?.id ?? ""),
    );
  }, [api]);
  const loadHotel = useCallback(
    async (target: string) => {
      if (!target) return;
      const request = ++requestRef.current;
      setBusy(true);
      setError(null);
      setData(emptyData());
      try {
      const [access,
          departments,
          buildings,
          floors,
          rooms,
          employees,
          roles,
          skills,
          shifts,
      ] = await Promise.all([
        api.access(target),
          api.list<NamedDto[]>(target, "departments"),
          api.list<BuildingDto[]>(target, "buildings"),
          api.list<FloorDto[]>(target, "floors"),
          api.list<RoomPageDto>(target, "rooms", { page: 0, size: 100 }),
          api.list<MembershipDto[]>(target, "employees"),
          api.list<NamedDto[]>(target, "roles"),
          api.list<NamedDto[]>(target, "skills"),
          api.list<ShiftDto[]>(target, "shifts"),
        ]);
        if (!shouldApplyHotelResponse(request, requestRef.current)) return;
      setData({
        permissionCodes:access.permissionCodes,
          departments,
          buildings,
          floors,
          rooms: rooms.items,
          employees,
          roles,
          skills,
          shifts,
        });
      } catch (e) {
        if (shouldApplyHotelResponse(request, requestRef.current)) setError(message(e));
      } finally {
        if (shouldApplyHotelResponse(request, requestRef.current)) setBusy(false);
      }
    },
    [api],
  );
  useEffect(() => {
    void loadHotels().catch((e) => setError(message(e)));
  }, [loadHotels]);
  useEffect(() => {
    setSelectedRole(null);
    setSelectedEmployee(null);
    setSelectedRoom(null);
    setSearch("");
    setBuildingFilter("");
    setFloorFilter("");
    setData(emptyData());
    if (hotelId) void loadHotel(hotelId);
  }, [hotelId, loadHotel]);
  const refresh = async () => {
    await loadHotels();
    if (hotelId) await loadHotel(hotelId);
  };
  const switchHotel = (id: string) => {
    if (id === hotelId) return;
    requestRef.current++;
    setHotelId(id);
  };
  const filteredRooms = data.rooms.filter(
    (r) =>
      (!search || r.roomNumber.toLowerCase().includes(search.toLowerCase())) &&
      (!buildingFilter || r.buildingId === buildingFilter) &&
      (!floorFilter || r.floorId === floorFilter),
  );
  const filteredEmployees = data.employees.filter(
    (e) =>
      !search ||
      `${e.displayName} ${e.email}`
        .toLowerCase()
        .includes(search.toLowerCase()),
  );
  return (
    <View testID="administration-screen" style={styles.screen}>
      <View style={[styles.header, layout.phone && styles.headerPhone]}>
        <View style={styles.headerCopy}>
          <Text style={styles.title}>Administration</Text>
          <Text style={styles.subtitle}>
            {selectedHotel
              ? `Administering ${selectedHotel.name} · ${selectedHotel.code}`
              : "Hotel master data and access"}
          </Text>
        </View>
        {platform ? (
          <AdminButton label="Onboard hotel" onPress={() => setWizard(true)} />
        ) : null}
      </View>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.selector}
        contentContainerStyle={styles.horizontalContent}
      >
        {hotels.map((h) => (
          <Pressable
            key={h.id}
            onPress={() => switchHotel(h.id)}
            style={[styles.chip, h.id === hotelId && styles.chipActive]}
          >
            <Text style={styles.chipText}>{h.name}</Text>
            <Text style={styles.chipCode}>{h.code}</Text>
          </Pressable>
        ))}
      </ScrollView>
      <View testID="admin-section-navigation-frame" style={styles.tabStrip}>
        <ScrollView
          ref={sectionNavigationRef}
          testID="admin-section-navigation"
          horizontal
          directionalLockEnabled
          showsHorizontalScrollIndicator={false}
          style={styles.tabs}
          contentContainerStyle={styles.horizontalContent}
        >
          {visibleSections.map((s) => (
            <Pressable
              key={s}
              onLayout={(event: LayoutChangeEvent) => {
                sectionTabLayouts.current[s] = event.nativeEvent.layout;
              }}
              onPress={() => {
                setSection(s);
                setSearch("");
                const tab = sectionTabLayouts.current[s];
                if (tab)
                  sectionNavigationRef.current?.scrollTo({
                    x: Math.max(0, tab.x - spacing.lg),
                    animated: true,
                  });
              }}
              style={[styles.tab, s === section && styles.tabActive]}
            >
              <Text numberOfLines={1} style={styles.tabText}>
                {label(s)}
              </Text>
            </Pressable>
          ))}
        </ScrollView>
      </View>
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={[
          styles.content,
          layout.phone && styles.contentPhone,
        ]}
      >
        {error ? (
          <Text style={adminStyles.error}>{businessError(error)}</Text>
        ) : null}
        {busy ? (
          <Text style={adminStyles.help}>
            Refreshing {selectedHotel?.name ?? "hotel"}…
          </Text>
        ) : null}
        {busy && section !== "hotels" ? null : section === "hotels" ? (
          <HotelsPanel hotels={hotels} canManage={platform} />
        ) : (
          <>
            <View style={styles.context}>
              <Text style={styles.contextTitle}>{selectedHotel?.name}</Text>
              <Text style={styles.contextText}>
                All records below are scoped to {selectedHotel?.code}. Switching
                hotel clears selections and pending filters.
              </Text>
            </View>
            {can(managePermission[section]) ? (
              <AdminButton
                label={`Add ${singular(section)}`}
                onPress={() => setAddOpen(true)}
              />
            ) : null}
            {section === "rooms" ? (
              <RoomsPanel
                rooms={filteredRooms}
                buildings={data.buildings}
                floors={data.floors}
                search={search}
                setSearch={setSearch}
                buildingFilter={buildingFilter}
                setBuildingFilter={(value) => {
                  setBuildingFilter(value);
                  setFloorFilter("");
                }}
                floorFilter={floorFilter}
                setFloorFilter={setFloorFilter}
                canUpdate={can("ROOM_UPDATE")}
                onOpen={setSelectedRoom}
              />
            ) : section === "employees" ? (
              <EmployeesPanel
                items={filteredEmployees}
                search={search}
                setSearch={setSearch}
                onOpen={setSelectedEmployee}
              />
            ) : (
              <GenericPanel
                section={section}
                data={data}
                onRole={setSelectedRole}
              />
            )}
            {section === "rooms" && can("ROOM_CREATE") ? (
              <RoomCsvImportPanel
                busy={busy}
                onImport={async (csv) => {
                  const result = await api.importRooms(hotelId, csv);
                  await loadHotel(hotelId);
                  return result;
                }}
              />
            ) : null}
          </>
        )}
      </ScrollView>
      <HotelOnboardingWizard
        visible={wizard}
        administratorUserId={currentUser?.userId ?? ""}
        busy={busy}
        onClose={() => setWizard(false)}
        onCreate={async (payload) => {
          setBusy(true);
          try {
            const result = await api.onboard(payload);
            await loadHotels();
            setHotelId(result.hotel.id);
            setSection("departments");
          } finally {
            setBusy(false);
          }
        }}
      />
      <AddDialog
        visible={addOpen}
        section={section}
        hotelId={hotelId}
        data={data}
        api={api}
        onClose={() => setAddOpen(false)}
        onSaved={async () => {
          setAddOpen(false);
          await loadHotel(hotelId);
        }}
      />
      <RolePermissionEditor
        api={api}
        hotelId={hotelId}
        role={selectedRole}
        canManage={can("ROLE_MANAGE")}
        onClose={() => setSelectedRole(null)}
      />
      <EmployeeEditor
        api={api}
        hotelId={hotelId}
        hotelName={selectedHotel?.name ?? "Selected hotel"}
        employee={selectedEmployee}
        departments={data.departments}
        roles={data.roles}
        skills={data.skills}
        shifts={data.shifts}
        canUpdate={can("USER_UPDATE")}
        canAssign={can("USER_ASSIGN")}
        onClose={() => setSelectedEmployee(null)}
        onSaved={() => void loadHotel(hotelId)}
      />
      <RoomEditor
        api={api}
        hotelId={hotelId}
        room={selectedRoom}
        buildings={data.buildings}
        floors={data.floors}
        canUpdate={can("ROOM_UPDATE")}
        canDeactivate={can("ROOM_DELETE")}
        onClose={() => setSelectedRoom(null)}
        onSaved={() => void loadHotel(hotelId)}
      />
    </View>
  );
}

function HotelsPanel({
  hotels,
  canManage,
}: {
  hotels: HotelDto[];
  canManage: boolean;
}) {
  return (
    <AdminCard title="Hotels">
      {hotels.map((h) => (
        <View key={h.id} style={adminStyles.row}>
          <Text style={adminStyles.rowTitle}>{h.name}</Text>
          <Text style={adminStyles.rowDetail}>
            {h.code} · {h.timezone} · {h.active ? "Active" : "Inactive"}
          </Text>
        </View>
      ))}
      {canManage ? (
        <Text style={adminStyles.help}>
          Use Onboard hotel for a complete, transactional setup.
        </Text>
      ) : null}
    </AdminCard>
  );
}
function RoomsPanel({
  rooms,
  buildings,
  floors,
  search,
  setSearch,
  buildingFilter,
  setBuildingFilter,
  floorFilter,
  setFloorFilter,
  canUpdate,
  onOpen,
}: {
  rooms: RoomDto[];
  buildings: BuildingDto[];
  floors: FloorDto[];
  search: string;
  setSearch: (v: string) => void;
  buildingFilter: string;
  setBuildingFilter: (v: string) => void;
  floorFilter: string;
  setFloorFilter: (v: string) => void;
  canUpdate: boolean;
  onOpen: (r: RoomDto) => void;
}) {
  const choices = availableFloors(floors, buildingFilter);
  return (
    <AdminCard title={`Rooms · ${rooms.length}`}>
      <TextInput
        value={search}
        onChangeText={setSearch}
        placeholder="Search room number"
        style={adminStyles.input}
      />
      <Text style={adminStyles.label}>Building</Text>
      <Filter
        values={buildings}
        selected={buildingFilter}
        onSelect={setBuildingFilter}
      />
      <Text style={adminStyles.label}>Floor</Text>
      <Filter
        values={choices.map((f) => ({
          id: f.id,
          name: `Floor ${f.floorNumber}`,
        }))}
        selected={floorFilter}
        onSelect={setFloorFilter}
      />
      {rooms.map((r) => (
        <Pressable
          key={r.id}
          disabled={!canUpdate}
          onPress={() => onOpen(r)}
          style={adminStyles.row}
        >
          <Text style={adminStyles.rowTitle}>{r.roomNumber}</Text>
          <Text style={adminStyles.rowDetail}>
            {buildings.find((b) => b.id === r.buildingId)?.name} · Floor{" "}
            {floors.find((f) => f.id === r.floorId)?.floorNumber} ·{" "}
            {r.roomType ?? "No type"} · {r.active ? "Active" : "Inactive"}
          </Text>
        </Pressable>
      ))}
      {!rooms.length ? (
        <Text style={adminStyles.help}>No rooms match the current filters.</Text>
      ) : null}
    </AdminCard>
  );
}
function EmployeesPanel({
  items,
  search,
  setSearch,
  onOpen,
}: {
  items: MembershipDto[];
  search: string;
  setSearch: (v: string) => void;
  onOpen: (v: MembershipDto) => void;
}) {
  return (
    <AdminCard title={`Employees · ${items.length}`}>
      <TextInput
        value={search}
        onChangeText={setSearch}
        placeholder="Search name or email"
        style={adminStyles.input}
      />
      {items.map((e) => (
        <Pressable key={e.id} onPress={() => onOpen(e)} style={adminStyles.row}>
          <Text style={adminStyles.rowTitle}>{e.displayName}</Text>
          <Text style={adminStyles.rowDetail}>
            {e.email} · {e.active ? "Active membership" : "Inactive membership"}
          </Text>
        </Pressable>
      ))}
      {!items.length ? (
        <Text style={adminStyles.help}>No employees found.</Text>
      ) : null}
    </AdminCard>
  );
}
function GenericPanel({
  section,
  data,
  onRole,
}: {
  section: Section;
  data: Data;
  onRole: (r: NamedDto) => void;
}) {
  const items = (data[section as keyof Data] ?? []) as unknown as Record<
    string,
    unknown
  >[];
  return (
    <AdminCard title={`${label(section)} · ${items.length}`}>
      {items.map((item, index) => (
        <Pressable
          key={String(item.id ?? index)}
          disabled={section !== "roles"}
          onPress={() =>
            section === "roles" && onRole(item as unknown as NamedDto)
          }
          style={adminStyles.row}
        >
          <Text style={adminStyles.rowTitle}>
            {String(item.name ?? item.code ?? `Floor ${item.floorNumber}`)}
          </Text>
          <Text style={adminStyles.rowDetail}>
            {[
              item.code,
              item.description,
              item.floorNumber,
              item.startTime && `${item.startTime}–${item.endTime}`,
              item.active === false ? "Inactive" : "Active",
            ]
              .filter(Boolean)
              .join(" · ")}
          </Text>
        </Pressable>
      ))}
      {!items.length ? (
        <Text style={adminStyles.help}>No {label(section).toLowerCase()} configured.</Text>
      ) : null}
    </AdminCard>
  );
}
function Filter({
  values,
  selected,
  onSelect,
}: {
  values: { id: string; name: string }[];
  selected: string;
  onSelect: (id: string) => void;
}) {
  return (
    <View style={adminStyles.actions}>
      <AdminButton
        label="All"
        tone={selected ? "secondary" : "primary"}
        onPress={() => onSelect("")}
      />
      {values.map((v) => (
        <AdminButton
          key={v.id}
          label={v.name}
          tone={selected === v.id ? "primary" : "secondary"}
          onPress={() => onSelect(v.id)}
        />
      ))}
    </View>
  );
}

function AddDialog({
  visible,
  section,
  hotelId,
  data,
  api,
  onClose,
  onSaved,
}: {
  visible: boolean;
  section: Section;
  hotelId: string;
  data: Data;
  api: AdminApi;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [code, setCode] = useState(""),
    [name, setName] = useState(""),
    [extra, setExtra] = useState(""),
    [parent, setParent] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<string | null>(null);
  useEffect(() => {
    setCode("");
    setName("");
    setExtra("");
    setParent("");
    setError(null);
  }, [visible, section, hotelId]);
  const save = async () => {
    setBusy(true);
    try {
      if (section === "employees")
        await api.create(hotelId, "employees", {
          email: code,
          displayName: name,
          initialPassword: extra,
        });
      else if (section === "buildings")
        await api.create(hotelId, "buildings", {
          code: code.toUpperCase(),
          name,
        });
      else if (section === "floors")
        await api.create(hotelId, "floors", {
          buildingId: parent,
          floorNumber: Number(code),
          name: name || null,
        });
      else if (section === "rooms") {
        const floor = data.floors.find((f) => f.id === parent);
        if (!floor) throw new Error("Select a floor");
        await api.create(hotelId, "rooms", {
          buildingId: floor.buildingId,
          floorId: floor.id,
          roomNumber: code,
          roomType: extra || null,
        });
      } else if (section === "shifts")
        await api.create(hotelId, "shifts", {
          code: code.toUpperCase(),
          name,
          startTime: extra.split("-")[0]?.trim(),
          endTime: extra.split("-")[1]?.trim(),
        });
      else
        await api.create(hotelId, section, {
          code: code.toUpperCase(),
          name,
          description: extra || null,
        });
      await onSaved();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };
  const parents =
    section === "floors"
      ? data.buildings
      : section === "rooms"
        ? data.floors.map((f) => ({
            id: f.id,
            name: `${data.buildings.find((b) => b.id === f.buildingId)?.name} · Floor ${f.floorNumber}`,
          }))
        : [];
  return (
    <AdminModal
      visible={visible}
      title={`Add ${singular(section)}`}
      onClose={onClose}
    >
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={styles.dialogBody}
      >
      {parents.length ? (
        <Filter values={parents} selected={parent} onSelect={setParent} />
      ) : null}
      <TextInput
        value={code}
        onChangeText={setCode}
        placeholder={
          section === "employees"
            ? "Email"
            : section === "floors"
              ? "Floor number"
              : section === "rooms"
                ? "Room number"
                : "Code"
        }
        style={adminStyles.input}
      />
      {section !== "rooms" && section !== "floors" ? (
        <TextInput
          value={name}
          onChangeText={setName}
          placeholder="Name"
          style={adminStyles.input}
        />
      ) : null}
      <TextInput
        secureTextEntry={section === "employees"}
        value={extra}
        onChangeText={setExtra}
        placeholder={
          section === "employees"
            ? "Initial password"
            : section === "shifts"
              ? "07:00 - 15:00"
              : "Description / room type"
        }
        style={adminStyles.input}
      />
      {error ? (
        <Text style={adminStyles.error}>{businessError(error)}</Text>
      ) : null}
      <AdminButton
        label="Create"
        disabled={
          busy ||
          !code ||
          ((section === "floors" || section === "rooms") && !parent)
        }
        onPress={() => void save()}
      />
      </ScrollView>
    </AdminModal>
  );
}
function RoomEditor({
  api,
  hotelId,
  room,
  buildings,
  floors,
  canUpdate,
  canDeactivate,
  onClose,
  onSaved,
}: {
  api: AdminApi;
  hotelId: string;
  room: RoomDto | null;
  buildings: BuildingDto[];
  floors: FloorDto[];
  canUpdate: boolean;
  canDeactivate: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [buildingId, setBuildingId] = useState(""),
    [floorId, setFloorId] = useState(""),
    [number, setNumber] = useState(""),
    [type, setType] = useState(""),
    [active, setActive] = useState(true),
    [confirm, setConfirm] = useState(false),
    [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (room) {
      setBuildingId(room.buildingId);
      setFloorId(room.floorId);
      setNumber(room.roomNumber);
      setType(room.roomType ?? "");
      setActive(room.active);
      setConfirm(false);
      setError(null);
    }
  }, [room]);
  const save = async () => {
    if (!room) return;
    try {
      await api.update(hotelId, "rooms", room.id, {
        buildingId,
        floorId,
        roomNumber: number,
        roomType: type || null,
        active,
      });
      onSaved();
      onClose();
    } catch (e) {
      setError(message(e));
    }
  };
  return (
    <AdminModal
      visible={Boolean(room)}
      title={`Room ${room?.roomNumber ?? ""}`}
      onClose={onClose}
    >
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={styles.dialogBody}
      >
      <Filter
        values={buildings}
        selected={buildingId}
        onSelect={(id) => {
          setBuildingId(id);
          setFloorId("");
        }}
      />
      <Filter
        values={availableFloors(floors, buildingId).map((f) => ({
          id: f.id,
          name: `Floor ${f.floorNumber}`,
        }))}
        selected={floorId}
        onSelect={setFloorId}
      />
      <TextInput
        value={number}
        onChangeText={setNumber}
        style={adminStyles.input}
      />
      <TextInput
        value={type}
        onChangeText={setType}
        placeholder="Room type"
        style={adminStyles.input}
      />
      {confirm ? (
        <View>
          <Text style={adminStyles.error}>
            Deactivate this room? Existing references remain intact.
          </Text>
          <View style={adminStyles.actions}>
            <AdminButton
              label="Confirm"
              tone="danger"
              onPress={() => {
                setActive(false);
                setConfirm(false);
              }}
            />
            <AdminButton
              label="Cancel"
              tone="secondary"
              onPress={() => setConfirm(false)}
            />
          </View>
        </View>
      ) : active && canDeactivate ? (
        <AdminButton
          label="Deactivate"
          tone="danger"
          onPress={() => setConfirm(true)}
        />
      ) : !active && canDeactivate ? (
        <AdminButton label="Reactivate" onPress={() => setActive(true)} />
      ) : null}
      {error ? (
        <Text style={adminStyles.error}>{businessError(error)}</Text>
      ) : null}
      <AdminButton
        label="Save room"
        disabled={!canUpdate || !floorId || !number}
        onPress={() => void save()}
      />
      </ScrollView>
    </AdminModal>
  );
}
function singular(s: Section) {
  return s === "employees"
    ? "employee"
    : s === "skills"
      ? "skill"
      : s === "shifts"
        ? "shift"
        : s.endsWith("s")
          ? s.slice(0, -1)
          : s;
}
function label(s: string) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
function message(e: unknown) {
  return e instanceof Error ? e.message : "Request failed";
}
function businessError(value: string) {
  if (value.includes("Duplicate or cross-hotel"))
    return "This value already exists, is referenced, or belongs to another hotel.";
  if (value.includes("HTTP 403"))
    return "You do not have permission for this action in the selected hotel.";
  if (
    value.includes("HTTP 500") ||
    value.includes("BadSqlGrammar") ||
    value.includes("could not determine data type")
  )
    return "This administration data could not be loaded. Please try again or contact support.";
  return value;
}
const styles = StyleSheet.create({
  screen: {
    flex: 1,
    width: "100%",
    maxWidth: "100%",
    minWidth: 0,
    overflow: "hidden",
    backgroundColor: colors.background,
  },
  header: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 8,
  },
  headerPhone: { alignItems: "stretch", flexDirection: "column" },
  headerCopy: { flex: 1, minWidth: 0 },
  title: { fontSize: typography.title, fontWeight: "900", color: colors.text },
  subtitle: { fontSize: typography.body, color: colors.textMuted },
  selector: {
    maxHeight: 54,
    marginTop: spacing.md,
  },
  horizontalContent: {
    paddingHorizontal: spacing.lg,
    paddingRight: spacing.xl,
  },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: radius.lg,
    backgroundColor: colors.surfaceMuted,
    marginRight: 6,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  chipActive: {
    backgroundColor: colors.greenSoft,
    borderColor: colors.greenBorder,
  },
  chipText: { fontSize: 11, fontWeight: "800", color: colors.nav },
  chipCode: { fontSize: 8, color: colors.textMuted },
  tabs: {
    width: "100%",
    flexGrow: 0,
    maxHeight: 43,
  },
  tabStrip: {
    width: "100%",
    maxWidth: "100%",
    minWidth: 0,
    overflow: "hidden",
    borderBottomWidth: 1,
    borderColor: colors.divider,
  },
  tab: { flexShrink: 0, paddingHorizontal: 10, paddingVertical: 10 },
  tabActive: { borderBottomWidth: 2, borderColor: colors.green },
  tabText: { fontSize: 10, fontWeight: "900", color: colors.nav },
  content: {
    width: "100%",
    maxWidth: "100%",
    minWidth: 0,
    padding: spacing.lg,
    gap: spacing.md,
    paddingBottom: 80,
  },
  contentPhone: { paddingHorizontal: spacing.md },
  dialogBody: { gap: spacing.sm, paddingBottom: spacing.lg },
  context: {
    borderLeftWidth: 3,
    borderColor: colors.green,
    paddingLeft: spacing.md,
  },
  contextTitle: { fontSize: 13, fontWeight: "900", color: colors.text },
  contextText: { fontSize: 9, color: colors.textMuted },
});
