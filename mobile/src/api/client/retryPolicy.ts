export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export type RetryPolicy = {
  maxRetries: number;
  delaysMs: number[];
};

export const defaultGetRetryPolicy: RetryPolicy = {
  maxRetries: 2,
  delaysMs: [300, 900]
};

const NON_RETRYABLE_STATUS = new Set([400, 401, 403, 404, 409, 422, 429]);

export function shouldRetryRequest(input: {
  method: HttpMethod;
  error: unknown;
  attempt: number;
  policy?: RetryPolicy;
}): boolean {
  const policy = input.policy ?? defaultGetRetryPolicy;
  if (input.method !== "GET" || input.attempt > policy.maxRetries) {
    return false;
  }

  if (!isRetryableErrorShape(input.error)) {
    return false;
  }

  if (input.error.status && NON_RETRYABLE_STATUS.has(input.error.status)) {
    return false;
  }

  return input.error.kind === "network" || input.error.kind === "timeout";
}

export function retryDelayMs(attempt: number, policy: RetryPolicy = defaultGetRetryPolicy): number {
  return policy.delaysMs[Math.max(0, attempt - 1)] ?? policy.delaysMs[policy.delaysMs.length - 1] ?? 0;
}

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isRetryableErrorShape(error: unknown): error is { kind: string; status?: number } {
  return Boolean(error && typeof error === "object" && "kind" in error);
}
