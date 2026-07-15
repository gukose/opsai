import type { DashboardRecentNotification } from "../../dashboard/types";

export function shouldShowUnreadBadge(unreadCount: number | null | undefined): boolean {
  return typeof unreadCount === "number" && unreadCount > 0;
}

export function formatUnreadBadge(unreadCount: number | null | undefined): string {
  if (!shouldShowUnreadBadge(unreadCount)) {
    return "";
  }

  const count = unreadCount ?? 0;
  return count > 99 ? "99+" : String(count);
}

export function visibleRecentNotifications(
  notifications: DashboardRecentNotification[] | null | undefined
): DashboardRecentNotification[] {
  return (notifications ?? []).filter((notification) => Boolean(notification.id && notification.title));
}
