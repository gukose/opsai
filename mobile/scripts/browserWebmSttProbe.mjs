import { readFile } from "node:fs/promises";

const pages = await fetch("http://localhost:9223/json/list").then((response) => response.json());
const page = pages.find((candidate) => candidate.type === "page");
if (!page) throw new Error("No local Chrome page target is available.");
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

const wav = (await readFile("/tmp/hotel-opai-stt-test.wav")).toString("base64");
const expression = `(async () => {
  const bytes = Uint8Array.from(atob(${JSON.stringify(wav)}), character => character.charCodeAt(0));
  const context = new AudioContext();
  await context.resume();
  const buffer = await context.decodeAudioData(bytes.buffer);
  const source = context.createBufferSource();
  source.buffer = buffer;
  const destination = context.createMediaStreamDestination();
  source.connect(destination);
  const recorder = new MediaRecorder(destination.stream, { mimeType: "audio/webm" });
  const chunks = [];
  recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data); };
  const stopped = new Promise(resolve => { recorder.onstop = resolve; });
  recorder.start();
  source.start();
  await new Promise(resolve => { source.onended = resolve; });
  recorder.stop();
  await stopped;
  const recording = new Blob(chunks, { type: "audio/webm" });
  const data = new Uint8Array(await recording.arrayBuffer());
  let binary = "";
  for (const byte of data) binary += String.fromCharCode(byte);
  await context.close();
  return { audio: btoa(binary), size: recording.size, type: recording.type };
})()`;
const result = await command("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
socket.close();
const recording = result.result.value;
if (!recording?.audio || recording.size === 0) throw new Error("Chrome produced an empty WebM recording.");

const login = await fetch("http://localhost:8080/api/v1/auth/login", {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ hotelCode: "hotel-opai-demo", email: "admin@hotelopai.local", password: "admin123" })
});
const session = await login.json();
if (!login.ok) throw new Error(`Probe login failed: ${login.status}`);
const form = new FormData();
form.append("audio", new Blob([Buffer.from(recording.audio, "base64")], { type: "audio/webm" }), "browser-recording.webm");
const response = await fetch("http://localhost:8080/api/v1/internal/voice/transcribe", {
  method: "POST",
  headers: { authorization: `Bearer ${session.accessToken}` },
  body: form
});
const body = await response.json().catch(() => ({}));
console.log(JSON.stringify({
  browserMimeType: recording.type,
  audioBytes: recording.size,
  status: response.status,
  provider: body.transcript?.provider,
  simulated: body.transcript?.simulated,
  intent: body.intent?.intent,
  detail: body.detail
}));
