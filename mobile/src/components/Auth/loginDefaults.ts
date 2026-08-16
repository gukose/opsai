import type { AppEnvironment } from "../../config/appConfig";

const DEMO_EMAIL = "reviewer.admin@demo.hotelopai.app";
const DEMO_PASSWORD = "demo12345678";
const LOCAL_EMAIL = "admin@hotelopai.local";
const LOCAL_PASSWORD = "admin123";

export function loginDefaults(environment: AppEnvironment) {
  if (environment === "demo") {
    return { email: DEMO_EMAIL, password: DEMO_PASSWORD };
  }
  if (environment === "local") {
    return { email: LOCAL_EMAIL, password: LOCAL_PASSWORD };
  }
  return { email: "", password: "" };
}
