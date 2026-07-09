import { StyleSheet, Text, View } from "react-native";
import { Bell, LogOut } from "lucide-react-native";

import { assistantDataSourceMode } from "../../config/assistantConfig";
import {
  getCurrentUserDisplayName,
  getCurrentUserHotelLabel,
  getCurrentUserPermissionCount,
  getCurrentUserRoleCodes
} from "../../auth/currentUserHelpers";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { colors, radius, spacing, typography } from "../../theme/tokens";
import { IconButton } from "../ui/IconButton";

type AssistantHeaderProps = {
  currentUser: CurrentUserSnapshot | null;
  onReset?: () => void;
  onLogout?: () => void;
};

export function AssistantHeader({ currentUser, onReset, onLogout }: AssistantHeaderProps) {
  const displayName = getCurrentUserDisplayName(currentUser);
  const roleCodes = getCurrentUserRoleCodes(currentUser);
  const roleLabel = roleCodes.length > 0 ? roleCodes.join(" · ") : "Session active";
  const hotelLabel = getCurrentUserHotelLabel(currentUser);
  const permissionCount = getCurrentUserPermissionCount(currentUser);
  const sessionMeta = [
    currentUser?.employeeId ? `Employee ${currentUser.employeeId}` : null,
    currentUser?.hotelId ? `Hotel ${currentUser.hotelId}` : null,
    `${permissionCount} permissions`
  ]
    .filter((value): value is string => Boolean(value))
    .join(" · ");

  return (
    <View style={styles.header}>
      <View style={styles.content}>
        <View>
          <Text style={styles.greeting}>👋 Good Morning{displayName ? `, ${displayName}` : ""}</Text>
          <Text style={styles.role}>{roleLabel}</Text>
          <Text style={styles.shift}>{hotelLabel}</Text>
          {sessionMeta ? <Text style={styles.sessionMeta}>{sessionMeta}</Text> : null}
          {__DEV__ ? (
            <View style={styles.modePill}>
              <Text style={styles.modeLabel}>assistant mode</Text>
              <Text style={styles.modeValue}>{assistantDataSourceMode}</Text>
            </View>
          ) : null}
        </View>
        <View style={styles.actions}>
          <IconButton
            icon={Bell}
            label="Reset assistant"
            onPress={onReset}
            color={colors.text}
            size={16}
            style={styles.notification}
          />
          {onLogout ? (
            <IconButton
              icon={LogOut}
              label="Sign out"
              onPress={onLogout}
              color={colors.textMuted}
              size={15}
              style={styles.logout}
            />
          ) : null}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    minHeight: 64,
    paddingHorizontal: spacing.xl,
    paddingTop: 2
  },
  content: {
    minHeight: 54,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between"
  },
  actions: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8
  },
  greeting: {
    color: colors.text,
    fontSize: typography.title,
    fontWeight: "800"
  },
  role: {
    marginTop: spacing.xxs,
    color: colors.text,
    fontSize: typography.subtitle,
    fontWeight: "800"
  },
  shift: {
    marginTop: spacing.xxs,
    color: colors.nav,
    fontSize: typography.subtitle,
    fontWeight: "700"
  },
  sessionMeta: {
    marginTop: 2,
    color: colors.textMuted,
    fontSize: 9,
    fontWeight: "700"
  },
  modePill: {
    marginTop: 6,
    flexDirection: "row",
    alignItems: "center",
    alignSelf: "flex-start",
    gap: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderWidth: 1,
    borderColor: "#d8dee9",
    borderRadius: radius.pill,
    backgroundColor: "#f7f9fc"
  },
  modeLabel: {
    color: colors.textMuted,
    fontSize: 9,
    fontWeight: "700",
    textTransform: "uppercase"
  },
  modeValue: {
    color: colors.text,
    fontSize: 9,
    fontWeight: "800",
    textTransform: "lowercase"
  },
  notification: {
    width: 28,
    height: 28,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.pill,
    backgroundColor: colors.surface
  },
  logout: {
    width: 28,
    height: 28,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.pill,
    backgroundColor: colors.surface
  }
});
