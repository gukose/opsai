import { writeFile } from "node:fs/promises";

const sizes = [[375,812],[390,844],[430,932],[768,1024],[1024,768],[1440,900]];
const targets = await fetch("http://localhost:9223/json/list").then((response) => response.json());
const target = targets.find((candidate) => candidate.type === "page");
if (!target) throw new Error("No Chrome page target is available.");

const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => {
  socket.addEventListener("open", resolve, { once: true });
  socket.addEventListener("error", reject, { once: true });
});

let sequence = 0;
const pending = new Map();
socket.addEventListener("message", (event) => {
  const message = JSON.parse(event.data);
  if (!message.id) return;
  const operation = pending.get(message.id);
  if (!operation) return;
  pending.delete(message.id);
  if (message.error) operation.reject(new Error(message.error.message));
  else operation.resolve(message.result);
});

function command(method, params = {}) {
  const id = ++sequence;
  socket.send(JSON.stringify({ id, method, params }));
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
}

const evaluate = (expression) => command("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
const pause = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

await command("Page.enable");
await command("Runtime.enable");
await command("Page.navigate", { url: "http://localhost:8081" });
await pause(2500);

const session = {
  accessToken: "visual-acceptance-local-token",
  accessTokenExpiresAt: "2099-01-01T00:00:00Z",
  refreshToken: null,
  refreshTokenExpiresAt: null,
  tokenType: "Bearer",
  currentUser: {
    userId: "visual-user",
    hotelId: "visual-hotel",
    employeeId: "visual-employee",
    email: null,
    displayName: "Demo Manager",
    hotelName: "Hotel OpAI Demo",
    roles: [{ roleId: "admin", code: "ADMIN", name: "Administrator" }],
    permissions: [{ permissionId: "knowledge", code: "KNOWLEDGE_OPERATIONS", name: "Knowledge" }]
  }
};
await evaluate(`localStorage.setItem("hotel-opai.session.v1", ${JSON.stringify(JSON.stringify(session))}); location.reload()`);
await pause(3500);

const results = [];
for (const [width, height] of sizes) {
  await command("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile: width < 600 });
  await pause(500);
  const inspection = await evaluate(`(() => {
    const body = document.body;
    const root = document.getElementById("root")?.firstElementChild;
    const labels = [...document.querySelectorAll("div,span")].filter((node) => ["Tasks","Urgent","Unread"].includes(node.textContent?.trim() ?? ""));
    return {
      viewportWidth: innerWidth,
      bodyClientWidth: body.clientWidth,
      bodyScrollWidth: body.scrollWidth,
      rootWidth: Math.round(root?.getBoundingClientRect().width ?? 0),
      horizontalOverflow: body.scrollWidth > body.clientWidth,
      labels: labels.slice(0, 3).map((node) => ({ text: node.textContent, width: Math.round(node.getBoundingClientRect().width), height: Math.round(node.getBoundingClientRect().height) })),
      recordVoiceVisible: document.body.innerText.includes("Record voice")
    };
  })()`);
  const screenshot = await command("Page.captureScreenshot", { format: "png", captureBeyondViewport: false });
  const output = `/tmp/hotel-opai-${width}x${height}.png`;
  await writeFile(output, Buffer.from(screenshot.data, "base64"));
  results.push({ size: `${width}x${height}`, screenshot: output, ...inspection.result.value });
}

for (const result of results) {
  if (result.horizontalOverflow) throw new Error(`${result.size} has horizontal overflow.`);
  if (!result.recordVoiceVisible) throw new Error(`${result.size} does not expose Record voice.`);
  if (result.labels.some((label) => label.height > 24 || label.width < 40)) throw new Error(`${result.size} has a wrapped overview label.`);
}

await command("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
await evaluate(`(() => { const candidates = [...document.querySelectorAll('[role="button"]')]; const button = candidates.find((node) => node.textContent?.includes("Record voice")); button?.click(); return Boolean(button); })()`);
await pause(500);
const microphone = await evaluate(`({
  embedded: !document.body.innerText.includes("Voice request"),
  active: document.body.innerText.includes("Requesting microphone permission") || document.body.innerText.includes("Recording ") || document.body.innerText.includes("Microphone permission"),
  cancelVisible: document.body.innerText.includes("Cancel")
})`);
const microphoneShot = await command("Page.captureScreenshot", { format: "png", captureBeyondViewport: false });
await writeFile("/tmp/hotel-opai-microphone.png", Buffer.from(microphoneShot.data, "base64"));

console.log(JSON.stringify({ results, microphone: microphone.result.value, microphoneScreenshot: "/tmp/hotel-opai-microphone.png" }, null, 2));
socket.close();
