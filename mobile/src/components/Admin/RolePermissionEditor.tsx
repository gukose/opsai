import { useEffect, useMemo, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { AdminApi, NamedDto, PermissionDto } from "../../api/admin/AdminApi";
import { colors, radius, spacing } from "../../theme/tokens";
import { groupPermissions } from "./adminLogic";
import { AdminButton, AdminModal, adminStyles } from "./AdminUi";

export function RolePermissionEditor(props: {
  api: AdminApi;
  hotelId: string;
  role: NamedDto | null;
  canManage: boolean;
  onClose: () => void;
}) {
  const { api, hotelId, role, canManage, onClose } = props;
  const [items, setItems] = useState<PermissionDto[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    setItems([]);
    setSelected(new Set());
    setError(null);
    if (!role) return;
    setBusy(true);
    void api
      .rolePermissions(hotelId, role.id)
      .then((value) => {
        setItems(value);
        setSelected(new Set(value.filter((p) => p.assigned).map((p) => p.id)));
      })
      .catch((e) => setError(message(e)))
      .finally(() => setBusy(false));
  }, [api, hotelId, role?.id]);
  const groups = useMemo(() => groupPermissions(items), [items]);
  const setGroup = (ids: string[], on: boolean) =>
    setSelected((current) => {
      const next = new Set(current);
      ids.forEach((id) => (on ? next.add(id) : next.delete(id)));
      return next;
    });
  const save = async () => {
    if (!role) return;
    setBusy(true);
    try {
      await api.saveRolePermissions(hotelId, role.id, [...selected]);
      onClose();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <AdminModal
      visible={Boolean(role)}
      title={role ? `Role · ${role.name}` : "Role"}
      onClose={onClose}
    >
      <ScrollView contentContainerStyle={styles.body}>
        {busy ? (
          <Text style={adminStyles.help}>Loading permission catalog…</Text>
        ) : null}
        {groups.map((group) => (
          <View
            key={group.name}
            style={[styles.group, group.platform && styles.platform]}
          >
            <View style={styles.groupHeader}>
              <View>
                <Text style={styles.groupTitle}>{group.name}</Text>
                {group.platform ? (
                  <Text style={styles.platformText}>
                    Platform-wide permissions
                  </Text>
                ) : null}
              </View>
              {canManage ? (
                <View style={adminStyles.actions}>
                  <AdminButton
                    label="Select all"
                    tone="secondary"
                    onPress={() =>
                      setGroup(
                        group.items.map((i) => i.id),
                        true,
                      )
                    }
                  />
                  <AdminButton
                    label="Clear"
                    tone="secondary"
                    onPress={() =>
                      setGroup(
                        group.items.map((i) => i.id),
                        false,
                      )
                    }
                  />
                </View>
              ) : null}
            </View>
            {group.items.map((permission) => (
              <Pressable
                disabled={!canManage}
                key={permission.id}
                onPress={() =>
                  setSelected((current) => {
                    const next = new Set(current);
                    next.has(permission.id)
                      ? next.delete(permission.id)
                      : next.add(permission.id);
                    return next;
                  })
                }
                style={styles.permission}
              >
                <View
                  style={[
                    styles.checkbox,
                    selected.has(permission.id) && styles.checked,
                  ]}
                >
                  <Text style={styles.check}>
                    {selected.has(permission.id) ? "✓" : ""}
                  </Text>
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={adminStyles.rowTitle}>{permission.name}</Text>
                  <Text style={adminStyles.rowDetail}>{permission.code}</Text>
                </View>
              </Pressable>
            ))}
          </View>
        ))}
        {error ? <Text style={adminStyles.error}>{error}</Text> : null}
      </ScrollView>
      {canManage ? (
        <AdminButton
          label="Save permissions"
          disabled={busy}
          onPress={() => void save()}
        />
      ) : null}
    </AdminModal>
  );
}
function message(e: unknown) {
  return e instanceof Error ? e.message : "Permission request failed";
}
const styles = StyleSheet.create({
  body: { gap: spacing.md, paddingBottom: spacing.md },
  group: {
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    padding: spacing.md,
  },
  platform: { borderColor: colors.amber, backgroundColor: "#fffbeb" },
  groupHeader: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 8,
    marginBottom: 6,
  },
  groupTitle: { fontSize: 12, fontWeight: "900", color: colors.text },
  platformText: { fontSize: 9, fontWeight: "800", color: "#92400e" },
  permission: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingVertical: 6,
  },
  checkbox: {
    width: 20,
    height: 20,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: 5,
    alignItems: "center",
    justifyContent: "center",
  },
  checked: { backgroundColor: colors.green, borderColor: colors.green },
  check: { fontSize: 12, fontWeight: "900", color: "white" },
});
