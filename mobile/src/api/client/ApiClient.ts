export interface ApiClient {
  get<T>(path: string): Promise<T>;

  post<TResponse, TBody>(path: string, body: TBody): Promise<TResponse>;
}
