import type { components } from "./generated/schema.js";

export type ProblemDetail = components["schemas"]["ProblemDetail"];

export type AccessTokenProvider = () => string | null | undefined | Promise<string | null | undefined>;

export interface HotelOpAiClientConfig {
  readonly baseUrl: string;
  readonly accessToken?: AccessTokenProvider;
  readonly fetchImpl?: typeof fetch;
}

export interface ApiResponse<T> {
  readonly data: T;
  readonly status: number;
  readonly headers: Headers;
  readonly apiVersion: string | null;
}

export interface ApiRequestOptions<TBody = unknown, TQuery = unknown, TPath = unknown> {
  readonly method: string;
  readonly path: string;
  readonly auth: boolean;
  readonly body?: TBody;
  readonly query?: TQuery;
  readonly pathParams?: TPath;
  readonly signal?: AbortSignal;
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | undefined;
  readonly headers: Headers;
  readonly apiVersion: string | null;

  constructor(message: string, options: { status: number; problem?: ProblemDetail; headers: Headers }) {
    super(message);
    this.name = "ApiError";
    this.status = options.status;
    this.problem = options.problem;
    this.headers = options.headers;
    this.apiVersion = options.headers.get("X-API-Version");
  }
}

export class HotelOpAiClient {
  private accessToken: AccessTokenProvider | undefined;
  private readonly baseUrl: string;
  private readonly fetchImpl: typeof fetch;

  constructor(config: HotelOpAiClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/+$/, "");
    this.accessToken = config.accessToken;
    this.fetchImpl = config.fetchImpl ?? globalThis.fetch;
  }

  setAccessToken(provider: AccessTokenProvider | string | null | undefined): void {
    if (typeof provider === "string") {
      this.accessToken = () => provider;
    } else {
      this.accessToken = provider ?? undefined;
    }
  }

  clearAccessToken(): void {
    this.accessToken = undefined;
  }

  async request<TResponse, TBody = unknown, TQuery = unknown, TPath = unknown>(
    options: ApiRequestOptions<TBody, TQuery, TPath>
  ): Promise<ApiResponse<TResponse>> {
    const headers = new Headers();
    headers.set("Accept", "application/json");

    let body: BodyInit | undefined;
    if (options.body !== undefined) {
      headers.set("Content-Type", "application/json");
      body = JSON.stringify(options.body);
    }

    if (options.auth) {
      const token = await this.accessToken?.();
      if (token) {
        headers.set("Authorization", `Bearer ${token}`);
      }
    }

    const requestInit: RequestInit = {
      method: options.method,
      headers
    };
    if (body !== undefined) {
      requestInit.body = body;
    }
    if (options.signal !== undefined) {
      requestInit.signal = options.signal;
    }

    const response = await this.fetchImpl(this.url(options.path, options.pathParams, options.query), requestInit);

    const data = await parseResponseBody(response);
    if (!response.ok) {
      const errorOptions: { status: number; problem?: ProblemDetail; headers: Headers } = {
        status: response.status,
        headers: response.headers
      };
      if (isProblemDetail(data)) {
        errorOptions.problem = data;
      }
      throw new ApiError(`HTTP ${response.status}`, {
        ...errorOptions
      });
    }

    return {
      data: data as TResponse,
      status: response.status,
      headers: response.headers,
      apiVersion: response.headers.get("X-API-Version")
    };
  }

  private url(pathTemplate: string, pathParams: unknown, query: unknown): string {
    const path = applyPathParams(pathTemplate, pathParams);
    const url = new URL(`${this.baseUrl}${path}`);
    appendQuery(url, query);
    return url.toString();
  }
}

export function createHotelOpAiClient(config: HotelOpAiClientConfig): HotelOpAiClient {
  return new HotelOpAiClient(config);
}

async function parseResponseBody(response: Response): Promise<unknown> {
  if (response.status === 204) {
    return undefined;
  }
  const text = await response.text();
  if (!text) {
    return undefined;
  }
  const contentType = response.headers.get("Content-Type") ?? "";
  if (contentType.includes("json")) {
    return JSON.parse(text) as unknown;
  }
  return text;
}

function isProblemDetail(value: unknown): value is ProblemDetail {
  if (!value || typeof value !== "object") {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate.status === "number" && typeof candidate.title === "string";
}

function applyPathParams(pathTemplate: string, pathParams: unknown): string {
  if (!pathParams || typeof pathParams !== "object") {
    return pathTemplate;
  }
  return Object.entries(pathParams as Record<string, unknown>).reduce(
    (path, [key, value]) => path.replace(`{${key}}`, encodeURIComponent(String(value))),
    pathTemplate
  );
}

function appendQuery(url: URL, query: unknown): void {
  if (!query || typeof query !== "object") {
    return;
  }
  Object.entries(query as Record<string, unknown>).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    if (Array.isArray(value)) {
      value.forEach((item) => url.searchParams.append(key, String(item)));
    } else {
      url.searchParams.set(key, String(value));
    }
  });
}
