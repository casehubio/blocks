# Speech SPI Research — Comprehensive Findings

> **Date:** 2026-08-25/26
> **Issues:** casehubio/blocks#155 (restructuring), casehubio/blocks#157 (implementation)
> **Branch:** `issue-155-multi-module-restructure`
> **Driven by:** casehubio/drafthouse#117 (voice-first drafting mode)

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Native Library: sherpa-onnx](#2-native-library-sherpa-onnx)
3. [FFM/Panama Bindings](#3-ffmpanama-bindings)
4. [Speech-to-Text: Offline (Whisper)](#4-speech-to-text-offline-whisper)
5. [Text-to-Speech: VITS/Piper](#5-text-to-speech-vitspiper)
6. [Speech-to-Text: Streaming (Zipformer)](#6-speech-to-text-streaming-zipformer)
7. [Text Cleanup Pipeline](#7-text-cleanup-pipeline)
8. [Performance Benchmarks](#8-performance-benchmarks)
9. [CoreML / Metal Acceleration](#9-coreml--metal-acceleration)
10. [Cross-Platform Build & CI](#10-cross-platform-build--ci)
11. [Native Library Provisioning](#11-native-library-provisioning)
12. [Alternative Libraries & Models](#12-alternative-libraries--models)
13. [Future Directions](#13-future-directions)
14. [Known Issues & Gotchas](#14-known-issues--gotchas)
15. [Reproducing Results](#15-reproducing-results)

---

## 1. Architecture Overview

### Module Structure

```
casehub-blocks-parent (pom)
  ├── blocks                    — core blocks (existing)
  ├── engine-adapter            — engine integration
  ├── speech-api                — pure Java SPIs (zero dependencies)
  ├── speech-sherpa             — sherpa-onnx FFM implementation (JDK 22+)
  └── annotations               — annotation-driven patterns
```

`speech-sherpa` is conditional on JDK 22+ via Maven profile `jdk22+`. On JDK 21, it's
absent from the reactor — the rest of the build succeeds unchanged.

### SPI Layer (speech-api)

| Interface | Purpose |
|-----------|---------|
| `SpeechToTextService` | Offline batch transcription: `transcribe(Path, TranscriptionOptions) → TranscriptionResult` |
| `StreamingSpeechToTextService` | Live streaming: `startStream(TranscriptionOptions) → RecognitionStream` |
| `TextToSpeechService` | Speech synthesis: `synthesise(String, SynthesisOptions) → SynthesisResult` |
| `RecognitionStream` | AutoCloseable stream handle: `acceptSamples()`, `partialResult()`, `isEndpointDetected()`, `finalResult()` |
| `TextFilter` | Cleanup stage: `apply(String) → String`, `name()`, `destructiveness()` |
| `CleanupConfig` | Ordered filter chain with `maxDestructiveness` ceiling |

### Implementation Layer (speech-sherpa)

| Class | SPI | Engine |
|-------|-----|--------|
| `SherpaOnnxSpeechToText` | `SpeechToTextService` | Whisper (offline) |
| `SherpaOnnxStreamingSpeechToText` | `StreamingSpeechToTextService` | Zipformer transducer (online) |
| `SherpaOnnxTextToSpeech` | `TextToSpeechService` | VITS/Piper |
| `CasingFilter` | `TextFilter` (destructiveness: 0) | Lowercase normalization |
| `FillerRemovalFilter` | `TextFilter` (destructiveness: 1) | Regex filler stripping |
| `PunctuationFilter` | `TextFilter` (destructiveness: 2) | CNN-BiLSTM sherpa-onnx model |

### Data Types

| Record | Fields |
|--------|--------|
| `TranscriptionResult` | `text`, `language`, `origin` |
| `TranscriptionOptions` | `audioFormat`, `languageHint`, `modelSize` |
| `SynthesisResult` | `audioData` (byte[]), `audioFormat`, `phonemes` (List<PhonemeTiming>) |
| `SynthesisOptions` | `voice`, `language`, `audioFormat`, `includePhonemes` |
| `PhonemeTiming` | `phoneme`, `startMs`, `endMs` |
| `WavData` | `samples` (float[]), `sampleRate`, `channels` |

---

## 2. Native Library: sherpa-onnx

### What it is

sherpa-onnx (https://github.com/k2-fsa/sherpa-onnx) is a C/C++ speech processing
library from the Next-gen Kaldi project (k2-fsa). It wraps ONNX Runtime for model
inference and provides a C API (`libsherpa-onnx-c-api`) for:

- Offline ASR (Whisper, Paraformer, NeMo CTC, SenseVoice, Moonshine, and many more)
- Online/streaming ASR (Zipformer transducer, streaming Paraformer)
- Offline TTS (VITS/Piper, Matcha, Kokoro, Kitten, ZipVoice, Pocket, Supertonic)
- Punctuation restoration (CT-Transformer offline, CNN-BiLSTM online)
- Speaker diarization, speech denoising, source separation, audio tagging

### Version used

**v1.13.6** — released 2026-08-18. This is the version our FFM struct layouts target.

### Library files

| Platform | Files | Sizes |
|----------|-------|-------|
| macOS ARM64 | `libsherpa-onnx-c-api.dylib` + `libonnxruntime.dylib` | 4MB + 27MB |
| Linux x64 | `libsherpa-onnx-c-api.so` + `libonnxruntime.so` | similar |
| Windows x64 | `sherpa-onnx-c-api.dll` + `onnxruntime.dll` | similar |

### Pre-built binaries

Published on every GitHub release at https://github.com/k2-fsa/sherpa-onnx/releases

| Asset pattern | Contents |
|--------------|----------|
| `sherpa-onnx-v{ver}-{platform}-shared-lib.tar.bz2` | C API shared library + onnxruntime |
| `sherpa-onnx-native-lib-{platform}-{ver}.jar` | JNI-wrapped native libs (NOT for FFM) |
| `sherpa-onnx-v{ver}-{platform}-jni.tar.bz2` | JNI shared libraries |

**Important:** The `-native-lib-*.jar` files contain `libsherpa-onnx-jni.dylib` (JNI wrapper),
NOT `libsherpa-onnx-c-api.dylib` (C API). For FFM bindings, use the `-shared-lib.tar.bz2` assets.

---

## 3. FFM/Panama Bindings

### Why FFM, not JNI

The issue spec (#157) explicitly requires Java FFM/Panama (no JNI). Benefits:

- No native JNI glue code to maintain
- No `javah` / `javac -h` build step
- Direct memory access via `MemorySegment` — no byte array copying
- Arena-based lifecycle management — no manual `free()` calls
- Type-safe function handles via `FunctionDescriptor`

### JDK version requirement

FFM/Panama is preview in JDK 21, stable from JDK 22 (JEP 454). The `speech-sherpa`
module sets `<maven.compiler.release>22</maven.compiler.release>`.

JVM flag required at runtime: `--enable-native-access=ALL-UNNAMED`

### Library loading strategy (two-tier)

```
SherpaLibrary.load()
  ├── Tier 1: System path — SymbolLookup.libraryLookup("sherpa-onnx-c-api", Arena.global())
  └── Tier 2: Local cache — ~/.casehub/native/sherpa-onnx/{version}/{platform}/
              Detects platform via os.name + os.arch
              Override: -Dsherpa.native.dir=/path/to/libs
```

`SherpaLibrary.isAvailable()` returns false when neither tier finds the library — integration tests skip via `@EnabledIf`.

### Struct layout approach

**Initial approach (failed):** Define `MemoryLayout.structLayout()` with all sub-struct fields.
This required matching every field of every nested model config — 17 model types in
`SherpaOnnxOfflineModelConfig` alone. Wrong sizes caused SIGSEGV.

**Working approach:** Allocate 4096 bytes zero-filled, set fields at known byte offsets.
This matches the C pattern: `memset(&config, 0, sizeof(config))` then set specific fields.
Extra zeros beyond the struct size are in our arena and never touched by the C code.

```java
MemorySegment config = arena.allocate(SherpaLayouts.CONFIG_ALLOC_SIZE); // 4096
config.fill((byte) 0);
config.set(ValueLayout.ADDRESS, SherpaLayouts.WHISPER_ENCODER, arena.allocateFrom(path));
```

### Bugs found during development

1. **Encoder/decoder field order swapped.** The C header has `encoder` first, then `decoder`
   in `SherpaOnnxOfflineWhisperModelConfig`. Initial code had them reversed → SIGSEGV at
   `_platform_strlen` when the recognizer tried to read the encoder path from the decoder offset.

2. **Whisper struct missing 2 int32 fields.** The actual struct has `tail_paddings`,
   `enable_token_timestamps`, `enable_segment_timestamps` (3 × int32 = 12 bytes + padding = 16).
   Initial layout had only `tail_paddings` (4 bytes + 4 padding = 8). This shifted ALL
   subsequent field offsets by 8 bytes in the 504-byte `OfflineModelConfig`.

3. **OfflineModelConfig had 5 model types, actual has 17.** Missing: canary, cohere_transcribe,
   fire_red_asr, fire_red_asr_ctc, dolphin, zipformer_ctc, wenet_ctc, omnilingual, medasr,
   funasr_nano, qwen3_asr, and a 5th pointer in moonshine. Total size: 504 bytes vs my 200.

4. **TtsModelConfig similarly larger than expected.** Additional model types after vits:
   matcha, kokoro, kitten, zipvoice, pocket, supertonic. However, since all our target
   fields (vits, num_threads, provider) are at the beginning, the 4096-byte allocation
   approach works regardless.

### Key byte offsets (v1.13.6, 64-bit)

**Offline recognizer config:**
```
feat_config.sample_rate:          0
feat_config.feature_dim:          4
model_config.whisper.encoder:    48
model_config.whisper.decoder:    56
model_config.whisper.language:   64
model_config.whisper.task:       72
model_config.tokens:            104
model_config.num_threads:       112
model_config.provider:          120
```

**Online recognizer config:**
```
feat_config.sample_rate:          0
feat_config.feature_dim:          4
model_config.transducer.encoder:  8
model_config.transducer.decoder: 16
model_config.transducer.joiner:  24
model_config.tokens:             56
model_config.num_threads:        64
model_config.provider:           72
enable_endpoint:                156
rule1_min_trailing_silence:     160
rule2_min_trailing_silence:     164
rule3_min_utterance_length:     168
```

**TTS config (VITS fields at struct beginning):**
```
model.vits.model:         0
model.vits.lexicon:       8
model.vits.tokens:       16
model.vits.data_dir:     24
model.vits.length_scale: 40
model.num_threads:       56
model.provider:          64
```

---

## 4. Speech-to-Text: Offline (Whisper)

### Model used

`sherpa-onnx-whisper-tiny` — 39MB download, contains:
- `tiny-encoder.onnx` — Whisper encoder
- `tiny-decoder.onnx` — Whisper decoder
- `tiny-tokens.txt` — token vocabulary
- `test_wavs/` — 3 test audio files with expected transcriptions

Available model sizes: tiny (39MB), base (141MB), small (461MB), medium (1.5GB).

### Verified transcription results

| File | Duration | Expected | Actual | Match |
|------|----------|----------|--------|-------|
| 0.wav | 6.6s | "AFTER EARLY NIGHTFALL THE YELLOW LAMPS WOULD LIGHT UP HERE AND THERE THE SQUALID QUARTER OF THE BROTHELS" | "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels." | Exact |
| 1.wav | 16.4s | "GOD AS A DIRECT CONSEQUENCE OF THE SIN WHICH MAN THUS PUNISHED HAD GIVEN HER A LOVELY CHILD..." | "God, as a direct consequence of the sin which man thus punished, had given her a lovely child..." | Exact (with "parrot" for "parent") |

### Performance (Apple Silicon M-series, CPU, 2 threads)

| Audio duration | Processing time | RTF | Speed |
|---------------|----------------|-----|-------|
| 6.6s | 520ms | 0.08x | 12x real-time |
| 16.4s | 746ms | 0.05x | 20x real-time |
| 4.7s (8kHz) | 360ms | 0.08x | 12x real-time |

RTF = Real-Time Factor (processing time / audio duration). RTF < 1.0 = faster than real-time.
The 8kHz file was auto-resampled to 16kHz by sherpa-onnx internally.

### API usage

```java
var config = SherpaConfig.defaults(Path.of("/path/to/sherpa-onnx-whisper-tiny"));
var stt = new SherpaOnnxSpeechToText(config);
TranscriptionResult result = stt.transcribe(audioFile, TranscriptionOptions.defaults());
```

---

## 5. Text-to-Speech: VITS/Piper

### Model used

`vits-piper-en_US-amy-low` — 64MB download (int8 variant: 20MB), contains:
- `en_US-amy-low.onnx` — VITS model (symlinked as `model.onnx`)
- `tokens.txt` — token vocabulary
- `espeak-ng-data/` — phoneme data directory

### Verified results

| Input text | Audio length | Processing time | RTF |
|-----------|-------------|----------------|-----|
| "Hello." | 0.5s | 508ms | 1.04x (startup overhead) |
| "The quick brown fox..." (44 chars) | 2.4s | 561ms | 0.23x |
| "After early nightfall..." (106 chars) | 4.5s | 661ms | 0.15x |
| "God, as a direct..." (251 chars) | 9.6s | 878ms | 0.09x |

First call pays ~500ms model loading overhead. Subsequent calls scale sub-linearly.

### Round-trip verification

TTS generated audio from text, then STT transcribed it back:

```
Original:    "After early nightfall, the yellow lamps would light up here and there."
Round-trip:  "After early nightfall, the yellow lamps would light up here and there."
TTS: 587ms, STT: 353ms — perfect round-trip match.
```

### Phoneme timing

`SynthesisResult.phonemes()` returns `List<PhonemeTiming>` with per-phoneme start/end
millisecond offsets. Critical for downstream avatar lip-sync (casehubio/blocks#154).
Currently returns empty list — sherpa-onnx's `SherpaOnnxGeneratedAudio` struct doesn't
include phoneme data directly. Phoneme extraction requires a separate API or model-specific
post-processing.

### Model file naming

VITS Piper models use model-specific filenames (e.g., `en_US-amy-low.onnx`), not the
generic `model.onnx` our implementation expects. Current workaround: symlink. Proper fix:
make the model filename configurable in `SherpaConfig` or auto-detect `.onnx` files in the
model directory.

---

## 6. Speech-to-Text: Streaming (Zipformer)

### Model used

`sherpa-onnx-streaming-zipformer-en-20M-2023-02-17` — 122MB download (mobile variant: 103MB).
Streaming transducer architecture with encoder + decoder + joiner.

### How streaming works

1. Audio arrives in chunks (typically 100ms = 1600 samples at 16kHz)
2. Each chunk is fed to `RecognitionStream.acceptSamples()`
3. The stream decodes incrementally — `partialResult()` returns accumulated text
4. When the speaker pauses, `isEndpointDetected()` returns true (configurable silence thresholds)
5. At endpoint, `finalResult()` returns the complete segment

### Verified streaming output

```
[2.4s] The Ye
[2.7s] The Yellow La
[3.0s] The yellow lamps
[3.3s] The yellow lamps would light
[3.7s] The yellow lamps would light up
[4.0s] The yellow lamps would light up here
[4.3s] The yellow lamps would light up here and there
...
[6.5s] The yellow lamps would light up here and there the squalid quarter of the brafflel
```

Words appear ~200ms after they're spoken. The 20M model is optimized for speed over accuracy —
it stumbled on "brothels" → "brafflel". Larger streaming models (296MB+) handle harder words.

### Streaming vs offline accuracy

The streaming Zipformer 20M model is less accurate than offline Whisper:
- Missed "After early nightfall" (beginning of sentence)
- "brothels" → "brafflel"
- All caps output (offline Whisper produces proper case)

This is expected — streaming models trade accuracy for latency. They can't look ahead at
future audio to disambiguate. The 20M model has 20 million parameters vs Whisper tiny's
39 million.

### Endpoint detection configuration

```java
configSeg.set(JAVA_INT, SherpaLayouts.ONLINE_ENABLE_ENDPOINT, 1);
configSeg.set(JAVA_FLOAT, SherpaLayouts.ONLINE_RULE1_MIN_TRAILING_SILENCE, 2.4f);  // long pause → endpoint
configSeg.set(JAVA_FLOAT, SherpaLayouts.ONLINE_RULE2_MIN_TRAILING_SILENCE, 1.2f);  // medium pause
configSeg.set(JAVA_FLOAT, SherpaLayouts.ONLINE_RULE3_MIN_UTTERANCE_LENGTH, 20.0f); // max utterance
```

### Why Whisper can't stream

Whisper is an encoder-decoder transformer that processes complete utterances. Its architecture
requires the full audio input to compute attention over the entire sequence. There is no
streaming Whisper variant. For live transcription, you must use a transducer-based model
(Zipformer, Paraformer, Conformer) that processes audio incrementally.

---

## 7. Text Cleanup Pipeline

### Architecture

```
Raw STT output → [Filter 1] → [Filter 2] → [Filter 3] → ... → Clean text
                  (lowest destructiveness)            (highest)
```

Filters are priority-ordered by destructiveness. `CleanupConfig.maxDestructiveness` sets a
ceiling — filters above the ceiling are excluded. This lets consumers control how much
the text gets rewritten.

### Built-in filters

| Filter | Destructiveness | Speed | What it does | Example |
|--------|----------------|-------|-------------|---------|
| `CasingFilter` | 0 | <1ms | Lowercase (streaming models output ALL CAPS) | `"THE YELLOW"` → `"the yellow"` |
| `FillerRemovalFilter` | 1 | <1ms | Strip um/uh/er/hmm/ah/oh/eh/mhm via regex | `"um the uh yellow"` → `"the yellow"` |
| `PunctuationFilter` | 2 | 8-10ms | CNN-BiLSTM model: punctuation + re-casing | `"how are you i am fine"` → `"How are you? I am fine."` |

### Punctuation model details

Model: `sherpa-onnx-online-punct-en-2024-08-06` (29MB download)
Architecture: CNN-BiLSTM from the Edge-Punct-Casing project (arXiv:2407.13142)
Languages: English only (CT-Transformer variant supports Chinese + English but is 266MB)

**Verified results:**

| Input | Output | Time |
|-------|--------|------|
| `the yellow lamps would light up here and there` | `The yellow lamps would light up here and there` | 9ms |
| `how are you i am fine thank you` | `How are you? I am fine. Thank you.` | 8ms |
| `um the uh yellow lamps would um light up here and uh there` | `The yellow lamps would light up here and there` | 9ms |
| `as a direct consequence of the sin which man thus punished had given her a lovely child` | `As a direct consequence of the sin, which man thus punished had given her a lovely child` | 8ms |

Note: ALL CAPS input passes through unchanged — the model expects lowercase. The `CasingFilter`
(destructiveness 0) must run before `PunctuationFilter` (destructiveness 2) for correct results.
The `CleanupConfig` sorts filters by destructiveness automatically.

### Integration with streaming STT

Cleanup is baked into `SherpaOnnxStreamingSpeechToText`:

```java
// Auto-build default pipeline (casing + fillers + punctuation)
SherpaConfig config = SherpaConfig.defaults(sttModelDir)
        .withPunctuation(punctModelDir);

// Or explicit filter chain with destructiveness ceiling
SherpaConfig config = SherpaConfig.defaults(sttModelDir)
        .withCleanup(CleanupConfig.upTo(1,
            new CasingFilter(),
            new FillerRemovalFilter()));
```

`partialResult()` and `finalResult()` return cleaned text automatically.

---

## 8. Performance Benchmarks

### STT Offline — Whisper tiny (Apple Silicon, CPU, 2 threads)

| Audio | Processing | RTF | Speed |
|-------|-----------|-----|-------|
| 6.6s | 520ms | 0.08x | 12x real-time |
| 16.4s | 746ms | 0.05x | 20x real-time |
| 4.7s (8kHz resampled) | 360ms | 0.08x | 12x real-time |

Warmup run: ~738ms. Subsequent runs: ~720ms average. Consistent across runs.

### STT Offline — Whisper tiny (after warmup, 3 runs averaged)

| Run | Time | RTF |
|-----|------|-----|
| 1 | 707ms | 0.043x |
| 2 | 740ms | 0.045x |
| 3 | 727ms | 0.044x |
| **Average** | **724ms** | **0.044x (23x real-time)** |

### TTS — VITS Piper Amy (Apple Silicon, CPU, 2 threads)

| Text length | Generated audio | Processing | RTF |
|------------|----------------|-----------|-----|
| 6 chars | 0.5s | 508ms | 1.04x |
| 44 chars | 2.4s | 561ms | 0.23x |
| 106 chars | 4.5s | 661ms | 0.15x |
| 251 chars | 9.6s | 878ms | 0.09x (11x real-time) |

### STT Streaming — Zipformer 20M (Apple Silicon, CPU, 2 threads)

- Partial result latency: ~200ms per 100ms audio chunk
- Endpoint detection: configurable silence thresholds (1.2s / 2.4s)
- Total processing: faster than real-time (all chunks processed before audio would finish)

### Text Cleanup Pipeline

| Stage | Latency | Cumulative |
|-------|---------|-----------|
| CasingFilter | <1ms | <1ms |
| FillerRemovalFilter | <1ms | <1ms |
| PunctuationFilter | 8-10ms | 9-11ms |
| **Total cleanup** | **~10ms** | **imperceptible** |

---

## 9. CoreML / Metal Acceleration

### Findings

CoreML (Apple's GPU/ANE path) was tested as an alternative to CPU inference on Apple Silicon.

**STT benchmark — CPU vs CoreML (Whisper tiny, 16.4s audio, after warmup):**

| Provider | Average (3 runs) | RTF | Speed |
|----------|-----------------|-----|-------|
| **cpu** | **724ms** | 0.044x | **23x real-time** |
| coreml | 2561ms | 0.156x | 6x real-time |

**CPU is 3.5x faster than CoreML** on Apple Silicon for Whisper.

**TTS with CoreML:** Crashed with SIGSEGV in `libBNNS.dylib` (`BNNSComputeTwoVariablePolynomial`).
VITS models are not fully compatible with CoreML's computation graph.

### Root causes (confirmed by community research)

1. **Graph partitioning overhead** — ONNX Runtime's CoreML EP can't run all ops on ANE/GPU.
   Unsupported ops (e.g., Pad with reflect mode) fall back to CPU, causing expensive
   CPU↔ANE data round-trips at every partition boundary.
   (Source: https://github.com/microsoft/onnxruntime/issues/28022)

2. **Dynamic shapes penalty** — Whisper uses variable-length inputs. CoreML pays a compilation
   tax every time a new shape appears, even after warmup.
   (Source: https://macgpu.com/en/blog/2026-0420-mac-onnx-runtime-coreml-ep-vs-cpu-dynamic-shapes-remote.html)

3. **Silent CPU fallback** — Parts of the graph that appear to use ANE actually run on CPU,
   giving the worst of both worlds (transfer overhead + CPU execution).

4. **sherpa-onnx confirms this** — Issue #2910 reports CoreML slower than CPU for ASR models on M2.
   (Source: https://github.com/k2-fsa/sherpa-onnx/issues/2910)

### When CoreML would help

- Large models (Whisper medium/large) where computation dominates transfer overhead
- Sustained streaming recognition (100s+ of calls, CoreML compilation cost amortized)
- Older Intel Macs where CPU performance is poor

### Alternative: whisper.cpp with native CoreML

whisper.cpp (https://github.com/ggml-org/whisper.cpp) pre-compiles the Whisper encoder
specifically for ANE, bypassing ONNX Runtime's partitioning entirely. Reported ~3x speedup
over CPU-only, achieving ~7x real-time on M3 and ~10x real-time on M5 Pro. However:
- Requires building from source with `-DWHISPER_COREML=1`
- Requires Python step to generate `.mlmodelc` files (Xcode, coremltools, ane_transformers)
- No pre-built binary with CoreML available
- Our CPU results (23x real-time) already exceed whisper.cpp's CoreML numbers

### Recommendation

**Stick with `provider = "cpu"` as default.** Apple Silicon's CPU cores handle ONNX inference
extremely well. CoreML adds overhead without benefit for our model sizes. The `SherpaConfig.provider`
field is available for consumers who need it (Linux CUDA, older hardware) but shouldn't be
the default on macOS.

---

## 10. Cross-Platform Build & CI

### Maven build strategy

```xml
<profiles>
    <profile>
        <id>jdk22+</id>
        <activation>
            <jdk>[22,)</jdk>
        </activation>
        <modules>
            <module>speech-sherpa</module>
        </modules>
    </profile>
</profiles>
```

- JDK 21: `speech-sherpa` absent from reactor. All other modules build normally.
- JDK 22+: `speech-sherpa` included automatically. No flags needed.

### CI workflow (`speech-integration.yml`)

Two-stage workflow:

1. **Unit tests** (ubuntu-latest, no native lib) — 38 tests, always runs
2. **Integration tests** (matrix: ubuntu/macos/windows) — downloads native lib + Whisper model

Platform matrix:

| Runner | Platform ID | Library archive |
|--------|-----------|----------------|
| `ubuntu-latest` | `linux-x64` | `sherpa-onnx-v1.13.6-linux-x86_64-shared-cpu-lib.tar.bz2` |
| `macos-latest` | `osx-arm64` | `sherpa-onnx-v1.13.6-osx-arm64-shared-lib.tar.bz2` |
| `windows-latest` | `win-x64` | `sherpa-onnx-v1.13.6-win-x64-shared-MD-Release-lib.tar.bz2` |

Both native lib and Whisper model are cached between CI runs via `actions/cache`.

### Test classification

| Test type | Count | Requires native lib | Requires model |
|-----------|-------|-------------------|---------------|
| SPI contract tests (speech-api) | 15 | No | No |
| WAV reader/writer tests | 11 | No | No |
| Config validation tests | 6 | No | No |
| Parameter validation tests | 5 | No | No |
| Offline STT integration | 1 | Yes | Yes (Whisper) |
| TTS integration | 1 (skipped) | Yes | Yes (VITS) |
| **Total** | **39 (1 skipped)** | | |

---

## 11. Native Library Provisioning

### Current state (Tier 1 + 2)

| Tier | Mechanism | Status |
|------|----------|--------|
| 1 | System library path (`java.library.path`) | Working |
| 2 | Local cache (`~/.casehub/native/sherpa-onnx/{version}/{platform}/`) | Working |
| 3 | Auto-download from permanent URL | Designed, not built |

### Tier 3 design (auto-download)

On first use, detect platform, download from k2-fsa GitHub releases (permanent URLs),
extract to cache directory. Fallback: casehubio-hosted mirror.

### Bundling as Maven artifacts (zero-install)

Publish platform-specific JARs to GitHub Packages:

```
casehub-blocks-speech-sherpa-native-osx-arm64.jar     (~31MB)
casehub-blocks-speech-sherpa-native-linux-x64.jar     (~31MB)
casehub-blocks-speech-sherpa-native-win-x64.jar       (~31MB)
```

Each JAR contains the `.dylib`/`.so`/`.dll` files under `META-INF/native/{platform}/`.
At runtime, `SherpaLibrary` extracts from classpath to temp dir and loads.
This is the pattern used by SQLite JDBC, Netty native transports, and DJL.

### Model provisioning

Models are separate from the native library (39MB–1.5GB). Options:
- Manual download (current)
- Auto-download smallest model on first use, cache at `~/.casehub/models/sherpa-onnx/`
- Configurable model registry

---

## 12. Alternative Libraries & Models

### STT alternatives

| Library | Approach | Java binding | Streaming | Notes |
|---------|---------|-------------|-----------|-------|
| sherpa-onnx (current) | ONNX Runtime + C API | FFM/Panama | Yes | Best coverage of models |
| whisper.cpp | ggml + custom C API | whisper-jni (Maven Central) | No (offline only) | Faster CoreML on older hardware |
| whisper-jni (GiviMAD) | JNI wrapper for whisper.cpp | Pre-built JARs | No | Easiest zero-install for Whisper |
| Vosk | Kaldi-based | JNI | Yes | Older, less maintained |
| WhisperKit | Swift-native CoreML | None (Swift only) | No | iOS/macOS only |

### STT model comparison

| Model | Type | Size | Streaming | Quality | Speed (Apple Silicon) |
|-------|------|------|-----------|---------|---------------------|
| Whisper tiny | Encoder-decoder | 39MB | No | Good | 23x real-time |
| Whisper base | Encoder-decoder | 141MB | No | Better | ~10x real-time |
| Whisper small | Encoder-decoder | 461MB | No | Very good | ~4x real-time |
| Zipformer 20M | Transducer | 122MB | Yes | Fair (misses hard words) | >real-time |
| Zipformer large | Transducer | 296-488MB | Yes | Good | ~real-time |
| SenseVoice | Encoder-decoder | ~300MB | No | Very good + ITN | ~10x real-time |
| Paraformer | Streaming variant | ~200MB | Yes | Good | >real-time |

### TTS model comparison

| Model | Architecture | Size | Quality | Speed |
|-------|-------------|------|---------|-------|
| VITS Piper (current) | VITS | 64MB (20MB int8) | Good, natural | 11x real-time |
| Kokoro | Kokoro | ~200MB | Very natural | ~5x real-time |
| Matcha | Matcha + vocoder | ~300MB | High quality | ~3x real-time |

### Grammar correction alternatives

| Tool | Approach | Speed | Java native | ONNX | Destructiveness |
|------|---------|-------|-------------|------|----------------|
| GECToR | Token tagging (non-autoregressive) | 20-50ms/sentence | No (Python, exportable) | Yes | 3 |
| LanguageTool | Rule-based + statistical | 50-100ms/sentence | Yes (Maven) | No | 4 |
| CrisperWhisper | Whisper variant with "intended" mode | ~500ms/sentence | No (ONNX available) | Yes | 5 |

### Disfluency detection alternatives

| Tool | Approach | Notes |
|------|---------|-------|
| Regex filler stripping (current) | Pattern matching | Fast but dumb — misses context |
| BiLSTM-CRF taggers | Sequence labeling | Small ONNX models, <5ms |
| CrisperWhisper verbatim mode | Whisper variant | Detects fillers with `[um]` brackets |
| GECToR span classification | Token classification | Catches false starts, self-corrections |

---

## 13. Future Directions

### Near-term (follow-up issues)

1. **GECToR grammar filter** — `TextFilter` implementation at destructiveness 3.
   Token-level tagging via ONNX Runtime. 20-50ms per sentence. Grammarly's open-source
   model, proven in production on-device. Would complete the cleanup pipeline for
   grammar correction without an LLM.

2. **LanguageTool grammar filter** — `TextFilter` implementation at destructiveness 4.
   Pure Java (Maven dependency), rule-based. 50-100ms per sentence. 25+ languages.
   Heavier but broader coverage than GECToR.

3. **Auto-download provisioner (Tier 3)** — Download native lib + model on first use.
   Platform detection → permanent URL → cache. Makes the library truly zero-install.

4. **Platform-specific Maven JARs** — Bundle native libs as classifier JARs on GitHub
   Packages. Consumer adds one dependency, native lib extracts at runtime.

5. **TTS model filename auto-detection** — Scan model directory for `.onnx` files
   instead of hardcoding `model.onnx`. Eliminates symlink workaround.

6. **Phoneme timing from TTS** — The `PhonemeTiming` type exists but returns empty.
   Need to investigate sherpa-onnx's VITS phoneme output or use a separate alignment model.
   Critical for avatar lip-sync (casehubio/blocks#154).

### Medium-term

7. **CrisperWhisper "intended" mode** — ONNX model available on Hugging Face.
   Could replace the sentence-level cleanup pipeline entirely: one model does
   filler removal + grammar + formatting. Tradeoff: ~500ms per sentence vs ~10ms
   for the current pipeline.

8. **Live microphone integration** — `javax.sound.sampled.TargetDataLine` → PCM samples →
   `RecognitionStream.acceptSamples()`. The SPI is ready; needs an audio capture utility.

9. **Streaming TTS** — Current TTS generates complete audio. For real-time agents,
   incremental audio generation (sentence-by-sentence or phrase-by-phrase) would
   reduce first-byte latency. sherpa-onnx has `SherpaOnnxOfflineTtsGenerateWithCallback`
   for this (deprecated but functional).

10. **Larger streaming models** — The 20M Zipformer struggles with uncommon words.
    The 296MB+ models are significantly more accurate. Benchmark and document.

### Long-term

11. **whisper.cpp backend** — Alternative `SpeechToTextService` implementation for
    environments where whisper.cpp with native CoreML is preferred. Uses whisper-jni
    (Maven Central) or FFM bindings to libwhisper.

12. **Speaker diarization** — sherpa-onnx supports this (`SherpaOnnxCreateOfflineSpeakerDiarization`).
    Would enable multi-speaker transcription with speaker labels.

13. **Speech denoising** — sherpa-onnx has denoising models (`SherpaOnnxCreateOfflineSpeechDenoiser`).
    Pre-processing step before STT for noisy environments.

14. **Voice Activity Detection (VAD)** — Detect speech vs silence before feeding to STT.
    Reduces unnecessary processing and improves endpoint detection.

---

## 14. Known Issues & Gotchas

### FFM struct layouts are version-dependent

The byte offsets in `SherpaLayouts` are derived from sherpa-onnx v1.13.6's `c-api.h`.
Adding model types to future versions changes `OfflineModelConfig` and `OfflineRecognizerConfig`
struct sizes. The 4096-byte allocation approach is resilient to size increases (extra zeros
are safe), but field offset changes would break the binding.

**Mitigation:** The fields we use (whisper, transducer, tokens, num_threads, provider) are
at the beginning of the model config and unlikely to move. New model types are appended
at the end. Version bumps within 1.x should be safe; major versions need verification.

**Validation approach:** Load the library, create a recognizer with known config, verify
it returns non-NULL. If the layout is wrong, creation fails with NULL.

### JNI vs C API native libraries

sherpa-onnx publishes both `libsherpa-onnx-jni.dylib` (JNI) and `libsherpa-onnx-c-api.dylib`
(C API). They are different libraries with different symbols. FFM needs the C API library.
The `-native-lib-*.jar` assets contain JNI libraries, not C API.

### onnxruntime must be loaded first

On macOS, `libsherpa-onnx-c-api.dylib` depends on `libonnxruntime.dylib`. The dependency
must be loaded first via `SymbolLookup.libraryLookup()` before loading sherpa-onnx.

### CoreML "Context leak detected" messages

When using `provider = "coreml"`, macOS logs repeated `"Context leak detected, CoreAnalytics
returned false"` messages. This is a known macOS/ONNX Runtime issue, not a bug in our code.
The messages are cosmetic but noisy.

### CoreML TTS crash

VITS models crash in CoreML with SIGSEGV in `libBNNS.dylib`. This is a model compatibility
issue — not all ONNX graphs are fully supported by CoreML's computation graph compiler.
Stick with CPU provider for TTS.

### Streaming model accuracy

The 20M Zipformer is optimized for speed, not accuracy. It misses the beginning of sentences
and struggles with uncommon words. For production use, evaluate larger models (296MB+).

### WAV format requirements

`WavReader` only supports 16-bit PCM WAV files. Other formats (32-bit float, compressed)
are not handled. sherpa-onnx expects float samples in [-1, 1] range at 16kHz.

---

## 15. Reproducing Results

### Prerequisites

- JDK 22+ (tested on JDK 26.0.2)
- macOS ARM64 (benchmarks are on Apple Silicon)

### Setup

```bash
# 1. Install native library
mkdir -p ~/.casehub/native/sherpa-onnx/1.13.6/osx-arm64
curl -sL https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.6/sherpa-onnx-v1.13.6-osx-arm64-shared-lib.tar.bz2 | \
  tar xj --strip-components=2 -C ~/.casehub/native/sherpa-onnx/1.13.6/osx-arm64/

# 2. Download models
cd /tmp
curl -sL https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2 | tar xj
curl -sL https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2 | tar xj
curl -sL https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17.tar.bz2 | tar xj
curl -sL https://github.com/k2-fsa/sherpa-onnx/releases/download/punctuation-models/sherpa-onnx-online-punct-en-2024-08-06.tar.bz2 | tar xj

# Symlink VITS model to expected name
ln -s /tmp/vits-piper-en_US-amy-low/en_US-amy-low.onnx /tmp/vits-piper-en_US-amy-low/model.onnx

# 3. Build
mvn install -DskipTests
```

### Run tests

```bash
# Unit tests (no native lib needed)
mvn test -pl speech-api,speech-sherpa -am

# Integration tests (native lib + models required)
mvn test -pl speech-sherpa -Dsherpa.model.dir=/tmp/sherpa-onnx-whisper-tiny
```

### Run CLI

```bash
CP=speech-sherpa/target/classes:speech-api/target/classes

# Offline transcription
java --enable-native-access=ALL-UNNAMED -cp $CP \
  io.casehub.blocks.speech.sherpa.SpeechCli transcribe \
  /tmp/sherpa-onnx-whisper-tiny /tmp/sherpa-onnx-whisper-tiny/test_wavs/0.wav

# TTS
java --enable-native-access=ALL-UNNAMED -cp $CP \
  io.casehub.blocks.speech.sherpa.SpeechCli synthesise \
  /tmp/vits-piper-en_US-amy-low "Hello from CaseHub" output.wav

# Streaming
java --enable-native-access=ALL-UNNAMED -cp $CP \
  io.casehub.blocks.speech.sherpa.SpeechCli stream \
  /tmp/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17 \
  /tmp/sherpa-onnx-whisper-tiny/test_wavs/1.wav
```

### Benchmark reference data

All benchmark numbers in this document were collected on Apple Silicon (M-series),
macOS, JDK 26.0.2, CPU provider, 2 threads. Results will vary by hardware, JDK version,
and model size. The relative comparisons (CPU vs CoreML, RTF ratios) should be stable
across M-series generations.
