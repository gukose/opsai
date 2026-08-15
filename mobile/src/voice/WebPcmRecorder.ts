export type WebPcmRecording = { uri: string; durationMs: number; mimeType: "audio/wav" };

export class WebPcmRecorder {
  private readonly chunks: Float32Array[] = [];
  private readonly startedAt = Date.now();

  private constructor(
    private readonly context: AudioContext,
    private readonly stream: MediaStream,
    private readonly source: MediaStreamAudioSourceNode,
    private readonly processor: ScriptProcessorNode
  ) {
    processor.onaudioprocess = (event) => {
      this.chunks.push(new Float32Array(event.inputBuffer.getChannelData(0)));
    };
  }

  static async start(): Promise<WebPcmRecorder> {
    if (!navigator.mediaDevices?.getUserMedia) throw new Error("No microphone is available in this browser.");
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const context = new AudioContext();
    await context.resume();
    const source = context.createMediaStreamSource(stream);
    const processor = context.createScriptProcessor(4096, 1, 1);
    const recorder = new WebPcmRecorder(context, stream, source, processor);
    source.connect(processor);
    processor.connect(context.destination);
    return recorder;
  }

  async stop(): Promise<WebPcmRecording> {
    this.processor.disconnect();
    this.source.disconnect();
    this.stream.getTracks().forEach((track) => track.stop());
    await this.context.close();
    const samples = mergeSamples(this.chunks);
    if (samples.length === 0) throw new Error("Audio recording is empty.");
    const blob = new Blob([encodeWav(samples, this.context.sampleRate)], { type: "audio/wav" });
    return { uri: URL.createObjectURL(blob), durationMs: Date.now() - this.startedAt, mimeType: "audio/wav" };
  }
}

function mergeSamples(chunks: Float32Array[]): Float32Array {
  const merged = new Float32Array(chunks.reduce((total, chunk) => total + chunk.length, 0));
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.length;
  }
  return merged;
}

function encodeWav(samples: Float32Array, sampleRate: number): ArrayBuffer {
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  writeText(view, 0, "RIFF");
  view.setUint32(4, 36 + samples.length * 2, true);
  writeText(view, 8, "WAVE");
  writeText(view, 12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeText(view, 36, "data");
  view.setUint32(40, samples.length * 2, true);
  for (let index = 0; index < samples.length; index += 1) {
    const sample = Math.max(-1, Math.min(1, samples[index] ?? 0));
    view.setInt16(44 + index * 2, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
  }
  return buffer;
}

function writeText(view: DataView, offset: number, text: string): void {
  for (let index = 0; index < text.length; index += 1) view.setUint8(offset + index, text.charCodeAt(index));
}
