import { Platform } from "react-native";

export type AppEnvironment = "local" | "test" | "prod";

function readFlag(name: string): string | undefined {
  return process.env[name]?.trim();
}

function normalizeEnvironment(value: string | undefined): AppEnvironment {
  switch (value?.toLowerCase()) {
    case "test":
      return "test";
    case "prod":
    case "production":
      return "prod";
    case "local":
    case "dev":
    case "development":
    default:
      return "local";
  }
}

export const appEnvironment: AppEnvironment = normalizeEnvironment(readFlag("EXPO_PUBLIC_APP_ENV"));

export const appApiBaseUrl = resolveApiBaseUrl();

export const isWebPlatform = Platform.OS === "web";

function resolveApiBaseUrl(): string {
  const explicitBaseUrl = readFlag("EXPO_PUBLIC_API_BASE_URL");
  if (explicitBaseUrl) {
    return explicitBaseUrl;
  }

  // Physical devices cannot reach a developer laptop through localhost.
  // Use EXPO_PUBLIC_API_BASE_URL with a LAN IP or tunnel URL when testing on-device.
  if (Platform.OS === "android") {
    return "http://10.0.2.2:8080";
  }

  return "http://localhost:8080";
}

export function getApiBaseUrlForDocumentation(): string {
  return appApiBaseUrl;
}
