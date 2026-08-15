import test from "node:test";
import assert from "node:assert/strict";
import { formatRecordingDuration, mapPermissionResponse, transitionVoicePhase, voiceErrorMessage } from "./voiceModels.ts";

test("microphone permission distinguishes denied and permanently blocked", () => {
  assert.equal(mapPermissionResponse({granted:true,canAskAgain:true}),"granted");
  assert.equal(mapPermissionResponse({granted:false,canAskAgain:true}),"denied");
  assert.equal(mapPermissionResponse({granted:false,canAskAgain:false}),"blocked");
});

test("recording lifecycle supports start stop transcription retry and cancel", () => {
  assert.equal(transitionVoicePhase("idle","START"),"recording");
  assert.equal(transitionVoicePhase("recording","STOP"),"transcribing");
  assert.equal(transitionVoicePhase("transcribing","TRANSCRIBED"),"ready");
  assert.equal(transitionVoicePhase("recording","CANCEL"),"idle");
  assert.equal(transitionVoicePhase("error","RETRY"),"idle");
  assert.throws(()=>transitionVoicePhase("idle","STOP"),/Invalid voice transition/);
  assert.equal(formatRecordingDuration(65_000),"1:05");
});

test("voice failures are actionable without exposing payload text", () => {
  assert.match(voiceErrorMessage(new Error("network fetch failed")),/connection/i);
  assert.match(voiceErrorMessage(new Error("empty transcript")),/No speech/i);
  assert.match(voiceErrorMessage(new Error("provider unavailable")),/unavailable/i);
  assert.match(voiceErrorMessage(new Error("Speech service could not transcribe this audio recording.")),/at least one second/i);
  assert.match(voiceErrorMessage(new Error("Voice transcription provider is not configured or available on this backend.")),/not enabled/i);
});
