import { Pressable, StyleSheet, Text, View } from "react-native";
import {
  CheckSquare,
  Home,
  Settings,
  Sparkles,
  User
} from "lucide-react-native";
import { ComponentType } from "react";
import { LucideProps } from "lucide-react-native";

import { getCurrentUserDisplayName } from "../../auth/currentUserHelpers";
import { CurrentUserSnapshot } from "../../session/sessionTypes";
import { colors, spacing, typography } from "../../theme/tokens";

type NavigationItem = {
  key: BottomNavigationKey;
  icon: ComponentType<LucideProps>;
  label: string;
};

const items: NavigationItem[] = [
  { key: "home", icon: Home, label: "Home" },
  { key: "tasks", icon: CheckSquare, label: "My Tasks" },
  { key: "assistant", icon: Sparkles, label: "Assistant" },
  { key: "operations", icon: Settings, label: "Operations" },
  { key: "profile", icon: User, label: "Profile" }
];

export type BottomNavigationKey = "home" | "tasks" | "assistant" | "operations" | "profile";

type BottomNavigationProps = {
  activeKey: BottomNavigationKey;
  currentUser?: CurrentUserSnapshot | null;
  onSelect?: (key: BottomNavigationKey) => void;
};

export function BottomNavigation({ activeKey, currentUser, onSelect }: BottomNavigationProps) {
  const displayName = getCurrentUserDisplayName(currentUser ?? null);

  return (
    <View style={styles.nav}>
      {items.map((item) => {
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
    </View>
  );
}

const styles = StyleSheet.create({
  nav: {
    height: 47,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-around",
    paddingHorizontal: 11,
    backgroundColor: colors.surface
  },
  item: {
    width: 53,
    height: 39,
    alignItems: "center",
    justifyContent: "center"
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
    fontSize: typography.tiny,
    fontWeight: "900"
  },
  active: {
    color: colors.green
  }
});
