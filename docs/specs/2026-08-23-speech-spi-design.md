# Speech SPIs — SpeechToTextService & TextToSpeechService

> **Issues:** casehubio/blocks#155 (restructuring), casehubio/blocks#157 (implementation)
> **Date:** 2026-08-23
> **Status:** Design — pending implementation plan
> **Driven by:** casehubio/drafthouse#117 (voice-first drafting mode)

## 1. Problem Statement

CaseHub applications need speech capabilities (audio-to-text, text-to-audio) as composable building blocks. The SPI must be provider-agnostic — latency and quality vary significantly across models and services, so implementations must be swappable without touching consumer code.

## 2. Module Structure

Adding speech SPIs requires restructuring casehub-blocks from a single module into a multi-module reactor (blocks#155):

| Module | Contents | Dependencies |
|--------|----------|-------------|
| blocks (existing) | Current blocks — channel, agentic, summarisation | qhorus-api, work-api, engine-api |
| blocks-speech-api | Pure Java SPI interfaces | None (zero foundation deps) |
| blocks-speech-sherpa | sherpa-onnx FFM implementation | blocks-speech-api, sherpa-onnx native |

blocks-speech-api has zero foundation dependencies — consumers that only need the interface don't pull in qhorus/work/engine.

## 3. SpeechToTextService SPI

```java
public interface SpeechToTextService {
    TranscriptionResult transcribe(Path audioFile, TranscriptionOptions options);
}

public record TranscriptionResult(String text, String language, double confidence) {}

public record TranscriptionOptions(String audioFormat, String languageHint, String modelSize) {}
```

Path rather than byte[] — avoids loading entire audio files into heap. Sherpa-onnx reads files natively. A future streaming variant can be added without breaking the initial contract.

## 4. TextToSpeechService SPI

```java
public interface TextToSpeechService {
    SynthesisResult synthesise(String text, SynthesisOptions options);
}

public record SynthesisResult(byte[] audioData, String audioFormat, List<PhonemeTiming> phonemes) {}
public record PhonemeTiming(String phoneme, long startMs, long endMs) {}
public record SynthesisOptions(String voice, String language, String audioFormat, boolean includePhonemes) {}
```

PhonemeTiming enables downstream avatar lip-sync (blocks#154).

## 5. Default Implementation: sherpa-onnx

Unified runtime for both STT and TTS via Java FFM/Panama:
- STT: Whisper models (tiny=39MB to large=1.5GB)
- TTS: VITS/Piper models with phoneme timing
- One native binding for both directions
- Apple Silicon Metal acceleration, Linux CPU/CUDA

Model management: models stored under a configurable path, downloaded on first use. Model selection configurable (speed vs accuracy tradeoff).

Alternative implementations pluggable via CDI for external services (Deepgram, Google, ElevenLabs, Amazon Polly).

## 6. Design Constraints

- SPI interfaces are pure Java — no CDI, no Quarkus, no framework annotations
- Implementation module handles CDI integration (@ApplicationScoped, @DefaultBean)
- Path-based audio input for memory efficiency
- Synchronous initial API — async/streaming variant deferred to Phase 2

## References

- casehubio/drafthouse#117 — driving use case (voice-first drafting)
- casehubio/blocks#154 — avatar lip-sync (future TTS consumer)
- casehubio/blocks#155 — multi-module restructuring
- casehubio/blocks#157 — speech SPI implementation
