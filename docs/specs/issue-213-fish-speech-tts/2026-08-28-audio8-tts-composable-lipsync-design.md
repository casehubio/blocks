# Audio8 TTS Integration + Composable Lip-Sync Pipeline

**Issue:** casehubio/blocks#213
**Date:** 2026-08-28
**Status:** Draft

## Summary

Integrate Audio8 TTS as a `TextToSpeechService` implementation providing near-human quality voice synthesis via DualAR autoregressive ONNX inference. Introduce a composable lip-sync pipeline (`PhonemeAligner` + `LipSyncEnricher`) that enriches any phoneme-less TTS engine with lip-sync timing — reusable across Kokoro, SherpaOnnx, Audio8, and future engines including Dia (#214).

Three deliverables:
1. **`Audio8TextToSpeech`** — DualAR TTS via `OnnxRuntimeLibrary`, supporting 0.1B (INT8) and 0.6B (INT4) models with zero-shot voice cloning
2. **`PhonemeAligner` SPI + `LipSyncEnricher` decorator** — composable lip-sync enrichment for any `TextToSpeechService`
3. **Provisioner extensions** — user-selected model download from HuggingFace with size disclosure

## Background

### Why Audio8, not Fish Speech

Issue #213 originally targeted Fish Speech. Research revealed Fish Speech's LLaMA-based Dual-AR architecture cannot export to ONNX — GitHub issues [#600](https://github.com/fishaudio/fish-speech/issues/600) and [#903](https://github.com/fishaudio/fish-speech/issues/903) document failed attempts. The Fish Audio team is investing in LLM-native inference (SGLang, vLLM), not ONNX.

[Audio8 TTS](https://huggingface.co/Audio8/audio8-TTS-0.1B-ONNX-INT8) is a community derivative that successfully exported the Fish Audio DualAR architecture to ONNX with INT8/INT4 quantization. Compact (~1 GB), CPU-only inference, runs on M2 MacBook Air.

### Autoregressive inference — a new paradigm

All existing TTS implementations are single-pass feedforward:
- `VitsTextToSpeech` — one `session.runRaw()` → complete audio
- `KokoroTextToSpeech` — one sherpa-onnx call → complete audio
- `SherpaOnnxTextToSpeech` — one sherpa-onnx call → complete audio

Audio8's DualAR is autoregressive: the slow AR generates semantic tokens one at a time, feeding each output back as input, maintaining KV-cache state. This introduces new concerns: degenerate loop detection, variable latency (O(n) passes), KV-cache memory growth, and sampling stochasticity (temperature, top-k).

## Architecture

### Layer 1: Audio8TextToSpeech

```
Audio8TextToSpeech implements TextToSpeechService, AutoCloseable
├── Audio8Tokenizer        — text → token IDs (JSON tokenizer parsing)
├── DualARLoop             — autoregressive inference (slow AR → fast AR)
│   ├── slow_ar session    — semantic token prediction with KV-cache
│   └── fast_ar session    — codec codebook prediction per frame
├── CodecDecoder           — codec tokens → PCM waveform
└── VoiceRegistry          — voice cloning lifecycle
    └── codec_encoder session (transient — loaded for registration only)
```

**Public API:**
```java
public final class Audio8TextToSpeech implements TextToSpeechService, AutoCloseable {
    // Zero-install factories
    static Audio8TextToSpeech withDefaults(String modelVariant);  // "0.1b" or "0.6b"
    static Audio8TextToSpeech withDefaults();                     // defaults to "0.1b"

    // TextToSpeechService
    SynthesisResult synthesise(String text, SynthesisOptions options);

    // Voice cloning
    String registerVoice(Path referenceAudio);   // returns voice ID
    void releaseVoice(String voiceId);
    Set<String> registeredVoices();

    // AutoCloseable
    void close();
}
```

**Voice cloning lifecycle:** `registerVoice()` transiently loads the codec encoder session, encodes the reference audio into codec tokens, caches the tokens under an opaque voice ID, then releases the encoder session. Subsequent `synthesise()` calls with `SynthesisOptions.voice(voiceId)` condition the slow AR on the cached codec tokens. Memory: steady-state holds only the cached tokens (~KB per voice), not the encoder session (~hundreds of MB).

**Internal stages** (package-private, unit-testable):

| Class | Responsibility | Testable in isolation? |
|-------|---------------|----------------------|
| `Audio8Tokenizer` | Parse `tokenizer.json`, encode text to token IDs | Yes — pure function |
| `DualARLoop` | Autoregressive slow AR → fast AR loop with KV-cache | Yes — mock ONNX sessions, verify token feeding and termination |
| `CodecDecoder` | Codec tokens → PCM float[] → WAV bytes | Yes — known codec tokens → expected audio shape |
| `VoiceRegistry` | Register/release voice IDs, manage cached codec tokens | Yes — in-memory lifecycle |

**Model variants:**

| Variant | Architecture | Quantization | Size | Provisioner method |
|---------|-------------|-------------|------|-------------------|
| 0.1B | Falcon-H1 hybrid (attention + Mamba) | INT8 | ~1 GB | `ensureAudio8Model("0.1b")` |
| 0.6B | 24-layer transformer | INT4 | ~1 GB | `ensureAudio8Model("0.6b")` |

Both share the same inference loop — different ONNX files and ORT session options. The variant is a constructor parameter, not a code path difference.

**ONNX sessions per variant:**

| Session | File | Lifecycle |
|---------|------|-----------|
| Slow AR | `slow_ar_{int8,int4}.onnx` | Persistent (created at construction) |
| Fast AR | `fast_ar_{int8,int4}.onnx` | Persistent |
| Codec decoder | `codec_decoder_fp16.onnx` | Persistent |
| Codec encoder | `codec_encoder_fp16.onnx` | Transient (loaded during `registerVoice()`, released after) |

**Autoregressive loop guards:**
- Max token count — hard stop to prevent degenerate loops
- End-of-sequence token detection
- Configurable via `Audio8Config` record (with sensible defaults)

### Layer 2: Composable Lip-Sync Pipeline

```
PhonemeAligner (SPI)
├── EspeakPhonemeAligner     — espeak-ng phonemization + duration estimation
└── (future) Wav2VecAligner  — wav2vec2 ONNX forced alignment

LipSyncEnricher (decorator)
└── wraps any TextToSpeechService
```

**`PhonemeAligner` SPI:**
```java
@FunctionalInterface
public interface PhonemeAligner {
    List<PhonemeTiming> align(String text, byte[] audioData, int sampleRate);
}
```

**`EspeakPhonemeAligner`** (initial implementation):
- Uses existing `EspeakLibrary` FFM bindings to phonemize text
- Estimates per-phoneme durations from espeak-ng's synthesis timing
- Proportionally maps estimated durations to actual audio duration
- Zero new dependencies — espeak-ng is already integrated for `VitsTextToSpeech`

**`LipSyncEnricher`** (decorator):
```java
public final class LipSyncEnricher implements TextToSpeechService {
    static TextToSpeechService wrap(TextToSpeechService delegate, PhonemeAligner aligner);

    SynthesisResult synthesise(String text, SynthesisOptions options) {
        SynthesisResult result = delegate.synthesise(text, options);
        if (!result.phonemes().isEmpty()) {
            return result;  // native timing available (e.g. VitsTextToSpeech)
        }
        List<PhonemeTiming> timing = aligner.align(text, result.audioData(), sampleRate);
        return new SynthesisResult(result.audioData(), result.audioFormat(), timing);
    }
}
```

**Key design property:** The enricher only acts when phonemes are empty. `VitsTextToSpeech` native duration-predictor timing is always preferred. No ambiguity about which phonemes win — empty list triggers enrichment, non-empty passes through unchanged.

**Composable usage:**
```java
// Audio8 with lip-sync
TextToSpeechService tts = Audio8TextToSpeech.withDefaults("0.6b");
TextToSpeechService withLipSync = LipSyncEnricher.wrap(tts, new EspeakPhonemeAligner());

// Kokoro with lip-sync (previously had no lip-sync at all)
TextToSpeechService kokoro = KokoroTextToSpeech.withDefaults();
TextToSpeechService kokoroWithLipSync = LipSyncEnricher.wrap(kokoro, new EspeakPhonemeAligner());

// VITS — not wrapped, native timing is better
TextToSpeechService vits = VitsTextToSpeech.withDefaults("en_US-lessac-medium");
```

### Layer 3: Provisioner Extensions

User-selected model download. Provisioner lists available models with sizes; the user chooses what to download. CaseHub does not distribute models.

```java
// Audio8 models
Provisioner.ensureAudio8Model("0.1b");  // ~1 GB download
Provisioner.ensureAudio8Model("0.6b");  // ~1 GB download

// Future: Dia models
Provisioner.ensureDiaModel();           // ~6.5 GB download
```

Each method follows the existing pattern: double-check lock, download from HuggingFace, SHA-256 verify, extract, atomic move.

Model directory layout:
```
~/.casehub/models/audio8-tts/
├── 0.1b/
│   ├── slow_ar_int8.onnx
│   ├── fast_ar_int8.onnx
│   ├── codec_decoder_fp16.onnx
│   ├── codec_encoder_fp16.onnx  (optional — for voice cloning)
│   ├── tokenizer/tokenizer.json
│   └── runtime_manifest.json
└── 0.6b/
    ├── slow_ar_int4.onnx(.data)
    ├── fast_ar_int4.onnx(.data)
    ├── codec_decoder_fp16.onnx(.data)
    ├── codec_encoder_fp16.onnx(.data)
    ├── tokenizer/tokenizer.json
    └── runtime_manifest.json
```

### Integration with SpeechSession

No changes to `SpeechSession` or the WebSocket protocol. The existing sentence-by-sentence loop works as-is:

```java
// SpeechSession already does:
SynthesisResult synthesis = tts.synthesise(sentence, options);
var visemeFrames = VisemeMapping.convert(synthesis.phonemes());
send(new AvatarMessage.Phonemes(visemeFrames));
binarySink.accept(synthesis.audioData());
```

The `LipSyncEnricher` fills phonemes before `SpeechSession` sees the result. `TtsModelRegistry` registers the enriched service, not the raw engine.

## Future Extensibility

### Dia TTS (#214)

[Dia ONNX](https://huggingface.co/onnx-community/Dia-1.6B-0626-ONNX) already exists (encoder 1 GB + decoder 5.4 GB). Same composable pattern:

```java
TextToSpeechService dia = DiaTextToSpeech.withDefaults();
TextToSpeechService withEmotions = EmotionInjector.wrap(dia, moodOrchestrator);
TextToSpeechService withLipSync = LipSyncEnricher.wrap(withEmotions, aligner);
```

`EmotionInjector` is a text-preprocessing decorator: maps `MoodOrchestrator` PAD state to Dia tokens (`[laugh]`, `[sigh]`) and injects them before synthesis. Reusable — any TTS with emotion token support can use it.

Dia's architecture (encoder-decoder transformer with DAC codec) fits the same `TextToSpeechService` contract. The `PhonemeAligner` SPI provides lip-sync. Voice cloning uses the same `registerVoice()` pattern.

### Alignment upgrade path

The `PhonemeAligner` SPI enables transparent quality upgrades:
- **Today:** `EspeakPhonemeAligner` — zero dependencies, approximate timing
- **Later:** `Wav2VecPhonemeAligner` — wav2vec2 ONNX, accurate forced alignment
- Swap is a single-class replacement behind the same interface

## Testing Strategy

| Component | Test type | What it verifies |
|-----------|-----------|-----------------|
| `Audio8Tokenizer` | Unit | Tokenizer parsing, encoding correctness against Python reference |
| `DualARLoop` | Unit (mocked sessions) | Token feeding, KV-cache protocol, EOS detection, max-length guard |
| `CodecDecoder` | Unit (mocked session) | Codec token → audio shape, WAV encoding |
| `VoiceRegistry` | Unit | Register/release lifecycle, ID uniqueness, cleanup on close |
| `EspeakPhonemeAligner` | Unit | Phonemization, duration estimation, proportional mapping |
| `LipSyncEnricher` | Unit | Pass-through when phonemes exist, enrichment when empty |
| `Audio8TextToSpeech` | Integration | End-to-end synthesis with real model (requires model download) |
| `Provisioner` | Integration | Model download, checksum verification, idempotent re-provisioning |

Integration tests that require model downloads are guarded by a system property (`-Dspeech.integration=true`) — they don't run in CI by default.

## Implementation approach

Study the [Audio8_TTS Python onnx_runtime](https://github.com/Audio8-AI/Audio8_TTS) code before porting. The autoregressive inference protocol (KV-cache management, token feeding sequence, sampling parameters) must match exactly. Port from working code, not from documentation.

## Module placement

All new code lives in the `speech-sherpa` module alongside existing TTS implementations. The `PhonemeAligner` SPI and `LipSyncEnricher` go in `speech-api` (they're engine-independent). `EspeakPhonemeAligner` goes in `speech-sherpa` (depends on `EspeakLibrary`).

| Class | Module | Package |
|-------|--------|---------|
| `PhonemeAligner` | speech-api | `io.casehub.blocks.speech` |
| `LipSyncEnricher` | speech-api | `io.casehub.blocks.speech` |
| `Audio8TextToSpeech` | speech-sherpa | `io.casehub.blocks.speech.sherpa` |
| `Audio8Tokenizer` | speech-sherpa | `io.casehub.blocks.speech.sherpa` |
| `DualARLoop` | speech-sherpa | `io.casehub.blocks.speech.sherpa` |
| `CodecDecoder` | speech-sherpa | `io.casehub.blocks.speech.sherpa` |
| `VoiceRegistry` | speech-sherpa | `io.casehub.blocks.speech.sherpa` |
| `Audio8Config` | speech-sherpa | `io.casehub.blocks.speech.sherpa` |
| `EspeakPhonemeAligner` | speech-sherpa | `io.casehub.blocks.speech.sherpa` |

## References

- [Audio8/audio8-TTS-0.1B-ONNX-INT8](https://huggingface.co/Audio8/audio8-TTS-0.1B-ONNX-INT8) — 0.1B model card
- [Audio8/Audio8-TTS-Preview-0.6B-ONNX-INT4](https://huggingface.co/Audio8/Audio8-TTS-Preview-0.6B-ONNX-INT4) — 0.6B model card
- [Audio8-AI/Audio8_TTS](https://github.com/Audio8-AI/Audio8_TTS) — reference Python implementation
- [onnx-community/Dia-1.6B-0626-ONNX](https://huggingface.co/onnx-community/Dia-1.6B-0626-ONNX) — Dia ONNX (future #214)
- [GitHub fishaudio/fish-speech#903](https://github.com/fishaudio/fish-speech/issues/903) — Fish Speech ONNX export failure
- [Visemes: Bringing Digital Humans to Life](https://springct.com/technicalarticles/visemes-bringing-digital-humans-to-life/) — lip-sync best practices
- `VitsTextToSpeech.java` — existing direct-ONNX TTS with phoneme timing
- `KokoroTextToSpeech.java` — existing sherpa-onnx TTS
- `EspeakLibrary.java` — existing espeak-ng FFM bindings
- `OnnxRuntimeLibrary.java` — existing ONNX Runtime FFM bindings
- `VisemeMapping.java` — existing phoneme → viseme mapping
- `SpeechSession.java` — existing sentence-by-sentence synthesis loop
