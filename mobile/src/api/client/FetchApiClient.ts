import { AppApiError, isProblemDetails } from "./AppApiError";
import type { ProblemDetails } from "./AppApiError";
import type { AccessTokenProvider, ApiClient, ApiRequestOptions } from "./ApiClient";
import { createCorrelationId } from "./correlationId";
import { defaultGetRetryPolicy, retryDelayMs, shouldRetryRequest, sleep } from "./retryPolicy";
import type { HttpMethod } from "./retryPolicy";

export type FetchApiClientOptions = {
  baseUrl: string;
  timeoutMs?: number;
  accessTokenProvider?: AccessTokenProvider;
  correlationIdProvider?: () => string;
  refreshAccessToken?: () => Promise<string | null>;
  delay?: (ms: number) => Promise<void>;
};

export class FetchApiClient implements ApiClient {
  private readonly options: FetchApiClientOptions;
  private readonly timeoutMs: number;
  private readonly accessTokenProvider?: AccessTokenProvider;
  private readonly correlationIdProvider: () => string;
  private readonly refreshAccessToken?: () => Promise<string | null>;
  private readonly delay: (ms: number) => Promise<void>;

  constructor(options: FetchApiClientOptions) {
    this.options = options;
    this.timeoutMs = options.timeoutMs ?? 15_000;
    this.accessTokenProvider = options.accessTokenProvider;
    this.correlationIdProvider = options.correlationIdProvider ?? createCorrelationId;
    this.refreshAccessToken = options.refreshAccessToken;
    this.delay = options.delay ?? sleep;
  }

  async get<T>(path: string, options?: ApiRequestOptions): Promise<T> {
    return this.requestWithRetry<T>("GET", path, { method: "GET" }, options);
  }

  async post<TResponse, TBody>(
    path: string,
    body: TBody,
    options?: ApiRequestOptions
  ): Promise<TResponse> {
    return this.requestWithRetry<TResponse>(
      "POST",
      path,
      {
        method: "POST",
        body: JSON.stringify(body)
      },
      options
    );
  }

  async put<TResponse, TBody>(
    path: string,
    body: TBody,
    options?: ApiRequestOptions
  ): Promise<TResponse> {
    return this.requestWithRetry<TResponse>("PUT", path, { method: "PUT", body: JSON.stringify(body) }, options);
  }

  async patch<TResponse, TBody>(
    path: string,
    body: TBody,
    options?: ApiRequestOptions
  ): Promise<TResponse> {
    return this.requestWithRetry<TResponse>("PATCH", path, { method: "PATCH", body: JSON.stringify(body) }, options);
  }

  async delete<TResponse>(path: string, options?: ApiRequestOptions): Promise<TResponse> {
    return this.requestWithRetry<TResponse>("DELETE", path, { method: "DELETE" }, options);
  }

  private async requestWithRetry<T>(
    method: HttpMethod,
    path: string,
    init: RequestInit,
    options?: ApiRequestOptions
  ): Promise<T> {
    const policy = options?.retry ?? defaultGetRetryPolicy;
    let attempt = 0;
    let didRefresh = false;
    let refreshedAccessToken: string | null = null;

    while (true) {
      try {
        return await this.request<T>(path, init, options, refreshedAccessToken);
      } catch (error) {
        if (
          error instanceof AppApiError &&
          error.status === 401 &&
          !options?.skipAuth &&
          !didRefresh &&
          this.refreshAccessToken &&
          method === "GET"
        ) {
          didRefresh = true;
          const refreshed = await this.refreshAccessToken();
          if (refreshed) {
            refreshedAccessToken = refreshed;
            continue;
          }
        }

        attempt += 1;
        if (!shouldRetryRequest({ method, error, attempt, policy })) {
          throw error;
        }
        await this.delay(retryDelayMs(attempt, policy));
      }
    }
  }

  private async request<T>(
    path: string,
    init: RequestInit,
    options?: ApiRequestOptions,
    accessTokenOverride?: string | null
  ): Promise<T> {
    const controller = new AbortController();
    const timeoutMs = options?.timeoutMs ?? this.timeoutMs;
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    const correlationId = options?.correlationId ?? this.correlationIdProvider();

    try {
      const response = await fetch(this.url(path), {
        ...init,
        signal: controller.signal,
        headers: {
          "Content-Type": "application/json",
          "X-Correlation-Id": correlationId,
          ...(this.authorizationHeader(options, accessTokenOverride) ?? {}),
          ...(init.headers || {}),
          ...(options?.headers || {})
        }
      });

      const responseText = await response.text();

      if (!response.ok) {
        throw this.toApiError(response.status, responseText, correlationId);
      }

      if (!responseText) {
        return undefined as T;
      }

      return JSON.parse(responseText) as T;
    } catch (error) {
      throw this.toApiErrorFromTransport(error, correlationId);
    } finally {
      clearTimeout(timeout);
    }
  }

  private url(path: string): string {
    if (path.startsWith("http")) {
      return path;
    }

    return `${this.options.baseUrl.replace(/\/$/, "")}${path}`;
  }

  private authorizationHeader(options?: ApiRequestOptions, accessTokenOverride?: string | null): Record<string, string> | null {
    if (options?.skipAuth) {
      return null;
    }

    const token = accessTokenOverride ?? this.accessTokenProvider?.();
    if (!token) {
      return null;
    }

    return { Authorization: `Bearer ${token}` };
  }

  private toApiError(
    status: number,
    rawBody: string,
    correlationId: string
  ): AppApiError {
    const problem = parseProblemDetails(rawBody);

    if (problem) {
      return new AppApiError(problem.title ?? "Request failed", {
        kind: "problem-details",
        status,
        problem,
        correlationId
      });
    }

    return new AppApiError(`Request failed with status ${status}`, {
      kind: "problem-details",
      status,
      correlationId
    });
  }

  private toApiErrorFromTransport(error: unknown, correlationId: string): AppApiError {
    if (error instanceof AppApiError) {
      return error;
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
}

function parseProblemDetails(rawBody: string): ProblemDetails | null {
  if (!rawBody.trim()) {
    return null;
  }

  try {
    const parsed = JSON.parse(rawBody) as unknown;
    return isProblemDetails(parsed) ? (parsed as ProblemDetails) : null;
  } catch {
    return null;
  }
}
