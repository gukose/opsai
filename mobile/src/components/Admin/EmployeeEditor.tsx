import { Dispatch, SetStateAction, useEffect, useState } from "react";
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import {
  AdminApi,
  MembershipDetailDto,
  MembershipDto,
  NamedDto,
  ShiftDto,
} from "../../api/admin/AdminApi";
import { colors, radius, spacing } from "../../theme/tokens";
import { AdminButton, AdminModal, adminStyles } from "./AdminUi";

export function EmployeeEditor(props: {
  api: AdminApi;
  hotelId: string;
  hotelName: string;
  employee: MembershipDto | null;
  departments: NamedDto[];
  roles: NamedDto[];
  skills: NamedDto[];
  shifts: ShiftDto[];
  canUpdate: boolean;
  canAssign: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const {
    api,
    hotelId,
    hotelName,
    employee,
    departments,
    roles,
    skills,
    shifts,
    canUpdate,
    canAssign,
    onClose,
    onSaved,
  } = props;
  const [detail, setDetail] = useState<MembershipDetailDto | null>(null);
  const [name, setName] = useState("");
  const [departmentId, setDepartmentId] = useState<string | null>(null);
  const [active, setActive] = useState(true);
  const [roleIds, setRoleIds] = useState<Set<string>>(new Set());
  const [skillIds, setSkillIds] = useState<Set<string>>(new Set());
  const [shiftId, setShiftId] = useState("");
  const [shiftDate, setShiftDate] = useState(
    new Date().toISOString().slice(0, 10),
  );
  const [busy, setBusy] = useState(false);
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    setDetail(null);
    setError(null);
    if (!employee) return;
    setBusy(true);
    void api
      .membership(hotelId, employee.id)
      .then((value) => {
        setDetail(value);
        setName(value.membership.displayName);
        setDepartmentId(value.membership.departmentId);
        setActive(value.membership.active);
        setRoleIds(new Set(value.roles.map((r) => r.id)));
        setSkillIds(new Set(value.skills.map((s) => s.id)));
      })
      .catch((e) => setError(message(e)))
      .finally(() => setBusy(false));
  }, [api, hotelId, employee?.id]);
  const save = async () => {
    if (!employee) return;
    setBusy(true);
    setError(null);
    try {
      if (canUpdate)
        await api.updateMembership(hotelId, employee.id, {
          displayName: name,
          departmentId,
          active,
        });
      if (canAssign) {
        await api.replaceMembershipRoles(hotelId, employee.id, [...roleIds]);
        const mapped = Object.fromEntries(
          [...skillIds].map((id) => [
            id,
            detail?.skills.find((s) => s.id === id)?.skillLevel ??
              "INTERMEDIATE",
          ]),
        );
        await api.replaceMembershipSkills(hotelId, employee.id, mapped);
      }
      onSaved();
      onClose();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };
  const assignShift = async () => {
    if (!employee || !shiftId) return;
    setBusy(true);
    try {
      await api.create(hotelId, "shift-assignments", {
        membershipId: employee.id,
        shiftId,
        shiftDate,
      });
      setDetail(await api.membership(hotelId, employee.id));
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <AdminModal
      visible={Boolean(employee)}
      title={employee ? employee.displayName : "Employee"}
      onClose={onClose}
    >
      <ScrollView
        style={styles.scroll}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={styles.body}
      >
        <Text style={styles.context}>{hotelName}</Text>
        <Text style={adminStyles.label}>Global identity / login</Text>
        <Text style={adminStyles.rowDetail}>{employee?.email}</Text>
        <Text style={adminStyles.help}>
          Email and display name belong to the global app_user. Changing the
          display name is visible in every hotel membership.
        </Text>
        <Text style={adminStyles.label}>Display name</Text>
        <TextInput
          editable={canUpdate}
          value={name}
          onChangeText={setName}
          style={adminStyles.input}
        />
        <Text style={adminStyles.help}>Hotel-specific membership configuration</Text>
        <Text style={adminStyles.label}>Department</Text>
        <ChoiceRow
          values={departments}
          selected={departmentId ? new Set([departmentId]) : new Set()}
          onToggle={setDepartmentId}
        />
        <Text style={adminStyles.label}>Hotel roles</Text>
        <ChoiceRow
          values={roles}
          selected={roleIds}
          onToggle={(id) => toggle(setRoleIds, id)}
        />
        <Text style={adminStyles.label}>Hotel skills</Text>
        <ChoiceRow
          values={skills}
          selected={skillIds}
          onToggle={(id) => toggle(setSkillIds, id)}
        />
        <Text style={adminStyles.label}>Shift assignment</Text>
        <View style={adminStyles.actions}>
          <ChoiceRow
            values={shifts}
            selected={shiftId ? new Set([shiftId]) : new Set()}
            onToggle={setShiftId}
          />
          <TextInput
            value={shiftDate}
            onChangeText={setShiftDate}
            style={adminStyles.input}
          />
          <AdminButton
            label="Assign shift"
            disabled={!canAssign || !shiftId || busy}
            tone="secondary"
            onPress={() => void assignShift()}
          />
        </View>
        {detail?.shifts.map((s) => (
          <Text key={s.id} style={adminStyles.rowDetail}>
            {s.shiftDate} ·{" "}
            {shifts.find((x) => x.id === s.shiftId)?.name ?? s.shiftId}
          </Text>
        ))}
        <View style={adminStyles.actions}>
          {active ? (
            <AdminButton
              label="Deactivate membership"
              tone="danger"
              disabled={!canUpdate}
              onPress={() => setConfirmDeactivate(true)}
            />
          ) : (
            <AdminButton
              label="Reactivate membership"
              disabled={!canUpdate}
              onPress={() => setActive(true)}
            />
          )}
          <AdminButton
            label="Save changes"
            disabled={busy || (!canUpdate && !canAssign)}
            onPress={() => void save()}
          />
        </View>
        {confirmDeactivate ? (
          <View>
            <Text style={adminStyles.error}>
              Deactivate this hotel membership? Login and hotel-specific access
              will stop for {hotelName}.
            </Text>
            <View style={adminStyles.actions}>
              <AdminButton
                label="Confirm deactivation"
                tone="danger"
                onPress={() => {
                  setActive(false);
                  setConfirmDeactivate(false);
                }}
              />
              <AdminButton
                label="Cancel"
                tone="secondary"
                onPress={() => setConfirmDeactivate(false)}
              />
            </View>
          </View>
        ) : null}
        {error ? <Text style={adminStyles.error}>{error}</Text> : null}
      </ScrollView>
    </AdminModal>
  );
}
function ChoiceRow({
  values,
  selected,
  onToggle,
}: {
  values: { id: string; name: string }[];
  selected: Set<string>;
  onToggle: (id: string) => void;
}) {
  return (
    <View style={adminStyles.actions}>
      {values.map((v) => (
        <Pressable
          key={v.id}
          onPress={() => onToggle(v.id)}
          style={[styles.choice, selected.has(v.id) && adminStyles.selected]}
        >
          <Text style={styles.choiceText}>
            {selected.has(v.id) ? "✓ " : ""}
            {v.name}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}
function toggle(setter: Dispatch<SetStateAction<Set<string>>>, id: string) {
  setter((current) => {
    const next = new Set(current);
    next.has(id) ? next.delete(id) : next.add(id);
    return next;
  });
}
function message(e: unknown) {
  return e instanceof Error ? e.message : "Employee update failed";
}
const styles = StyleSheet.create({
  scroll: { flexShrink: 1, minHeight: 0 },
  body: { gap: spacing.sm, paddingBottom: spacing.md },
  context: { fontSize: 12, fontWeight: "900", color: colors.green },
  choice: {
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.pill,
    paddingHorizontal: 9,
    paddingVertical: 6,
  },
  choiceText: { fontSize: 10, fontWeight: "800", color: colors.nav },
});
