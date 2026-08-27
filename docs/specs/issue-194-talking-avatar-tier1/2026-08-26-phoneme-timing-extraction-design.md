# Design: Phoneme Timing Extraction from VITS TTS

**Issue:** casehubio/blocks#186 (epic: #194)
**Date:** 2026-08-26
**Branch:** issue-194-talking-avatar-tier1

## Problem

`SynthesisResult.phonemes()` returns an empty list. Avatar lip-sync (#154)
needs per-phoneme start/end millisecond offsets from VITS synthesis. The
sherpa-onnx C API does not expose phoneme timing — `SherpaOnnxGeneratedAudio`
contains only `samples`, `n`, and `sample_rate`. Issue
[k2-fsa/sherpa-onnx#3705](https://github.com/k2-fsa/sherpa-onnx/issues/3705)
requests this capability but has no implementation.

## Key Insight

The VITS duration predictor runs during every synthesis. Its output
(`/Ceil_output_0` — per-phoneme frame counts after ceiling) is consumed
internally and discarded. By modifying the ONNX model to expose this tensor
as a second output, we get exact phoneme timing at zero additional CPU cost.

**Verified empirically:** modified `en_US-lessac-medium.onnx` to add
`/Ceil_output_0` as a second output. Ran inference via onnxruntime Python.
Duration sum from frames (220.6 ms) matches audio duration (220.6 ms) exactly.

## Architecture

Bypass sherpa-onnx TTS entirely. Call onnxruntime and espeak-ng directly
via Java FFM.

```
Text
 │
 ├─► EspeakLibrary (FFM) ──► IPA phonemes ("həlˈoʊ")
 │                                │
 │                     phoneme_id_map lookup
 │                                │
 │                          phoneme IDs [1, 20, 59, 24, 120, 2]
 │                                │
 └─► OnnxRuntimeLibrary (FFM) ──► modified VITS ONNX model
                                  │
                          ┌───────┴───────┐
                     output[0]       output[1]
                    audio samples   duration frames
                          │               │
                     WavWriter.encode  frames → ms
                          │               │
                     byte[] audioData  List<PhonemeTiming>
                          │               │
                          └───────┬───────┘
                          SynthesisResult
```

### New Classes (speech-sherpa module)

| Class | Responsibility |
|-------|---------------|
| `OnnxRuntimeLibrary` | FFM bindings for onnxruntime C API via vtable pattern. Loads `libonnxruntime` independently (same tiered lookup as `SherpaLibrary`: system path → local cache → provisioned dir). Resolves `OrtGetApiBase()`, reads `OrtApi` function pointers. ~12 downcall handles: `CreateEnv`, `CreateSessionOptions`, `CreateSession`, `CreateCpuMemoryInfo`, `CreateTensorWithDataAsOrtValue`, `Run`, `GetTensorMutableData`, `SessionGetOutputCount`, `SessionGetOutputName`, plus release functions. Singleton, thread-safe. |
| `EspeakLibrary` | FFM bindings for espeak-ng C API. 4 downcall handles: `espeak_ng_Initialize`, `espeak_ng_SetVoiceByName`, `espeak_TextToPhonemes`, `espeak_ng_Terminate`. All methods `synchronized` — espeak-ng uses global state and is not thread-safe. Singleton. |
| `VitsTextToSpeech` | `TextToSpeechService` implementation. Owns ORT session lifecycle (created once at construction, reused). Phonemization via `EspeakLibrary`. Inference via `OnnxRuntimeLibrary`. Produces `SynthesisResult` with audio + phoneme timing. |
| `VitsConfig` | Record: `modelDir`, `numThreads`, `provider`. Factory `fromModelDir(Path)` parses the `.onnx.json` config to extract `phoneme_id_map`, `espeak.voice`, `audio.sample_rate`, and inference defaults. |
| `ModelPatcher` | Static utility: `patch(Path modelPath) → boolean`. Uses `protobuf-java` to parse the ONNX model (protobuf format), append a `ValueInfoProto` for `/Ceil_output_0` to `graph.output`, and serialize back. Pure Java, no Python. Idempotent — checks if output already exists. |

### Unchanged Classes

`SherpaOnnxTextToSpeech` — remains as a lightweight TTS implementation for
consumers who have sherpa-onnx but not espeak-ng provisioned. `VitsTextToSpeech`
requires espeak-ng as an additional native dependency (~2MB); consumers who
only need STT + basic TTS without phoneme timing can avoid this by using
`SherpaOnnxTextToSpeech`. Both implement `TextToSpeechService`.

`TextToSpeechService`, `SynthesisResult`, `PhonemeTiming`, `SynthesisOptions`
— all unchanged. The existing API already accommodates phoneme timing.

### `includePhonemes` Behaviour

Phonemization always happens — it's required as MODEL INPUT (VITS takes
phoneme IDs, not text). The `SynthesisOptions.includePhonemes()` flag controls
only the OUTPUT:

- `includePhonemes=false` (default): skip reading `output[1]`, skip reverse
  ID-to-phoneme mapping. Return empty `phonemes()` list.
- `includePhonemes=true`: read duration output, reverse-map IDs to phoneme
  strings, compute cumulative timing. Return populated `phonemes()` list.

## Data Flow

### Provisioning (one-time)

1. `Provisioner.ensureEspeak()` downloads `libespeak-ng` from
   [espeakng-loader releases](https://github.com/thewh1teagle/espeakng-loader/releases).

   | Platform | Library filename | Archive pattern |
   |----------|-----------------|-----------------|
   | osx-arm64 | `libespeak-ng.dylib` | `espeak-ng-libs-macos-arm64.tar.gz` |
   | osx-x64 | `libespeak-ng.dylib` | `espeak-ng-libs-macos-x86_64.tar.gz` |
   | linux-x64 | `libespeak-ng.so` | `espeak-ng-libs-linux-x86_64.tar.gz` |
   | linux-arm64 | `libespeak-ng.so` | `espeak-ng-libs-linux-aarch64.tar.gz` |

   SHA-256 verified per platform. Stored at
   `~/.casehub/native/espeak-ng/<version>/<platform>/`.
   Version pinned to match espeak-ng-data bundled in Piper models.

2. `Provisioner.ensureTtsModel(modelName)` downloads a Piper VITS model from
   sherpa-onnx GitHub releases (`tts-models/` tag). SHA-256 verified. Stored at
   `~/.casehub/models/sherpa-onnx/<modelName>/`.

3. `ModelPatcher.patch(modelDir)` uses protobuf-java to parse the ONNX model,
   append `/Ceil_output_0` to `graph.output`, and write back. Pure Java,
   idempotent. Called by `Provisioner.ensureTtsModel()` after extraction.

### Synthesis (per call)

1. **Phonemize:** `EspeakLibrary.textToPhonemes(text, voice)` returns IPA
   string (e.g., `"həlˈoʊ wˈɜːld"`).

2. **Tokenize:** Split IPA string into phoneme tokens using greedy longest-match
   against `phoneme_id_map` keys (sorted by key length descending). Handles
   multi-character phonemes (`oʊ`, `tʃ`). Unknown IPA characters (not in map)
   are skipped with a warning log. Multi-ID map entries (e.g., diphthong →
   `[24, 25]`) are expanded in sequence in the input tensor. BOS/EOS/space
   token IDs are looked up from `phoneme_id_map` (keys `^`, `$`, ` `), not
   hardcoded.

3. **Infer:** Call ORT session with inputs:
   - `input`: int64 phoneme ID tensor [1, N]
   - `input_lengths`: int64 [1] = N
   - `scales`: float32 [3] = [noise_scale, length_scale, noise_scale_w]

   Read outputs:
   - `output[0]`: float32 audio samples [1, 1, 1, T]
   - `output[1]`: float32 duration frames [1, 1, N] (only if model is patched)

4. **Convert durations:** For each phoneme i:
   - `durationMs = frames[i] * 256 / sampleRate * 1000`
   - `startMs = sum of previous durations`
   - `endMs = startMs + durationMs`

5. **Build result:** Encode audio via `WavWriter.encode()`. Map phoneme IDs
   back to phoneme strings via reverse `phoneme_id_map`. Construct
   `List<PhonemeTiming>` and return `SynthesisResult`.

## Constraints

### hop_length = 256

Standard for Piper VITS models. The frame-to-millisecond conversion is:
`ms = frames * 256 / sampleRate * 1000`. This is hardcoded. The Piper model
config JSON does not include hop_length — it's baked into the model
architecture. If a future model uses a different hop length, this needs
parameterization.

### Piper VITS Only

The duration predictor output node `/Ceil_output_0` is specific to Piper's
`export_onnx.py` output. Other VITS variants (Coqui, HuggingFace, NeMo) may
use different node names. Extending to other variants requires model graph
inspection per variant.

### espeak-ng Thread Safety

espeak-ng uses global state internally. All `EspeakLibrary` methods are
`synchronized`. This serializes phonemization across threads. For the avatar
use case (single-user TTS), this is not a bottleneck.

### espeak-ng Data Compatibility

The provisioned `libespeak-ng` version must be compatible with the
`espeak-ng-data` directory bundled in the Piper model. espeak-ng data format
is generally backward-compatible. Pin espeak-ng library version to match the
version sherpa-onnx was built against (currently embedded in sherpa-onnx
1.13.6).

### Graceful Degradation

`VitsTextToSpeech` queries `SessionGetOutputCount` at construction time.
If the model has only 1 output (unpatched), it sets an internal
`phonemeTimingAvailable` flag to false. On synthesis with
`includePhonemes=true`, it requests only `output[0]` and returns an
empty phoneme list — no ORT error.

| Missing component | Behaviour |
|-------------------|-----------|
| Model not patched (1 output) | Audio works. `phonemes()` returns empty list. Logged once at construction. |
| espeak-ng lib not provisioned | `VitsTextToSpeech` constructor throws `SherpaException`. Use `SherpaOnnxTextToSpeech` instead. |

### ORT Session Lifecycle

`VitsTextToSpeech` implements `AutoCloseable`. The ORT session, env, and
memory info are created at construction and released in `close()`.
`OnnxRuntimeLibrary` is a singleton — the env outlives individual sessions.
ORT sessions are thread-safe for concurrent inference when each call uses
its own input/output buffers (`Arena.ofConfined()` per call ensures this).

### Duration Precision

All frame-to-millisecond computation uses `double` arithmetic. Rounding to
`long` (for `PhonemeTiming.startMs/endMs`) happens only at the final
construction step. Cumulative boundaries are computed in `double` to prevent
drift over many phonemes.

## Testing

| Test | Type | Native deps | What it verifies |
|------|------|-------------|-----------------|
| `PhonemeIdMapTest` | Unit | None | Config JSON parsing, IPA → ID mapping, ID → phoneme reverse mapping, BOS/EOS wrapping |
| `ModelPatcherTest` | Unit | None (protobuf-java) | Idempotent model modification. Output count before/after. Verifies `/Ceil_output_0` added exactly once. Uses a small synthetic ONNX model. |
| `OnnxRuntimeLibraryTest` | Integration | onnxruntime | Session lifecycle: create env → create session → run → read output → cleanup. Uses a minimal test ONNX model |
| `EspeakLibraryTest` | Integration | espeak-ng | Phonemization of known English text produces expected IPA. `@DisabledIf` espeak-ng lib unavailable |
| `VitsTextToSpeechTest` | Integration | onnxruntime + espeak-ng + VITS model | End-to-end: text → `SynthesisResult` with non-empty phonemes. Duration sum equals audio length (±1ms rounding). `@DisabledIf` any native dep unavailable |
| `VitsTextToSpeechNoPatchTest` | Integration | onnxruntime + espeak-ng | Graceful degradation: unpatched model produces audio with empty phoneme list |

## References

- [k2-fsa/sherpa-onnx#3705](https://github.com/k2-fsa/sherpa-onnx/issues/3705) — open feature request for word-level timestamps in TTS
- [Piper export_onnx.py](https://github.com/rhasspy/piper/blob/master/src/python/piper_train/export_onnx.py) — VITS ONNX export (single output, duration discarded)
- [espeakng-loader](https://github.com/thewh1teagle/espeakng-loader/releases) — pre-built espeak-ng shared libraries
- [onnxruntime C API](https://github.com/microsoft/onnxruntime/blob/main/include/onnxruntime/core/session/onnxruntime_c_api.h) — vtable pattern via `OrtGetApiBase()`
- `SherpaOnnxTextToSpeech.java` — existing sherpa-onnx TTS implementation
- `SherpaLibrary.java` — existing native library loading (onnxruntime already loaded here)
- `Provisioner.java` — existing provisioning infrastructure
- `PhonemeTiming.java`, `SynthesisResult.java`, `SynthesisOptions.java` — existing API types
- Empirical model inspection: `en_US-lessac-medium.onnx` — VITS duration predictor chain: `/dp/Split_output_0` → `/Exp` → `/Mul` (x_mask) → `/Mul_1` (length_scale) → `/Ceil` = final per-phoneme frame counts
