import { ComponentType } from "react";
import { Pressable, StyleSheet, ViewStyle } from "react-native";
import { LucideProps } from "lucide-react-native";

import { colors, radius } from "../../theme/tokens";

type IconButtonProps = {
  icon: ComponentType<LucideProps>;
  label: string;
  onPress?: () => void;
  style?: ViewStyle;
  color?: string;
  size?: number;
};

export function IconButton({
  icon: Icon,
  label,
  onPress,
  style,
  color = colors.blue,
  size = 15
}: IconButtonProps) {
  return (
    <Pressable
      accessibilityLabel={label}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        style,
        pressed && styles.pressed
      ]}
    >
      <Icon color={color} size={size} strokeWidth={2.2} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: 28,
    height: 28,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceMuted
  },
  pressed: {
    opacity: 0.72
  }
});
