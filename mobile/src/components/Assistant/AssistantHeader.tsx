import { Pressable, StyleSheet, Text, View } from "react-native";
import { Bell, LogOut, RotateCcw } from "lucide-react-native";
import { useMemo, useState } from "react";

import { assistantDataSourceMode } from "../../config/assistantConfig";
import {
  getCurrentUserDisplayName,
  getCurrentUserHotelLabel,
  getCurrentUserPermissionCount,
  getCurrentUserRoleCodes
} from "../../auth/currentUserHelpers";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { DashboardRecentNotification } from "../../dashboard/types";
import { colors, radius, spacing, typography } from "../../theme/tokens";
import { IconButton } from "../ui/IconButton";
import {
  formatUnreadBadge,
  shouldShowUnreadBadge,
  visibleRecentNotifications
} from "./notificationPanelLogic";

type AssistantHeaderProps = {
  currentUser: CurrentUserSnapshot | null;
  unreadNotificationCount?: number;
  recentNotifications?: DashboardRecentNotification[];
  notificationsStaleReason?: string | null;
  onReset?: () => void;
  onLogout?: () => void;
};

export function AssistantHeader({
  currentUser,
  unreadNotificationCount = 0,
  recentNotifications = [],
  notificationsStaleReason,
  onReset,
  onLogout
}: AssistantHeaderProps) {
  const [isNotificationPanelOpen, setNotificationPanelOpen] = useState(false);
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
  const visibleNotifications = useMemo(
    () => visibleRecentNotifications(recentNotifications),
    [recentNotifications]
  );
  const unreadBadge = formatUnreadBadge(unreadNotificationCount);

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
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Notifications${shouldShowUnreadBadge(unreadNotificationCount) ? `, ${unreadNotificationCount} unread` : ""}`}
            onPress={() => setNotificationPanelOpen((current) => !current)}
            style={({ pressed }) => [
              styles.notification,
              pressed ? styles.pressed : null
            ]}
          >
            <Bell color={colors.text} size={16} strokeWidth={2.2} />
            {shouldShowUnreadBadge(unreadNotificationCount) ? (
              <View style={styles.badge}>
                <Text style={styles.badgeText}>{unreadBadge}</Text>
              </View>
            ) : null}
          </Pressable>
          {onReset ? (
            <IconButton
              icon={RotateCcw}
              label="Reset assistant"
              onPress={onReset}
              color={colors.text}
              size={16}
              style={styles.notification}
            />
          ) : null}
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
      {isNotificationPanelOpen ? (
        <View style={styles.notificationPanel}>
          <View style={styles.notificationPanelHeader}>
            <Text style={styles.notificationPanelTitle}>Notifications</Text>
            {notificationsStaleReason ? <Text style={styles.notificationStale}>Offline data</Text> : null}
          </View>
          {visibleNotifications.length > 0 ? (
            visibleNotifications.map((notification) => (
              <View key={notification.id} style={styles.notificationRow}>
                <Text style={styles.notificationTitle} numberOfLines={1}>
                  {notification.title}
                </Text>
                <Text style={styles.notificationBody} numberOfLines={2}>
                  {notification.body}
                </Text>
              </View>
            ))
          ) : (
            <Text style={styles.emptyNotifications}>No notifications</Text>
          )}
        </View>
      ) : null}
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
  pressed: {
    opacity: 0.72
  },
  badge: {
    position: "absolute",
    top: -5,
    right: -7,
    minWidth: 15,
    height: 15,
    paddingHorizontal: 3,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.pill,
    backgroundColor: "#dc2626"
  },
  badgeText: {
    color: "#ffffff",
    fontSize: 8,
    fontWeight: "900"
  },
  notificationPanel: {
    marginTop: 8,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    paddingHorizontal: 10,
    paddingVertical: 8
  },
  notificationPanelHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 6
  },
  notificationPanelTitle: {
    color: colors.text,
    fontSize: typography.subtitle,
    fontWeight: "900"
  },
  notificationStale: {
    color: colors.textMuted,
    fontSize: 9,
    fontWeight: "800"
  },
  notificationRow: {
    paddingVertical: 7,
    borderTopWidth: 1,
    borderTopColor: colors.divider
  },
  notificationTitle: {
    color: colors.text,
    fontSize: typography.body,
    fontWeight: "900"
  },
  notificationBody: {
    marginTop: 2,
    color: colors.textMuted,
    fontSize: typography.caption,
    fontWeight: "700"
  },
  emptyNotifications: {
    paddingVertical: 10,
    color: colors.textMuted,
    fontSize: typography.body,
    fontWeight: "800",
    textAlign: "center"
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
