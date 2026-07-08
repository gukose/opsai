export const colors = {
  background: "#ffffff",
  surface: "#ffffff",
  surfaceMuted: "#f7f8fa",
  cardBorder: "#e5eaf2",
  divider: "#eef1f6",
  text: "#071224",
  textMuted: "#64748b",
  textSubtle: "#94a3b8",
  green: "#009b4d",
  greenSoft: "#dff6e8",
  greenBorder: "#bfeacf",
  red: "#ef4444",
  redSoft: "#fff1f2",
  blue: "#2563eb",
  amber: "#f6b73c",
  purple: "#7c3aed",
  nav: "#253858"
} as const;

export const spacing = {
  xxs: 2,
  xs: 4,
  sm: 7,
  md: 9,
  lg: 13,
  xl: 17,
  xxl: 24
} as const;

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 22,
  pill: 999
} as const;

export const typography = {
  title: 15,
  subtitle: 10,
  body: 10,
  caption: 8,
  tiny: 7
} as const;

export const shadow = {
  soft: {
    shadowColor: "#0f172a",
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.025,
    shadowRadius: 10,
    elevation: 1
  },
  card: {
    shadowColor: "#0f172a",
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.035,
    shadowRadius: 10,
    elevation: 2
  }
} as const;
