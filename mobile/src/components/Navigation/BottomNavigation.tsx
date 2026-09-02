import { Pressable, StyleSheet, Text, useWindowDimensions, View } from "react-native";
import {
  CheckSquare,
  Home,
  Mic,
  Settings,
  User
} from "lucide-react-native";
import { ComponentType } from "react";
import { LucideProps } from "lucide-react-native";

import { getCurrentUserDisplayName } from "../../auth/currentUserHelpers";
import { getCurrentUserPermissionCodes } from "../../auth/currentUserHelpers";
import { canOpenAdministration } from "../Admin/adminLogic";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { colors, spacing, typography } from "../../theme/tokens";
import { resolveResponsiveLayout } from "../../layout/responsiveLayout";
import { resolveExperienceMode } from "../../auth/experienceMode";

type NavigationItem = {
  key: BottomNavigationKey;
  icon: ComponentType<LucideProps>;
  label: string;
};

const baseItems: NavigationItem[] = [
  { key: "home", icon: Home, label: "Overview" },
  { key: "tasks", icon: CheckSquare, label: "My Tasks" },
  { key: "operations", icon: Settings, label: "Operations" },
  { key: "profile", icon: User, label: "Profile" }
];

export type BottomNavigationKey = "home" | "tasks" | "assistant" | "knowledge" | "operations" | "profile";

type BottomNavigationProps = {
  activeKey: BottomNavigationKey;
  currentUser?: CurrentUserSnapshot | null;
  onSelect?: (key: BottomNavigationKey) => void;
  onAssistantPress?: () => void;
};

export function BottomNavigation({ activeKey, currentUser, onSelect, onAssistantPress }: BottomNavigationProps) {
  const { width } = useWindowDimensions();
  const desktop = resolveResponsiveLayout(width).mode === "desktop";
  const displayName = getCurrentUserDisplayName(currentUser ?? null);
  const mode = resolveExperienceMode(currentUser ?? null);
  const items = mode === "FRONTLINE_SIMPLE"
    ? baseItems.filter((item) => item.key === "tasks" || item.key === "profile")
    : baseItems.filter((item) => item.key !== "operations" || canOpenAdministration(getCurrentUserPermissionCodes(currentUser ?? null)));
  const visibleItems = items.map((item) => item.key === "home"
    ? { ...item, label: mode === "FRONTLINE_SIMPLE" ? "Home" : "Overview" }
    : item);
  const centerSplitIndex = visibleItems.length <= 2 ? 1 : 2;

  return (
    <View style={[styles.nav, desktop ? styles.navDesktop : null]}>
      {visibleItems.slice(0, centerSplitIndex).map((item) => {
        const Icon = item.icon;
        const isActive = item.key === activeKey;

        return (
          <Pressable
            key={item.key}
            accessibilityRole="button"
            accessibilityLabel={item.key === "profile" ? `${item.label}, ${displayName}` : item.label}
            onPress={() => onSelect?.(item.key)}
            style={({ pressed }) => [
              styles.item,
              desktop && styles.itemDesktop,
              pressed && styles.pressed,
              isActive && styles.activeItem
            ]}
          >
            <Icon
              color={isActive ? colors.green : colors.nav}
              size={15}
              strokeWidth={2.15}
            />
            <Text style={[styles.label, isActive && styles.active]}>{item.label}</Text>
          </Pressable>
        );
      })}
      <Pressable accessibilityRole="button" accessibilityLabel="Open voice assistant" onPress={onAssistantPress} style={styles.micButton}>
        <Mic color="#ffffff" size={25} strokeWidth={2.5} />
      </Pressable>
      {visibleItems.slice(centerSplitIndex).map((item) => {
        const Icon = item.icon;
        const isActive = item.key === activeKey;
        return <Pressable key={item.key} accessibilityRole="button" accessibilityLabel={item.key === "profile" ? `${item.label}, ${displayName}` : item.label} onPress={() => onSelect?.(item.key)} style={({ pressed }) => [styles.item, desktop && styles.itemDesktop, pressed && styles.pressed, isActive && styles.activeItem]}><Icon color={isActive ? colors.green : colors.nav} size={15} strokeWidth={2.15} /><Text style={[styles.label, isActive && styles.active]}>{item.label}</Text></Pressable>;
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  nav: {
    minHeight: 54,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-around",
    paddingHorizontal: 11,
    backgroundColor: colors.surface
  },
  item: {
    flex: 1,
    minWidth: 0,
    maxWidth: 92,
    height: 39,
    alignItems: "center",
    justifyContent: "center"
  },
  micButton: {
    width: 60,
    height: 60,
    marginTop: -23,
    marginHorizontal: 7,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 30,
    backgroundColor: colors.green,
    borderWidth: 4,
    borderColor: colors.background,
    shadowColor: "#000",
    shadowOpacity: 0.2,
    shadowRadius: 7,
    shadowOffset: { width: 0, height: 3 },
    elevation: 5
  },
  activeItem: {
    opacity: 1
  },
  pressed: {
    opacity: 0.72
  },
  label: {
    marginTop: 1,
    color: colors.nav,
    fontSize: 9,
    fontWeight: "900"
  },
  navDesktop: {
    alignSelf: "center",
    width: "100%",
    maxWidth: 760,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: 18
  },
  itemDesktop: {
    maxWidth: 120
  },
  active: {
    color: colors.green
  }
});
