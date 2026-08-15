export type MicrophonePermissionState = "unknown" | "granted" | "denied" | "blocked";
export type VoiceFlowPhase = "idle" | "requesting_permission" | "recording" | "transcribing" | "ready" | "error";
export type VoiceFlowEvent = "REQUEST_PERMISSION" | "START" | "STOP" | "TRANSCRIBED" | "FAIL" | "CANCEL" | "RETRY";

export function transitionVoicePhase(current: VoiceFlowPhase, event: VoiceFlowEvent): VoiceFlowPhase {
  if (event === "CANCEL") return "idle";
  if (event === "FAIL") return "error";
  if (event === "RETRY") return "idle";
  if (event === "REQUEST_PERMISSION" && current === "idle") return "requesting_permission";
  if (event === "START" && (current === "idle" || current === "requesting_permission")) return "recording";
  if (event === "STOP" && current === "recording") return "transcribing";
  if (event === "TRANSCRIBED" && current === "transcribing") return "ready";
  throw new Error(`Invalid voice transition: ${current} -> ${event}`);
}

export type VoiceTaskProposal = {
  transcript: string;
  languageCode: string;
  durationMs: number;
  provider: string;
  simulated: boolean;
  intent: string;
  confidence: number;
  confirmationRequired: boolean;
  location?: string;
  category?: string;
  priority: string;
  requiredDepartment?: string;
  requiredSkill?: string;
};

export function mapPermissionResponse(response: { granted: boolean; canAskAgain?: boolean }): MicrophonePermissionState {
  if (response.granted) return "granted";
  return response.canAskAgain === false ? "blocked" : "denied";
}

export function voiceErrorMessage(error: unknown): string {
  if (error instanceof DOMException && error.name === "AbortError") return "Transcription timed out. Try again.";
  const message = error instanceof Error ? error.message : "Voice recording failed.";
  if (/not configured or available/i.test(message)) return "Voice transcription is not enabled on the connected backend.";
  if (/could not transcribe this audio recording/i.test(message)) return "The speech service could not read this recording. Record for at least one second, speak clearly, and try again.";
  if (/not authorized.*sign in again/i.test(message)) return "Your voice session expired. Sign in again and retry.";
  if (/network|fetch|upload/i.test(message)) return "Audio upload failed. Check your connection and retry.";
  if (/empty transcript|empty audio/i.test(message)) return "No speech was detected. Please record again.";
  if (/provider|transcription|speech/i.test(message)) return "Speech transcription is unavailable. Please retry or type the request.";
  return message;
}

export function formatRecordingDuration(durationMs: number): string {
  const seconds = Math.max(0, Math.floor(durationMs / 1000));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
}
