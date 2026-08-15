import { useEffect, useRef, useState } from "react";
import { Linking, Platform, Pressable, StyleSheet, Text, View } from "react-native";
import { Mic, Square, Trash2, RotateCcw, Settings } from "lucide-react-native";
import { RecordingPresets, getRecordingPermissionsAsync, requestRecordingPermissionsAsync, setAudioModeAsync, useAudioRecorder, useAudioRecorderState } from "expo-audio";
import { colors, radius, spacing } from "../../theme/tokens";
import { transcribeRecording } from "../../voice/VoiceApi";
import { formatRecordingDuration, mapPermissionResponse, MicrophonePermissionState, VoiceFlowPhase, VoiceTaskProposal, voiceErrorMessage } from "../../voice/voiceModels";
import { WebPcmRecorder } from "../../voice/WebPcmRecorder";

const MAX_DURATION_MS = 60_000;
type Props = { accessToken: string; onUseTranscript: (proposal: VoiceTaskProposal) => void; onClose: () => void };

export function VoiceRecorderPanel({ accessToken, onUseTranscript, onClose }: Props) {
  const webMicrophoneSupported = Platform.OS !== "web" ||
    (typeof navigator !== "undefined" && Boolean(navigator.mediaDevices?.getUserMedia));
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recorderState = useAudioRecorderState(recorder, 200);
  const [webDurationMs, setWebDurationMs] = useState(0);
  const [permission, setPermission] = useState<MicrophonePermissionState>("unknown");
  const [phase, setPhase] = useState<VoiceFlowPhase>("idle");
  const [error, setError] = useState<string | null>(null);
  const [proposal, setProposal] = useState<VoiceTaskProposal | null>(null);
  const stopping = useRef(false);
  const startupRequested = useRef(false);
  const webRecorder = useRef<WebPcmRecorder | null>(null);
  const recordingStartedAt = useRef(0);
  const displayedDurationMs = Platform.OS === "web" ? webDurationMs : recorderState.durationMillis;

  useEffect(() => {
    if (startupRequested.current) return;
    startupRequested.current = true;

    void getRecordingPermissionsAsync()
      .then(async (result) => {
        const next = mapPermissionResponse(result);
        setPermission(next);
        if (next === "granted") {
          await startRecording(true);
        } else if (next === "blocked") {
          setPhase("idle");
        } else {
          await requestPermission();
        }
      })
      .catch((cause) => {
        setPermission("denied");
        setPhase("error");
        setError(voiceErrorMessage(cause));
      });
  }, []);
  useEffect(() => {
    if (phase !== "recording" || Platform.OS !== "web") return;
    const timer = setInterval(() => setWebDurationMs(Date.now() - recordingStartedAt.current), 200);
    return () => clearInterval(timer);
  }, [phase]);
  useEffect(() => {
    if (phase === "recording" && displayedDurationMs >= MAX_DURATION_MS && !stopping.current) void stopAndTranscribe();
  }, [phase, displayedDurationMs]);
  useEffect(() => () => {
    if (webRecorder.current) void webRecorder.current.stop().catch(() => undefined);
    try { if (recorder.isRecording) void recorder.stop().catch(() => undefined); } catch { /* already stopped */ }
    void setAudioModeAsync({ allowsRecording: false }).catch(() => undefined);
  }, [recorder]);

  const requestPermission = async () => {
    setPhase("requesting_permission"); setError(null);
    try { const next = mapPermissionResponse(await requestRecordingPermissionsAsync()); setPermission(next); setPhase("idle"); if (next === "granted") await startRecording(true); }
    catch (cause) { setPhase("error"); setError(voiceErrorMessage(cause)); }
  };
  const startRecording = async (permissionAlreadyGranted = false) => {
    if (!webMicrophoneSupported) {
      setPhase("error");
      setError("This browser cannot record audio. Use Expo Go on iOS/Android or a browser with microphone recording support.");
      return;
    }
    if (!permissionAlreadyGranted && permission !== "granted") { await requestPermission(); return; }
    try {
      setError(null); setProposal(null); stopping.current=false;
      if (Platform.OS === "web") {
        webRecorder.current = await WebPcmRecorder.start();
        recordingStartedAt.current = Date.now();
        setWebDurationMs(0);
        setPhase("recording");
        return;
      }
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      await recorder.prepareToRecordAsync();
      const inputs=recorder.getAvailableInputs(); if(inputs.length===0) throw new Error("No microphone is available on this device.");
      recorder.record(); setPhase("recording");
    } catch (cause) { setPhase("error"); setError(voiceErrorMessage(cause)); }
  };
  const stopAndTranscribe = async () => {
    if (stopping.current) return; stopping.current=true;
    try {
      let durationMs: number;
      let uri: string | null;
      let mimeType: string;
      if (Platform.OS === "web") {
        const recording = await webRecorder.current?.stop();
        webRecorder.current = null;
        durationMs = recording?.durationMs ?? webDurationMs;
        uri = recording?.uri ?? null;
        mimeType = recording?.mimeType ?? "audio/wav";
      } else {
        durationMs=recorderState.durationMillis; await recorder.stop(); await setAudioModeAsync({ allowsRecording: false });
        uri=recorder.uri;
        mimeType="audio/mp4";
      }
      if(!uri||durationMs<300) throw new Error("Audio recording is empty.");
      setPhase("transcribing"); const next=await transcribeRecording({uri,mimeType,durationMs,accessToken}); if (Platform.OS === "web") URL.revokeObjectURL(uri); setProposal(next); setPhase("ready");
    } catch(cause){setPhase("error");setError(voiceErrorMessage(cause));} finally {stopping.current=false;}
  };
  const cancel = async () => { try { if(webRecorder.current){await webRecorder.current.stop();webRecorder.current=null;} if(recorder.isRecording) await recorder.stop(); await setAudioModeAsync({allowsRecording:false}); } finally {setProposal(null);setError(null);setPhase("idle");onClose();} };

  return <View style={styles.inline} accessibilityLabel="Voice recorder">
    {phase === "requesting_permission" ? <Text style={styles.status}>Requesting microphone permission…</Text> : null}
    {phase === "recording" ? <View style={styles.recording}><View style={styles.dot}/><Text style={styles.status}>Recording {formatRecordingDuration(displayedDurationMs)} / 1:00</Text></View> : null}
    {phase === "transcribing" ? <Text style={styles.status}>Uploading and transcribing…</Text> : null}
    {permission === "denied" ? <Text style={styles.error}>Microphone permission was denied. You can retry the permission request.</Text> : null}
    {permission === "blocked" ? <><Text style={styles.error}>Microphone permission is blocked. Enable it in device settings.</Text><Action icon={Settings} label="Open settings" onPress={() => void Linking.openSettings()}/></> : null}
    {error ? <Text style={styles.error}>{error}</Text> : null}
    {proposal ? <View style={styles.proposal}>
      <Text style={styles.transcript}>{proposal.transcript}</Text>
      <Text style={styles.meta}>Intent: {proposal.intent} · {Math.round(proposal.confidence*100)}% · {proposal.priority}</Text>
      <Text style={styles.meta}>{proposal.location ?? "Location needs review"}{proposal.requiredDepartment ? ` · ${proposal.requiredDepartment}` : ""}</Text>
      {proposal.simulated ? <Text style={styles.warning}>InternalDemo fixture transcription</Text> : null}
      {proposal.confirmationRequired ? <Text style={styles.warning}>Low confidence — review and confirm before creating the task.</Text> : null}
    </View> : null}
    <View style={styles.actions}>
      {phase === "idle" || phase === "error" ? <Action icon={phase==="error"?RotateCcw:Mic} label={permission==="granted"?"Record":"Allow microphone"} onPress={() => void startRecording()}/> : null}
      {phase === "recording" ? <Action icon={Square} label="Stop" onPress={() => void stopAndTranscribe()}/> : null}
      {proposal ? <Action icon={Mic} label="Use transcript" onPress={() => {onUseTranscript(proposal);onClose();}}/> : null}
      <Action icon={Trash2} label="Cancel" onPress={() => void cancel()}/>
    </View>
  </View>;
}

function Action({icon:Icon,label,onPress}:{icon:typeof Mic;label:string;onPress:()=>void}){return <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} style={styles.button}><Icon color={colors.nav} size={14}/><Text style={styles.buttonText}>{label}</Text></Pressable>}
const styles=StyleSheet.create({inline:{gap:7,paddingVertical:7,borderTopWidth:1,borderTopColor:colors.divider},recording:{flexDirection:"row",alignItems:"center",gap:6},dot:{width:8,height:8,borderRadius:4,backgroundColor:colors.red},status:{color:colors.text,fontWeight:"700"},error:{color:colors.red},proposal:{gap:4,padding:8,backgroundColor:"#f6f8fa",borderRadius:radius.sm},transcript:{color:colors.text,fontWeight:"600"},meta:{color:colors.textMuted,fontSize:12},warning:{color:"#8a5a00",fontSize:12},actions:{flexDirection:"row",flexWrap:"wrap",gap:8},button:{minHeight:34,flexDirection:"row",alignItems:"center",gap:5,paddingHorizontal:12,paddingVertical:6,borderWidth:1,borderColor:colors.cardBorder,borderRadius:radius.sm},buttonText:{color:colors.nav,fontWeight:"700",fontSize:12}});
