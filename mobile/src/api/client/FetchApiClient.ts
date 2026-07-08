import { ApiClient } from "./ApiClient";

export class FetchApiClient implements ApiClient {
  constructor(private readonly baseUrl: string) {}

  async get<T>(path: string): Promise<T> {
    return this.request<T>(path, { method: "GET" });
  }

  async post<TResponse, TBody>(path: string, body: TBody): Promise<TResponse> {
    return this.request<TResponse>(path, {
      method: "POST",
      body: JSON.stringify(body)
    });
  }

  private async request<T>(
    path: string,
    init: RequestInit
  ): Promise<T> {
    const response = await fetch(this.url(path), {
      headers: {
        "Content-Type": "application/json",
        ...(init.headers || {})
      },
      ...init
    });

    if (!response.ok) {
      throw new Error(await this.readError(response));
    }

    return (await response.json()) as T;
  }

  private url(path: string): string {
    if (path.startsWith("http")) {
      return path;
    }

    return `${this.baseUrl.replace(/\/$/, "")}${path}`;
  }

  private async readError(response: Response): Promise<string> {
    const text = await response.text();
    return text || `Request failed with status ${response.status}`;
  }
}
