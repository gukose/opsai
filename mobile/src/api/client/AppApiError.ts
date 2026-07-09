export type ProblemDetails = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  [key: string]: unknown;
};

export type AppApiErrorKind = "problem-details" | "network" | "timeout" | "unknown";

export class AppApiError extends Error {
  readonly kind: AppApiErrorKind;
  readonly status?: number;
  readonly problem?: ProblemDetails;
  readonly correlationId?: string;
  readonly cause?: unknown;

  constructor(
    message: string,
    options: {
      kind: AppApiErrorKind;
      status?: number;
      problem?: ProblemDetails;
      correlationId?: string;
      cause?: unknown;
    }
  ) {
    super(message);
    this.name = "AppApiError";
    this.kind = options.kind;
    this.status = options.status;
    this.problem = options.problem;
    this.correlationId = options.correlationId;
    this.cause = options.cause;
  }
}

export function isProblemDetails(value: unknown): value is ProblemDetails {
  if (!value || typeof value !== "object") {
    return false;
  }

  return "title" in value || "status" in value || "detail" in value || "type" in value;
}

export function getAppApiErrorMessage(error: unknown): string {
  if (error instanceof AppApiError) {
    if (error.problem?.detail && error.problem.detail.trim()) {
      return error.problem.detail;
    }

    if (error.problem?.title && error.problem.title.trim()) {
      return error.problem.title;
    }

    switch (error.kind) {
      case "timeout":
        return "The request timed out. Check your network connection and try again.";
      case "network":
        return "Unable to reach the server. Check your connection and try again.";
      default:
        return error.message || "Request failed.";
    }
  }

  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }

  return "Something went wrong. Please try again.";
}
