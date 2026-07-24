export {
  ApiError,
  HotelOpAiClient,
  createHotelOpAiClient,
  type AccessTokenProvider,
  type ApiResponse,
  type HotelOpAiClientConfig,
  type ProblemDetail
} from "./client.js";
export * from "./generated/operations.js";
export type { components, operations, paths } from "./generated/schema.js";
