export type ResponsiveLayoutMode = "phone" | "tablet" | "desktop";

export type ResponsiveLayout = {
  mode: ResponsiveLayoutMode;
  shellMaxWidth: number;
  contentGutter: number;
};

export function resolveResponsiveLayout(width: number): ResponsiveLayout {
  if (width < 600) {
    return { mode: "phone", shellMaxWidth: width, contentGutter: 0 };
  }
  if (width <= 1024) {
    return { mode: "tablet", shellMaxWidth: width, contentGutter: 12 };
  }
  return { mode: "desktop", shellMaxWidth: 1360, contentGutter: 24 };
}
