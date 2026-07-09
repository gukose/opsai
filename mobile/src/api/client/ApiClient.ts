export type ApiRequestOptions = {
  headers?: Record<string, string>;
  timeoutMs?: number;
  correlationId?: string;
  skipAuth?: boolean;
};

export interface ApiClient {
  get<T>(path: string, options?: ApiRequestOptions): Promise<T>;

  post<TResponse, TBody>(
    path: string,
    body: TBody,
    options?: ApiRequestOptions
  ): Promise<TResponse>;
}

export type AccessTokenProvider = () => string | null | undefined;
