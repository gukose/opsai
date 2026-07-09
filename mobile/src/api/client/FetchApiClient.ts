import { AppApiError, isProblemDetails, ProblemDetails } from "./AppApiError";
import { AccessTokenProvider, ApiClient, ApiRequestOptions } from "./ApiClient";
import { createCorrelationId } from "./correlationId";

export type FetchApiClientOptions = {
  baseUrl: string;
  timeoutMs?: number;
  accessTokenProvider?: AccessTokenProvider;
  correlationIdProvider?: () => string;
};

export class FetchApiClient implements ApiClient {
  private readonly timeoutMs: number;
  private readonly accessTokenProvider?: AccessTokenProvider;
  private readonly correlationIdProvider: () => string;

  constructor(
    private readonly options: FetchApiClientOptions
  ) {
    this.timeoutMs = options.timeoutMs ?? 15_000;
    this.accessTokenProvider = options.accessTokenProvider;
    this.correlationIdProvider = options.correlationIdProvider ?? createCorrelationId;
  }

  async get<T>(path: string, options?: ApiRequestOptions): Promise<T> {
    return this.request<T>(path, { method: "GET" }, options);
  }

  async post<TResponse, TBody>(
    path: string,
    body: TBody,
    options?: ApiRequestOptions
  ): Promise<TResponse> {
    return this.request<TResponse>(
      path,
      {
        method: "POST",
        body: JSON.stringify(body)
      },
      options
    );
  }

  private async request<T>(
    path: string,
    init: RequestInit,
    options?: ApiRequestOptions
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
          ...(this.authorizationHeader(options) ?? {}),
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

  private authorizationHeader(options?: ApiRequestOptions): Record<string, string> | null {
    if (options?.skipAuth) {
      return null;
    }

    const token = this.accessTokenProvider?.();
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
