import { Pressable, StyleSheet, Text, View } from "react-native";
import { Bell, LogOut, RotateCcw } from "lucide-react-native";
import { useMemo, useState } from "react";

import {
  getCurrentUserDisplayName,
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
  compact?: boolean;
};

export function AssistantHeader({
  currentUser,
  unreadNotificationCount = 0,
  recentNotifications = [],
  notificationsStaleReason,
  onReset,
  onLogout,
  compact = false
}: AssistantHeaderProps) {
  const [isNotificationPanelOpen, setNotificationPanelOpen] = useState(false);
  const displayName = getCurrentUserDisplayName(currentUser);
  const roleCodes = getCurrentUserRoleCodes(currentUser);
  const roleLabel = roleCodes.length > 0 ? roleCodes.map(humanizeRole).join(" · ") : "Session active";
  const visibleNotifications = useMemo(
    () => visibleRecentNotifications(recentNotifications),
    [recentNotifications]
  );
  const unreadBadge = formatUnreadBadge(unreadNotificationCount);

  return (
    <View style={styles.header}>
      <View style={styles.content}>
        <View style={styles.identity}>
          {!compact ? <><Text style={styles.greeting} numberOfLines={1}>👋 Good Morning{displayName ? `, ${displayName}` : ""}</Text><Text style={styles.role}>{roleLabel}</Text></> : null}
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

function humanizeRole(value: string): string {
  return value.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
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
    flexShrink: 0,
    flexDirection: "row",
    alignItems: "center",
    gap: 8
  },
  identity: {
    flex: 1,
    minWidth: 0,
    paddingRight: 10
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
  experiencePill: {
    marginTop: 5,
    alignSelf: "flex-start",
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: radius.pill,
    backgroundColor: "#e8f5ed"
  },
  experienceLabel: {
    color: colors.green,
    fontSize: 9,
    fontWeight: "900",
    letterSpacing: 0.5
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
  demoPill: {
    marginTop: 5,
    alignSelf: "flex-start",
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: radius.pill,
    backgroundColor: "#fff3cd"
  },
  demoLabel: {
    color: "#7a4d00",
    fontSize: 9,
    fontWeight: "900",
    letterSpacing: 0.7
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
