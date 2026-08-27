# Live Microphone Capture → RecognitionStream

**Issue:** casehubio/blocks#183
**Scale:** S | **Complexity:** Low
**Date:** 2026-08-26

## Summary

Audio capture utility that bridges `javax.sound.sampled.TargetDataLine` to `RecognitionStream.acceptSamples()`. Runs a background capture loop feeding 100ms PCM chunks at 16kHz. Pure feeder — caller manages `RecognitionStream` lifecycle and polls for results.

## Module and Package

`speech-sherpa` module, package `io.casehub.blocks.speech.sherpa`.

speech-api is pure SPIs with zero dependencies — a concrete utility doesn't belong there. MicrophoneCapture joins WavReader, WavWriter, and SpeechCli as implementation utilities in speech-sherpa.

## API

```java
public final class MicrophoneCapture implements AutoCloseable {

    public MicrophoneCapture(RecognitionStream stream, TargetDataLine line, int sampleRate)

    public static MicrophoneCapture openDefault(RecognitionStream stream)
    public static MicrophoneCapture openDefault(RecognitionStream stream, int sampleRate)

    public void start()
    public void stop()
    @Override public void close()

    static float[] pcmToFloat(byte[] pcm, int length)
}
```

### Constructor

Takes a `RecognitionStream` to feed, a `TargetDataLine` (already opened), and the sample rate. The injectable `TargetDataLine` enables testing without a real microphone.

### Factory methods

`openDefault()` opens the system default microphone with the standard speech audio format: 16kHz, 16-bit signed PCM, mono, little-endian. Overload accepts custom sample rate.

### Lifecycle

- `start()` — launches a virtual thread (`Thread.ofVirtual().name("mic-capture")`) that reads from `TargetDataLine` in a loop. Throws `IllegalStateException` if already running or closed.
- `stop()` — sets `volatile boolean running = false`, stops the `TargetDataLine`, joins the capture thread (1s timeout).
- `close()` — calls `stop()` if running, then closes the `TargetDataLine`. Safe to call multiple times.

### Capture loop

```
while (running):
    bytes = line.read(buffer, 0, bytesPerChunk)   // blocking
    if bytes <= 0: continue
    samples = pcmToFloat(buffer, bytes)
    stream.acceptSamples(samples, sampleRate)
```

**Chunk size:** `sampleRate / 10` samples per chunk (100ms). At 16kHz = 1600 samples = 3200 bytes.

**Thread:** Virtual thread — `TargetDataLine.read()` is blocking I/O, exactly the use case for virtual threads. No platform thread consumed while waiting for audio.

**Error handling:** If `stream.acceptSamples()` throws, the capture loop sets `running = false` and stores the exception. `stop()` rethrows it wrapped in `SherpaException`. If `line.read()` returns -1 or 0 (line closed), continue to next iteration — `running` check terminates naturally.

### PCM conversion

Package-private `static float[] pcmToFloat(byte[] pcm, int length)`:
- 16-bit signed little-endian PCM → float in [-1.0, 1.0]
- `(short)(lo | (hi << 8)) / 32768f`
- Same conversion as `SpeechCli.readWavSamples()` and `WavReader`

## SpeechCli `listen` command

New CLI command for live transcription demo:

```
java ... SpeechCli listen <model-dir>
```

1. Create `SherpaOnnxStreamingSpeechToText` with config from model-dir
2. Start stream via `startStream(TranscriptionOptions.defaults())`
3. Open mic via `MicrophoneCapture.openDefault(stream)`
4. Start capture
5. Main thread polls `partialResult()` and `isEndpointDetected()` in a loop (100ms sleep between polls)
6. Print partial transcripts, print endpoint markers
7. Enter key or Ctrl+C to stop
8. Print final result, close resources

## Test strategy

### pcmToFloat (unit)

- Known PCM bytes → expected float values
- Silence (all zeros) → all 0.0f
- Max positive amplitude (0x7FFF) → ~1.0f
- Max negative amplitude (0x8000) → -1.0f
- Odd byte count (truncated last sample) → ignore trailing byte

### Capture loop (unit, mocked TargetDataLine)

- Mock `TargetDataLine` returns canned PCM data, then returns 0 (EOF)
- Verify `RecognitionStream.acceptSamples()` receives correct float arrays
- Verify correct sample rate is passed through
- Verify chunk size matches expected 100ms

### Lifecycle (unit)

- `start()` twice → IllegalStateException
- `start()` after `close()` → IllegalStateException
- `stop()` when not started → no-op
- `close()` twice → no-op (idempotent)
- `close()` stops active capture

### openDefault (conditional)

- Skip on headless CI (no audio device)
- When audio device available: verify it opens without error and creates a valid MicrophoneCapture

## Non-goals

- No callback/listener mechanism — caller polls RecognitionStream directly
- No audio format negotiation — fixed at 16-bit signed PCM mono LE
- No gain/volume control
- No silence detection or VAD (voice activity detection)

## References

- `RecognitionStream.java` (speech-api) — the SPI being fed
- `SherpaOnnxStreamingSpeechToText.java:135-220` — existing SherpaRecognitionStream implementation
- `SpeechCli.java:71-110` — stream command showing the chunking pattern
- `WavReader.java` — existing PCM-to-float conversion
- Issue casehubio/blocks#183
- D6 in decisions.md — API shape decision
