import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { transcribeRecording } from "./VoiceApi.ts";

test("audio upload maps authenticated multipart response to transcript and task proposal", async () => {
  let request;
  const proposal=await transcribeRecording({uri:"file:///recording.m4a",mimeType:"audio/mp4",durationMs:2500,accessToken:"mobile-test-token",fetchImpl:async (url,options)=>{
    request={url,options};
    return new Response(JSON.stringify({transcript:{transcript:"Room 502 HVAC is broken",languageCode:"en",confidence:0.96,provider:"external",simulated:false},intent:{intent:"MAINTENANCE_REQUEST",confidence:0.92,confirmationRequired:false,entities:{location:"Room 502",category:"TECHNICAL",priority:"HIGH",requiredDepartment:"MAINTENANCE",requiredSkill:"HVAC"}}}),{status:200,headers:{"content-type":"application/json"}});
  }});
  assert.match(request.url,/\/api\/v1\/internal\/voice\/transcribe$/);
  assert.equal(request.options.headers.Authorization,"Bearer mobile-test-token");
  assert.ok(request.options.body instanceof FormData);
  assert.equal(proposal.intent,"MAINTENANCE_REQUEST");
  assert.equal(proposal.requiredSkill,"HVAC");
  assert.equal(proposal.transcript,"Room 502 HVAC is broken");
});

test("empty and low-confidence responses preserve confirmation policy", async () => {
  const low=await transcribeRecording({uri:"file:///voice.m4a",mimeType:"audio/mp4",durationMs:900,accessToken:"token",fetchImpl:async()=>new Response(JSON.stringify({transcript:{transcript:"Check this",languageCode:"en",provider:"external",simulated:false},intent:{intent:"UNKNOWN",confidence:0.25,confirmationRequired:true,entities:{priority:"MEDIUM"}}}),{status:200})});
  assert.equal(low.confirmationRequired,true);
  await assert.rejects(()=>transcribeRecording({uri:"file:///voice.m4a",mimeType:"audio/mp4",durationMs:900,accessToken:"token",fetchImpl:async()=>new Response(JSON.stringify({transcript:{transcript:""},intent:{}}),{status:200})}),/empty transcript/);
});

test("Expo Web blob recording is uploaded as a real multipart Blob", async () => {
  let uploadedAudio;
  await transcribeRecording({
    uri: "data:audio/webm;base64,AQID",
    mimeType: "audio/webm",
    durationMs: 1200,
    accessToken: "token",
    fetchImpl: async (_url, options) => {
      uploadedAudio = options.body.get("audio");
      return new Response(JSON.stringify({
        transcript: { transcript: "Room 502 HVAC issue", languageCode: "en", provider: "external", simulated: false },
        intent: { intent: "MAINTENANCE_REQUEST", confidence: 0.9, confirmationRequired: false, entities: { priority: "HIGH" } }
      }), { status: 200 });
    }
  });
  assert.ok(uploadedAudio instanceof Blob);
  assert.equal(uploadedAudio.type, "audio/webm");
  assert.equal(uploadedAudio.size, 3);
});

test("provider and authorization failures are not reported as generic network upload failures", async () => {
  await assert.rejects(
    () => transcribeRecording({ uri: "file:///voice.m4a", mimeType: "audio/mp4", durationMs: 900, accessToken: "token", fetchImpl: async () => new Response(null, { status: 503 }) }),
    /not configured or available/i
  );
  await assert.rejects(
    () => transcribeRecording({ uri: "file:///voice.m4a", mimeType: "audio/mp4", durationMs: 900, accessToken: "token", fetchImpl: async () => new Response(null, { status: 401 }) }),
    /sign in again/i
  );
});

test("mobile voice source and public config contain no provider credential", async () => {
  const files=["./VoiceApi.ts","../config/appConfig.ts","../config/assistantConfig.ts"];
  const source=(await Promise.all(files.map(file=>readFile(new URL(file,import.meta.url),"utf8")))).join("\n");
  assert.doesNotMatch(source,/OPENAI_API_KEY|api[_-]?key|Bearer sk-/i);
});
