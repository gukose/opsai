import { ComponentType } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { Building2, LogOut, Mail, ShieldCheck, UserCircle2 } from "lucide-react-native";
import { LucideProps } from "lucide-react-native";

import {
  getCurrentUserDisplayName,
  getCurrentUserHotelLabel,
  getCurrentUserPermissionCount,
  getCurrentUserPermissionCodes,
  getCurrentUserRoleCodes
} from "../../auth/currentUserHelpers";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { colors, radius, shadow, spacing, typography } from "../../theme/tokens";

type ProfilePanelProps = {
  currentUser: CurrentUserSnapshot | null;
  onLogout?: () => void;
};

export function ProfilePanel({ currentUser, onLogout }: ProfilePanelProps) {
  const displayName = getCurrentUserDisplayName(currentUser);
  const hotelLabel = getCurrentUserHotelLabel(currentUser);
  const roleCodes = getCurrentUserRoleCodes(currentUser);
  const permissionCodes = getCurrentUserPermissionCodes(currentUser);
  const permissionCount = getCurrentUserPermissionCount(currentUser);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.iconWell}>
          <UserCircle2 color={colors.blue} size={20} strokeWidth={2.1} />
        </View>
        <View style={styles.headerText}>
          <Text style={styles.kicker}>PROFILE</Text>
          <Text style={styles.title}>{displayName}</Text>
          <Text style={styles.subtitle} numberOfLines={1}>
            {permissionCount} permissions active
          </Text>
        </View>
      </View>

      <ProfileRow icon={Building2} label="Hotel" value={hotelLabel} />
      <ProfileRow icon={Mail} label="Email" value={currentUser?.email ?? "No email available"} />
      <ProfileRow
        icon={UserCircle2}
        label="Roles"
        value={roleCodes.length > 0 ? roleCodes.join(" · ") : "No roles loaded"}
      />
      <ProfileRow
        icon={ShieldCheck}
        label="Permissions"
        value={`${permissionCount} active`}
      />

      <View style={styles.metaRow}>
        <MetaPill label="Employee" value={currentUser?.employeeId ?? "n/a"} />
        <MetaPill label="Hotel ID" value={currentUser?.hotelId ?? "n/a"} />
      </View>

      {permissionCodes.length > 0 ? (
        <View style={styles.permissionWrap}>
          <Text style={styles.permissionLabel}>Permission codes</Text>
          <Text style={styles.permissionText} numberOfLines={2}>
            {permissionCodes.join(", ")}
          </Text>
        </View>
      ) : null}

      <Pressable
        accessibilityRole="button"
        disabled={!onLogout}
        onPress={onLogout}
        style={({ pressed }) => [
          styles.logoutButton,
          pressed && onLogout ? styles.logoutPressed : null,
          !onLogout ? styles.logoutDisabled : null
        ]}
      >
        <LogOut color="#ffffff" size={14} strokeWidth={2.2} />
        <Text style={styles.logoutLabel}>Log out</Text>
      </Pressable>
    </View>
  );
}

type ProfileRowProps = {
  icon: ComponentType<LucideProps>;
  label: string;
  value: string;
};

function ProfileRow({ icon: Icon, label, value }: ProfileRowProps) {
  return (
    <View style={styles.row}>
      <View style={styles.rowIcon}>
        <Icon color={colors.green} size={14} strokeWidth={2.2} />
      </View>
      <View style={styles.rowText}>
        <Text style={styles.rowLabel}>{label}</Text>
        <Text style={styles.rowValue} numberOfLines={1}>
          {value}
        </Text>
      </View>
    </View>
  );
}

function MetaPill({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.metaPill}>
      <Text style={styles.metaLabel}>{label}</Text>
      <Text style={styles.metaValue} numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    marginHorizontal: 13,
    marginTop: 8,
    marginBottom: 6,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    padding: spacing.md,
    ...shadow.card
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10
  },
  iconWell: {
    width: 40,
    height: 40,
    borderRadius: radius.pill,
    backgroundColor: "#e9f1ff",
    alignItems: "center",
    justifyContent: "center"
  },
  headerText: {
    flex: 1,
    minWidth: 0
  },
  kicker: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  title: {
    marginTop: 1,
    color: colors.text,
    fontSize: 14,
    fontWeight: "800"
  },
  subtitle: {
    marginTop: 2,
    color: colors.textMuted,
    fontSize: typography.caption,
    fontWeight: "700"
  },
  row: {
    marginTop: 10,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingVertical: 6,
    paddingHorizontal: 8,
    borderRadius: 12,
    backgroundColor: "#fbfcfe"
  },
  rowIcon: {
    width: 26,
    height: 26,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.pill,
    backgroundColor: "#eef6f0"
  },
  rowText: {
    flex: 1,
    minWidth: 0
  },
  rowLabel: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "800"
  },
  rowValue: {
    marginTop: 1,
    color: colors.text,
    fontSize: 12,
    fontWeight: "700"
  },
  metaRow: {
    marginTop: 10,
    flexDirection: "row",
    gap: 6
  },
  metaPill: {
    flex: 1,
    paddingVertical: 6,
    paddingHorizontal: 8,
    borderRadius: 12,
    backgroundColor: "#f6f8fb"
  },
  metaLabel: {
    color: colors.textMuted,
    fontSize: 9,
    fontWeight: "800",
    textTransform: "uppercase"
  },
  metaValue: {
    marginTop: 2,
    color: colors.text,
    fontSize: 11,
    fontWeight: "800"
  },
  permissionWrap: {
    marginTop: 10,
    padding: 8,
    borderRadius: 12,
    backgroundColor: "#f9fafc"
  },
  permissionLabel: {
    color: colors.textMuted,
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  permissionText: {
    marginTop: 3,
    color: colors.text,
    fontSize: 10,
    fontWeight: "700"
  },
  logoutButton: {
    marginTop: 12,
    height: 38,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    borderRadius: 13,
    backgroundColor: "#0f172a"
  },
  logoutPressed: {
    opacity: 0.88
  },
  logoutDisabled: {
    opacity: 0.45
  },
  logoutLabel: {
    color: "#ffffff",
    fontSize: 11,
    fontWeight: "800"
  }
});
