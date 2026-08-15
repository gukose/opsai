const login = await fetch("http://localhost:8080/api/v1/auth/login", {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ hotelCode: "hotel-opai-demo", email: "admin@hotelopai.local", password: "admin123" })
});
const session = await login.json();
if (!login.ok) throw new Error(`Voice UI probe login failed: ${login.status}`);

const pages = await fetch("http://localhost:9224/json/list").then((response) => response.json());
const page = pages.find((candidate) => candidate.type === "page");
if (!page) throw new Error("No isolated Chrome page target is available.");
const socket = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((resolve, reject) => {
  socket.addEventListener("open", resolve, { once: true });
  socket.addEventListener("error", reject, { once: true });
});
let sequence = 0;
const pending = new Map();
socket.addEventListener("message", (event) => {
  const message = JSON.parse(event.data);
  const operation = pending.get(message.id);
  if (!operation) return;
  pending.delete(message.id);
  message.error ? operation.reject(new Error(message.error.message)) : operation.resolve(message.result);
});
const command = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++sequence;
  pending.set(id, { resolve, reject });
  socket.send(JSON.stringify({ id, method, params }));
});
const evaluate = (expression) => command("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
const pause = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

await command("Page.enable");
await command("Page.navigate", { url: "http://localhost:8081" });
await pause(1500);
await evaluate(`localStorage.setItem("hotel-opai.session.v1", ${JSON.stringify(JSON.stringify(session))}); location.reload()`);
await pause(2500);
const clicked = await evaluate(`(() => { const button = [...document.querySelectorAll('[role="button"]')].find(node => node.textContent?.includes("Record voice")); button?.click(); return Boolean(button); })()`);
if (!clicked.result.value) throw new Error("Record voice control was not rendered.");
await pause(2500);
const during = await evaluate(`({ recording: document.body.innerText.includes("Recording "), separatePanel: document.body.innerText.includes("Voice request") })`);
await evaluate(`(() => { const button = [...document.querySelectorAll('[role="button"]')].find(node => node.textContent?.trim() === "Stop"); button?.click(); return Boolean(button); })()`);
await pause(12000);
const after = await evaluate(`({
  transcript: document.body.innerText.includes("Voice transcript"),
  unavailable: document.body.innerText.includes("Voice transcription is not enabled"),
  unreadable: document.body.innerText.includes("could not read this recording"),
  genericFailure: document.body.innerText.includes("Speech transcription is unavailable")
})`);
socket.close();
console.log(JSON.stringify({ during: during.result.value, after: after.result.value }));
