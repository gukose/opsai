import assert from "node:assert/strict";
import { test } from "node:test";
import {
  ApiError,
  AuthController_login,
  AuthController_me,
  AuthController_refresh,
  NotificationController_listNotifications,
  TaskController_listTasks,
  createHotelOpAiClient
} from "../dist/index.js";

test("login sends JSON without Authorization", async () => {
  const requests = [];
  const client = createHotelOpAiClient({
    baseUrl: "https://api.example.test",
    accessToken: () => "should-not-be-used",
    fetchImpl: async (url, init) => {
      requests.push({ url, init });
      return jsonResponse({ accessToken: "access-1", accessTokenExpiresAt: "2026-07-17T00:00:00Z", refreshToken: "refresh-1", refreshTokenExpiresAt: "2026-07-18T00:00:00Z", tokenType: "Bearer" });
    }
  });

  const response = await AuthController_login(client, {
    body: {
      hotelCode: "demo",
      email: "admin@example.test",
      password: "secret",
      deviceName: "test-device"
    }
  });

  assert.equal(response.data.accessToken, "access-1");
  assert.equal(requests[0].url, "https://api.example.test/api/v1/auth/login");
  assert.equal(requests[0].init.method, "POST");
  assert.equal(requests[0].init.headers.get("Authorization"), null);
  assert.equal(requests[0].init.headers.get("Content-Type"), "application/json");
  assert.deepEqual(JSON.parse(requests[0].init.body), {
    hotelCode: "demo",
    email: "admin@example.test",
    password: "secret",
    deviceName: "test-device"
  });
});

test("protected request uses current bearer token and clearing removes Authorization", async () => {
  let token = "token-a";
  const authorizations = [];
  const client = createHotelOpAiClient({
    baseUrl: "https://api.example.test",
    accessToken: () => token,
    fetchImpl: async (_url, init) => {
      authorizations.push(init.headers.get("Authorization"));
      return jsonResponse({ id: "user-1", email: "admin@example.test", name: "Admin", hotelId: "hotel-1", hotelCode: "demo", roles: ["ADMIN"], permissions: ["AUTH_VIEW"] });
    }
  });

  await AuthController_me(client);
  token = "token-b";
  await AuthController_me(client);
  client.clearAccessToken();
  await AuthController_me(client);

  assert.deepEqual(authorizations, ["Bearer token-a", "Bearer token-b", null]);
});

test("refresh remains public according to the contract", async () => {
  const requests = [];
  const client = createHotelOpAiClient({
    baseUrl: "https://api.example.test",
    accessToken: () => "access-token",
    fetchImpl: async (url, init) => {
      requests.push({ url, init });
      return jsonResponse({ accessToken: "new-access", accessTokenExpiresAt: "2026-07-17T00:00:00Z", refreshToken: "new-refresh", refreshTokenExpiresAt: "2026-07-18T00:00:00Z", tokenType: "Bearer" });
    }
  });

  await AuthController_refresh(client, { body: { refreshToken: "refresh-token" } });

  assert.equal(requests[0].url, "https://api.example.test/api/v1/auth/refresh");
  assert.equal(requests[0].init.headers.get("Authorization"), null);
});

test("task pagination query and response model are usable", async () => {
  const requests = [];
  const client = createHotelOpAiClient({
    baseUrl: "https://api.example.test",
    accessToken: () => "access-token",
    fetchImpl: async (url, init) => {
      requests.push({ url, init });
      return jsonResponse({
        items: [],
        page: 1,
        size: 10,
        totalItems: 0,
        totalPages: 0,
        hasNext: false,
        hasPrevious: true
      });
    }
  });

  const response = await TaskController_listTasks(client, { query: { page: 1, size: 10, status: "CREATED" } });

  assert.equal(requests[0].url, "https://api.example.test/api/v1/tasks?page=1&size=10&status=CREATED");
  assert.equal(response.data.page, 1);
  assert.equal(response.data.items.length, 0);
});

test("ProblemDetail error remains structured and exposes API version header", async () => {
  const client = createHotelOpAiClient({
    baseUrl: "https://api.example.test",
    accessToken: () => "access-token",
    fetchImpl: async () => jsonResponse(
      { type: "about:blank", title: "Permission denied", status: 403, detail: "Missing permission", instance: "/api/v1/tasks" },
      { status: 403 }
    )
  });

  await assert.rejects(
    () => TaskController_listTasks(client),
    (error) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 403);
      assert.equal(error.problem.title, "Permission denied");
      assert.equal(error.apiVersion, "v1");
      return true;
    }
  );
});

test("generated SDK includes public routes but excludes dev and actuator routes", async () => {
  const operations = await import("../dist/generated/operations.js");
  const operationNames = Object.keys(operations);

  assert.ok(operationNames.includes("AuthController_login"));
  assert.ok(operationNames.includes("TaskController_listTasks"));
  assert.ok(operationNames.includes("NotificationController_listNotifications"));
  assert.ok(operationNames.every((name) => !name.toLowerCase().includes("devpms")));
  assert.ok(operationNames.every((name) => !name.toLowerCase().includes("actuator")));
});

test("notification response accepts unknown fields without breaking parsing", async () => {
  const client = createHotelOpAiClient({
    baseUrl: "https://api.example.test",
    accessToken: () => "access-token",
    fetchImpl: async () => jsonResponse([{ id: "n1", title: "Task", body: "Created", status: "UNREAD", createdAt: "2026-07-17T00:00:00Z", extra: "ignored" }])
  });

  const response = await NotificationController_listNotifications(client);
  assert.equal(response.data[0].id, "n1");
});

function jsonResponse(body, options = {}) {
  return new Response(JSON.stringify(body), {
    status: options.status ?? 200,
    headers: {
      "Content-Type": "application/json",
      "X-API-Version": "v1"
    }
  });
}
