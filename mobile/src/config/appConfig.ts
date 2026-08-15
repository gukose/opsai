import { Platform } from "react-native";

export type AppEnvironment = "local" | "test" | "demo" | "prod";

function readFlag(name: string): string | undefined {
  return process.env[name]?.trim();
}

function normalizeEnvironment(value: string | undefined): AppEnvironment {
  switch (value?.toLowerCase()) {
    case "test":
      return "test";
    case "demo":
      return "demo";
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

export const appApiBaseUrl = resolveApiBaseUrlForEnvironment(
  appEnvironment,
  readFlag("EXPO_PUBLIC_API_BASE_URL"),
  Platform.OS
);

export const isWebPlatform = Platform.OS === "web";

export function resolveApiBaseUrlForEnvironment(
  environment: AppEnvironment,
  explicitBaseUrl: string | undefined,
  platform: string
): string {
  if (explicitBaseUrl) {
    return explicitBaseUrl.replace(/\/+$/, "");
  }

  if (environment === "demo" || environment === "prod") {
    throw new Error("EXPO_PUBLIC_API_BASE_URL is required outside local/test environments");
  }

  // Physical devices cannot reach a developer laptop through localhost.
  // Use EXPO_PUBLIC_API_BASE_URL with a LAN IP or tunnel URL when testing on-device.
  if (platform === "android") {
    return "http://10.0.2.2:8080";
  }

  return "http://localhost:8080";
}

export const isDemoEnvironment = appEnvironment === "demo";

export function shouldShowDemoIndicator(environment: AppEnvironment): boolean {
  return environment === "demo";
}

export function getApiBaseUrlForDocumentation(): string {
  return appApiBaseUrl;
}
