import { appApiBaseUrl } from "../config/appConfig";
import type { VoiceTaskProposal } from "./voiceModels";
import { File } from "expo-file-system";

type VoiceApiResponse = {
  transcript: { transcript: string; languageCode: string; confidence: number; provider: string; simulated: boolean };
  intent: {
    intent: string;
    confidence: number;
    confirmationRequired: boolean;
    entities: { location?: string | null; category?: string | null; priority: string; requiredDepartment?: string | null; requiredSkill?: string | null };
  };
};

export async function transcribeRecording(input: {
  uri: string;
  mimeType: string;
  durationMs: number;
  accessToken: string;
  languageHint?: string;
  timeoutMs?: number;
  fetchImpl?: typeof fetch;
}): Promise<VoiceTaskProposal> {
  if (!input.accessToken) throw new Error("Authentication is required for transcription.");
  if (!input.uri) throw new Error("Audio recording is empty.");
  const form = new FormData();
  const extension = extensionForAudio(input.uri, input.mimeType);
  const fileName = `voice-${Date.now()}.${extension}`;
  if (input.uri.startsWith("blob:") || input.uri.startsWith("data:")) {
    const recordingResponse = await fetch(input.uri);
    if (!recordingResponse.ok) throw new Error("Audio recording could not be read for upload.");
    const recordingBlob = await recordingResponse.blob();
    if (recordingBlob.size <= 0) throw new Error("Audio recording is empty.");
    const typedBlob = recordingBlob.type === input.mimeType
      ? recordingBlob
      : recordingBlob.slice(0, recordingBlob.size, input.mimeType);
    form.append("audio", typedBlob, fileName);
  } else {
    // React Native's legacy `{ uri, name, type }` FormData parts are rejected
    // by the SDK 57 fetch implementation. Expo's File is a real Blob-backed
    // multipart part and keeps the upload streaming from the local URI.
    const recordingFile = new File(input.uri);
    if (recordingFile.exists !== true || (recordingFile.size ?? 0) <= 0) {
      throw new Error("Audio recording is empty.");
    }
    form.append("audio", recordingFile, fileName);
  }
  if (input.languageHint) form.append("languageHint", input.languageHint);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), input.timeoutMs ?? 45_000);
  try {
    const response = await (input.fetchImpl ?? fetch)(`${appApiBaseUrl}/api/v1/internal/voice/transcribe`, {
      method: "POST",
      headers: { Authorization: `Bearer ${input.accessToken}` },
      body: form,
      signal: controller.signal
    });
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) throw new Error("Your session is not authorized for voice transcription. Sign in again.");
      if (response.status === 413) throw new Error("Audio recording is too large. Record a shorter request.");
      if (response.status === 415) throw new Error("Audio format is not supported.");
      if (response.status === 502) throw new Error("Speech service could not transcribe this audio recording.");
      if (response.status === 503) throw new Error("Voice transcription provider is not configured or available on this backend.");
      if (response.status >= 500) throw new Error("Speech transcription service failed unexpectedly.");
      throw new Error("Audio upload was rejected.");
    }
    const body = await response.json() as VoiceApiResponse;
    const transcript = body.transcript?.transcript?.trim();
    if (!transcript) throw new Error("Speech provider returned an empty transcript.");
    return {
      transcript,
      languageCode: body.transcript.languageCode || "und",
      durationMs: input.durationMs,
      provider: body.transcript.provider,
      simulated: body.transcript.simulated,
      intent: body.intent.intent,
      confidence: body.intent.confidence,
      confirmationRequired: body.intent.confirmationRequired,
      location: body.intent.entities.location ?? undefined,
      category: body.intent.entities.category ?? undefined,
      priority: body.intent.entities.priority,
      requiredDepartment: body.intent.entities.requiredDepartment ?? undefined,
      requiredSkill: body.intent.entities.requiredSkill ?? undefined
    };
  } finally {
    clearTimeout(timeout);
  }
}

function extensionForAudio(uri: string, mimeType: string): string {
  const uriExtension = (uri.split(/[?#]/, 1)[0] ?? "").match(/\.([a-z0-9]+)$/i)?.[1]?.toLowerCase();
  if (uriExtension && ["m4a", "mp4", "wav", "webm", "caf", "3gp"].includes(uriExtension)) return uriExtension;
  if (mimeType === "audio/webm") return "webm";
  if (mimeType === "audio/wav" || mimeType === "audio/x-wav") return "wav";
  if (mimeType === "audio/3gpp") return "3gp";
  return "m4a";
}
