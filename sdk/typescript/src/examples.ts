import {
  ApiError,
  AuthController_login,
  AuthController_me,
  TaskController_listTasks,
  createHotelOpAiClient
} from "./index.js";

let accessToken: string | null = null;

const client = createHotelOpAiClient({
  baseUrl: "https://example.invalid",
  accessToken: () => accessToken
});

async function compileTimeExample(): Promise<void> {
  const login = await AuthController_login(client, {
    body: {
      hotelCode: "demo",
      email: "admin@example.invalid",
      password: "not-a-real-password"
    }
  });

  accessToken = login.data.accessToken;

  const me = await AuthController_me(client);
  const userEmail: string = me.data.email;

  const tasks = await TaskController_listTasks(client, {
    query: {
      page: 0,
      size: 20
    }
  });

  if (!Array.isArray(tasks.data) && tasks.data.items) {
    const firstTaskTitle: string | undefined = tasks.data.items[0]?.title;
    void firstTaskTitle;
  }

  try {
    await AuthController_me(client);
  } catch (error) {
    if (error instanceof ApiError) {
      const problemTitle: string | undefined = error.problem?.title;
      void problemTitle;
    }
  }

  void userEmail;
}

void compileTimeExample;
