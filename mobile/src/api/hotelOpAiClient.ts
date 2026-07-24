import {
  ApiError as SdkApiError,
  HotelOpAiClient,
  createHotelOpAiClient
} from "@hotelopai/api-client";
import type { ApiResponse } from "@hotelopai/api-client";

import { appApiBaseUrl } from "../config/appConfig";
import { AppApiError } from "./client/AppApiError";
import type { ProblemDetails } from "./client/AppApiError";
import { createCorrelationId } from "./client/correlationId";
import { defaultGetRetryPolicy, retryDelayMs, shouldRetryRequest, sleep } from "./client/retryPolicy";
import type { HttpMethod, RetryPolicy } from "./client/retryPolicy";

export type MobileSdkClientOptions = {
  baseUrl?: string;
  timeoutMs?: number;
  accessTokenProvider?: () => string | null | undefined;
  refreshAccessToken?: () => Promise<string | null>;
  correlationIdProvider?: () => string;
  delay?: (ms: number) => Promise<void>;
};

export type MobileSdkRequestOptions = {
  timeoutMs?: number;
  correlationId?: string;
  retry?: RetryPolicy;
  authenticated?: boolean;
};

export class MobileHotelOpAiClient {
  private readonly baseUrl: string;
  private readonly accessTokenProvider?: () => string | null | undefined;
  private readonly timeoutMs: number;
  private readonly refreshAccessToken?: () => Promise<string | null>;
  private readonly correlationIdProvider: () => string;
  private readonly delay: (ms: number) => Promise<void>;

  constructor(options: MobileSdkClientOptions = {}) {
    this.timeoutMs = options.timeoutMs ?? 15_000;
    this.baseUrl = options.baseUrl ?? appApiBaseUrl;
    this.accessTokenProvider = options.accessTokenProvider;
    this.refreshAccessToken = options.refreshAccessToken;
    this.correlationIdProvider = options.correlationIdProvider ?? createCorrelationId;
    this.delay = options.delay ?? sleep;
  }

  async call<T>(
    method: HttpMethod,
    operation: (client: HotelOpAiClient, signal: AbortSignal) => Promise<ApiResponse<T>>,
    options?: MobileSdkRequestOptions
  ): Promise<T> {
    const policy = options?.retry ?? defaultGetRetryPolicy;
    let attempt = 0;
    let didRefresh = false;
    let accessTokenOverride: string | null = null;

    while (true) {
      const correlationId = options?.correlationId ?? this.correlationIdProvider();
      try {
        const response = await this.callOnce(
          operation,
          correlationId,
          accessTokenOverride,
          options?.authenticated ?? true,
          options?.timeoutMs ?? this.timeoutMs
        );
        this.warnOnUnexpectedApiVersion(response.apiVersion);
        return response.data;
      } catch (error) {
        const appError = this.toAppApiError(error, correlationId);
        if (
          appError.status === 401 &&
          !didRefresh &&
          this.refreshAccessToken &&
          method === "GET"
        ) {
          didRefresh = true;
          const refreshed = await this.refreshAccessToken();
          if (refreshed) {
            accessTokenOverride = refreshed;
            continue;
          }
        }

        attempt += 1;
        if (!shouldRetryRequest({ method, error: appError, attempt, policy })) {
          throw appError;
        }
        await this.delay(retryDelayMs(attempt, policy));
      }
    }
  }

  private async callOnce<T>(
    operation: (client: HotelOpAiClient, signal: AbortSignal) => Promise<ApiResponse<T>>,
    correlationId: string,
    accessTokenOverride: string | null,
    authenticated: boolean,
    timeoutMs: number
  ): Promise<ApiResponse<T>> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
      return await operation(this.createSdkClient(correlationId, accessTokenOverride, authenticated), controller.signal);
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        throw new AppApiError("Request timed out", {
          kind: "timeout",
          correlationId,
          cause: error
        });
      }
      throw error;
    } finally {
      clearTimeout(timeout);
    }
  }

  private createSdkClient(
    correlationId: string,
    accessTokenOverride: string | null,
    authenticated: boolean
  ): HotelOpAiClient {
    return createHotelOpAiClient({
      baseUrl: this.baseUrl,
      accessToken: () => authenticated ? accessTokenOverride ?? this.accessTokenProvider?.() ?? null : null,
      fetchImpl: async (input, init) => {
        const headers = new Headers(init?.headers);
        headers.set("X-Correlation-Id", correlationId);
        return fetch(input, {
          ...init,
          headers
        });
      }
    });
  }

  private toAppApiError(error: unknown, correlationId: string): AppApiError {
    if (error instanceof AppApiError) {
      return error;
    }

    if (error instanceof SdkApiError) {
      const problem = toProblemDetails(error.problem);
      return new AppApiError(problem?.title ?? error.message, {
        kind: "problem-details",
        status: error.status,
        problem,
        correlationId,
        apiVersion: error.apiVersion,
        cause: error
      });
    }

    if (error instanceof DOMException && error.name === "AbortError") {
      return new AppApiError("Request timed out", {
        kind: "timeout",
        correlationId,
        cause: error
      });
    }

    return new AppApiError("Network request failed", {
      kind: "network",
      correlationId,
      cause: error
    });
  }

  private warnOnUnexpectedApiVersion(apiVersion: string | null): void {
    if (!apiVersion || apiVersion === "v1" || !__DEV__) {
      return;
    }
    console.warn("[api] Unexpected API version header", apiVersion);
  }
}

export function createMobileHotelOpAiClient(options: MobileSdkClientOptions = {}): MobileHotelOpAiClient {
  return new MobileHotelOpAiClient(options);
}

function toProblemDetails(problem: unknown): ProblemDetails | undefined {
  if (!problem || typeof problem !== "object") {
    return undefined;
  }
  return problem as ProblemDetails;
}
