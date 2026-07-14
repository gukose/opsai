export type ApiRequestOptions = {
  headers?: Record<string, string>;
  timeoutMs?: number;
  correlationId?: string;
  skipAuth?: boolean;
  retry?: {
    maxRetries: number;
    delaysMs: number[];
  };
};

export interface ApiClient {
  get<T>(path: string, options?: ApiRequestOptions): Promise<T>;

  post<TResponse, TBody>(
    path: string,
    body: TBody,
    options?: ApiRequestOptions
  ): Promise<TResponse>;

  put?<TResponse, TBody>(
    path: string,
    body: TBody,
    options?: ApiRequestOptions
  ): Promise<TResponse>;

  patch?<TResponse, TBody>(
    path: string,
    body: TBody,
    options?: ApiRequestOptions
  ): Promise<TResponse>;

  delete?<TResponse>(path: string, options?: ApiRequestOptions): Promise<TResponse>;
}

export type AccessTokenProvider = () => string | null | undefined;
