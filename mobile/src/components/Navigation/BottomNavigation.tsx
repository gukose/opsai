import { StyleSheet, Text, View } from "react-native";
import {
  CheckSquare,
  Home,
  Settings,
  Sparkles,
  User
} from "lucide-react-native";
import { ComponentType } from "react";
import { LucideProps } from "lucide-react-native";

import { colors, spacing, typography } from "../../theme/tokens";

type NavigationItem = {
  key: string;
  icon: ComponentType<LucideProps>;
  label: string;
  active: boolean;
};

const items: NavigationItem[] = [
  { key: "home", icon: Home, label: "Home", active: true },
  { key: "tasks", icon: CheckSquare, label: "My Tasks", active: false },
  { key: "assistant", icon: Sparkles, label: "Assistant", active: false },
  { key: "operations", icon: Settings, label: "Operations", active: false },
  { key: "profile", icon: User, label: "Profile", active: false }
];

export function BottomNavigation() {
  return (
    <View style={styles.nav}>
      {items.map((item) => {
        const Icon = item.icon;

        return (
          <View key={item.key} style={styles.item}>
            <Icon
              color={item.active ? colors.green : colors.nav}
              size={15}
              strokeWidth={2.15}
            />
            <Text style={[styles.label, item.active && styles.active]}>{item.label}</Text>
          </View>
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
