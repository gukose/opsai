export type AssistantDataSourceMode = "static-mock" | "local-mock" | "backend";

function readFlag(name: string): string | undefined {
  return process.env[name]?.trim();
}

const backendEnabled =
  [readFlag("USE_BACKEND_ASSISTANT"), readFlag("EXPO_PUBLIC_USE_BACKEND_ASSISTANT")]
    .filter(Boolean)
    .some((value) => value?.toLowerCase() === "true");

const rawMode = readFlag("EXPO_PUBLIC_ASSISTANT_DATA_SOURCE")?.toLowerCase();

function normalizeMode(value: string | undefined): AssistantDataSourceMode | null {
  switch (value) {
    case "backend":
      return "backend";
    case "static":
    case "static-mock":
    case "mock":
      return "static-mock";
    case "local":
    case "local-mock":
    case "interactive":
    case "interactive-mock":
      return "local-mock";
    default:
      return null;
  }
}

export const assistantDataSourceMode: AssistantDataSourceMode =
  backendEnabled ? "backend" : normalizeMode(rawMode) ?? "local-mock";

export const assistantBackendEnabled = assistantDataSourceMode === "backend";
export const assistantLocalMockEnabled = assistantDataSourceMode === "local-mock";
export const assistantStaticMockEnabled = assistantDataSourceMode === "static-mock";

export const assistantApiBaseUrl =
  process.env.EXPO_PUBLIC_ASSISTANT_API_BASE_URL?.trim() || "http://localhost:8080";

export const assistantDefaultHotelId =
  process.env.EXPO_PUBLIC_ASSISTANT_DEFAULT_HOTEL_ID?.trim() || "hotel-1";

export const assistantDefaultUserId =
  process.env.EXPO_PUBLIC_ASSISTANT_DEFAULT_USER_ID?.trim() || "user-1";
