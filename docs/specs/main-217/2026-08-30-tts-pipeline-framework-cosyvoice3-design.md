# TTS Pipeline Framework + CosyVoice3 Integration

**Issue:** casehubio/blocks#217
**Date:** 2026-08-30
**Status:** Draft

## Summary

Build a manifest-driven TTS pipeline framework in `speech-sherpa` that manages ONNX session lifecycle, model provisioning, voice cloning, and execution provider configuration. Implement CosyVoice3 as the first pipeline configuration — a 14-model, 4-stage synthesis engine (LLM + flow diffusion + HiFT vocoder) with zero-shot voice cloning. Audio8 retrofit is a follow-up that validates the abstraction generalises.

Note: Issue #217 title ("CosyVoice2 TTS integration") should be updated to "CosyVoice3 TTS pipeline framework" — see D1 for the CosyVoice2→CosyVoice3 pivot.

Four deliverables:
1. **Pipeline framework** — `TtsPipeline`, `TtsPipelineManifest` (sealed hierarchy), stage SPIs (`TtsTokenizer`, `TtsGenerator`, `TtsDecoder`, `TtsVoiceEncoder`), `VoiceData` sealed hierarchy, generalised `VoiceRegistry`
2. **DSP primitives** — FFT, `MelSpectrogram` (three configurations), STFT/ISTFT (for HiFT), audio resampling (sinc interpolation for 48→16kHz and 48→24kHz). These are new implementations — no FFT or signal processing primitives exist in speech-sherpa today. Each requires verification against a reference implementation (librosa/scipy).
3. **CosyVoice3 implementation** — 4 stage implementations covering 14 ONNX models: prompt processing (campplus + speech tokenizer), LLM autoregressive generation (KV-cache transformer), flow decoder (10-step Euler ODE), HiFT vocoder (f0 + source + STFT/ISTFT). Includes Qwen2 BPE tokenizer (new implementation — structurally different from the existing `SentencePieceTokenizer`).
4. **Provisioner extensions** — HuggingFace download for CosyVoice3 (~3.8 GB), following the existing `provisionFromHuggingFace()` pattern used by Audio8

## Background

### Why a pipeline framework

Audio8 (#213) validated autoregressive ONNX TTS but its implementation is monolithic — `Audio8TextToSpeech` wires tokenizer, DualAR loop, codec decoder, and voice registry in a single factory method. CosyVoice3 has a fundamentally different architecture (LLM + flow diffusion + HiFT vocoder instead of DualAR + codec decoder) with 14 ONNX files across 4 stages. Without a framework, each new TTS model duplicates session lifecycle management, provisioning, voice registry, and execution provider configuration.

The framework's primary value is **lifecycle management** — session creation/configuration/closure, model provisioning, manifest-driven wiring — not making stage logic generic. Stage implementations remain model-specific; the framework standardises the infrastructure around them.

### Why CosyVoice3, not CosyVoice2

CosyVoice2's ONNX export (Lourdle/CosyVoice2-0.5B_ONNX) is incomplete — only flow + HiFT, missing the LLM backbone. Without the LLM, there's no text-to-speech capability.

CosyVoice3 (ayousanz/cosy-voice3-onnx) has a complete 14-file pipeline with a reference Python implementation (`onnx_inference_pure.py`). Apache 2.0. 3.8 GB total. Supports 9+ languages with auto-detection.

### Pipeline scope

The framework targets direct-ORT multi-stage models only. SherpaLibrary-based models (Kokoro, SherpaOnnx) continue using their existing integration paths — they delegate session management to sherpa-onnx's C API internally. VitsTextToSpeech is excluded because it's a hybrid model: espeak phonemization (non-ONNX) + single ORT session. It's multi-stage in the sense of phonemization → inference → timing extraction, but the phonemization stage uses a native library (EspeakLibrary), not an ONNX model. The pipeline framework manages ORT session lifecycle — stages with non-ORT dependencies are outside its scope.

## Architecture

### Pipeline Framework

```
TtsPipeline implements TextToSpeechService, AutoCloseable
├── TtsPipelineManifest (sealed hierarchy)
│   ├── PipelineHeader          — common: stages, ONNX files, sample rate, provider prefs
│   ├── CosyVoice3Manifest      — CosyVoice3 hyperparams
│   └── Audio8Manifest          — (future) Audio8 hyperparams from RuntimeManifest
├── TtsTokenizer (SPI)          — String → int[]
├── TtsGenerator (SPI)          — tokens + VoiceData → generated tokens/features
├── TtsDecoder (SPI)            — generated tokens/features → float[] PCM
├── TtsVoiceEncoder (SPI)       — byte[] audio → VoiceData
├── VoiceRegistry               — generalised: register/release/get with VoiceData
└── OnnxRuntimeLibrary.Session[] — managed lifecycle
```

### Stage SPIs

Each SPI is a typed contract between the framework and model-specific logic. The framework creates ONNX sessions from the manifest and passes them to stage constructors. Stages own inference logic; the framework owns lifecycle.

```java
@FunctionalInterface
interface TtsTokenizer {
    int[] encode(String text);
}

interface TtsGenerator {
    GeneratorOutput generate(int[] tokens, @Nullable VoiceData voiceData,
                             GeneratorConfig config);
}

record GeneratorConfig(float temperature, int topK, float topP,
                       int maxTokens, int minTokens) {
    static GeneratorConfig defaults() {
        return new GeneratorConfig(1.0f, 25, 0.9f, 500, 10);
    }
}

sealed interface GeneratorOutput
        permits SpeechTokenOutput, CodecFrameOutput {
    record SpeechTokenOutput(int[] speechTokens) implements GeneratorOutput {}
    record CodecFrameOutput(int[][] codecFrames) implements GeneratorOutput {}
}

interface TtsDecoder {
    float[] decode(GeneratorOutput generatorOutput,
                   @Nullable VoiceData voiceData);
}

@FunctionalInterface
interface TtsVoiceEncoder {
    VoiceData encode(byte[] audioData, @Nullable String transcriptText);
}
```

`GeneratorConfig` carries sampling parameters common to autoregressive generators. Default values match CosyVoice3's reference implementation (top-k=25). The pipeline reads overrides from the manifest.

The `TtsVoiceEncoder` takes an optional transcript because CosyVoice3's voice cloning requires the prompt audio transcript (for text embedding), while Audio8's codec encoder does not.

`GeneratorOutput` is a sealed hierarchy — CosyVoice3's generator produces `SpeechTokenOutput` (int[] of speech tokens); Audio8's produces `CodecFrameOutput` (int[][] of codec frames). The decoder uses exhaustive `switch` to consume its model's output, consistent with the sealed hierarchy philosophy from D8.

### TtsPipelineManifest (sealed hierarchy)

```java
sealed interface TtsPipelineManifest permits CosyVoice3Manifest, Audio8Manifest {
    PipelineHeader header();
}

record PipelineHeader(
    String name,
    int sampleRate,
    Map<String, List<String>> stageModels,   // stage name → ONNX filenames
    @Nullable ExecutionProviderConfig provider,
    Map<String, String> metadata
) {}

record ExecutionProviderConfig(
    String preferred,                         // "cuda", "coreml", "cpu"
    @Nullable Integer deviceId,
    Map<String, String> stageOverrides        // stage name → provider
) {}

record CosyVoice3Manifest(
    PipelineHeader header,
    int hiddenDim,           // 896
    int speechVocabSize,     // 6561
    int sosId,               // 6561
    int eosId,               // 6562
    int taskId,              // 6563
    int numLlmLayers,        // 24
    int kvHeadDim,           // 64
    int tokenMelRatio,       // 2
    int flowSteps,           // 10
    int melBins,             // 80
    int hiftNFft,            // 16
    int hiftHopLength,       // 4
    int speakerEmbedDim,     // 192
    String tokenizerDir,     // relative path to Qwen2 tokenizer files
    Map<String, String> defaultPrompts  // WAV filename → transcript text
) implements TtsPipelineManifest {}
```

The manifest JSON lives in the model directory alongside the ONNX files. The framework parses it and constructs the pipeline.

### VoiceData (sealed hierarchy)

```java
sealed interface VoiceData permits CodecVoiceData, EmbeddingVoiceData {
    record CodecVoiceData(int[] codecTokens) implements VoiceData {}
    record EmbeddingVoiceData(
        float[] speakerEmbedding,
        int[] speechTokens,
        float[] promptMel,         // 24kHz mel for flow conditioning
        String promptText          // transcript of reference audio for text embedding
    ) implements VoiceData {}
}
```

Audio8's `VoiceEncoder` produces `CodecVoiceData`; CosyVoice3's produces `EmbeddingVoiceData`. Generator stages use exhaustive `switch` to consume model-specific data.

`EmbeddingVoiceData` includes:
- **promptMel**: precomputed mel spectrogram for flow decoder conditioning — avoids re-extraction per synthesis call
- **promptText**: the transcript of the reference audio, needed by the generator for input sequence construction (`text_emb(prompt_text + tts_text)`). Stored as raw text rather than pre-tokenized tokens so voice data is tokenizer-version-independent.

### VoiceRegistry generalisation

VoiceRegistry currently stores `Map<String, int[]>` with a `VoiceEncoder` that returns `int[]`. Generalise to `Map<String, VoiceData>` with a `TtsVoiceEncoder` that returns `VoiceData`.

```java
public final class VoiceRegistry implements AutoCloseable {
    private final TtsVoiceEncoder encoder;
    private final Map<String, VoiceData> cache = new ConcurrentHashMap<>();
    private final @Nullable VoiceData defaultVoice;

    public String register(Path referenceAudio) { ... }
    public String register(Path referenceAudio, String transcript) { ... }
    public VoiceData get(String voiceId) { ... }
    public void release(String voiceId) { ... }
    public Set<String> registeredVoices() { ... }
}
```

The `register(Path, String)` overload supports CosyVoice3's requirement for a prompt transcript. Audio8 callers use the single-arg overload (transcript ignored by codec encoder).

**Audio8 caller migration (part of this deliverable):** The VoiceRegistry API change from `int[] getVoiceCodes(String)` to `VoiceData get(String)` requires a mechanical update to `Audio8TextToSpeech.synthesise()`:
```java
// Before: int[] voiceCodes = voiceRegistry.getVoiceCodes(options.voice());
// After:  CodecVoiceData vd = (CodecVoiceData) voiceRegistry.get(options.voice());
//         int[] voiceCodes = vd.codecTokens();
```
This is NOT the full Audio8 pipeline retrofit (D3) — it's a one-line API migration scoped to `Audio8TextToSpeech` and its tests. The Audio8 VoiceEncoder lambda in `fromModelDir()` wraps its `int[]` result in `CodecVoiceData`. Existing Audio8 tests are updated to use the new API — the behavioral assertions remain identical.

### TtsPipeline

```java
public final class TtsPipeline implements TextToSpeechService, AutoCloseable {
    private final TtsPipelineManifest manifest;
    private final TtsTokenizer tokenizer;
    private final TtsGenerator generator;
    private final TtsDecoder decoder;
    private final VoiceRegistry voiceRegistry;
    private final int sampleRate;
    private final OnnxRuntimeLibrary.Session[] sessions;  // managed lifecycle

    public static TtsPipeline fromModelDir(Path modelDir) { ... }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        int[] tokens = tokenizer.encode(text);
        VoiceData voice = resolveVoice(options);
        GeneratorOutput output = generator.generate(tokens, voice,
                                                     generatorConfig());
        float[] samples = decoder.decode(output, voice);
        byte[] wav = WavWriter.encode(samples, sampleRate, 1);
        return new SynthesisResult(wav, "wav", List.of());
    }

    // Voice cloning delegation
    public String registerVoice(Path referenceAudio) { ... }
    public String registerVoice(Path referenceAudio, String transcript) { ... }
    public void releaseVoice(String voiceId) { ... }

    // Memory metadata (D12 trade-off: deployment-level memory awareness)
    public int sessionCount() { ... }
    public String modelName() { ... }

    @Override
    public void close() { /* close all sessions */ }
}
```

The `fromModelDir` factory reads the manifest, detects the model type from the sealed hierarchy, instantiates the appropriate stage implementations, and creates all ONNX sessions. All-or-nothing: if any ONNX file fails to load, the entire pipeline fails cleanly.

Sessions are created once and held until `close()`. Mutable inference state (KV caches, diffusion intermediaries) is allocated per-synthesis-call using confined arenas.

**Voice resolution:** `resolveVoice(options)` uses `SynthesisOptions.voice()` as the VoiceRegistry ID (the UUID returned by `registerVoice()`). When `voice` is null or empty, the pipeline uses the default voice. CosyVoice3 always requires voice conditioning — the default voice is registered during pipeline construction using the manifest's `defaultPrompts` map:

1. `fromModelDir()` reads `CosyVoice3Manifest.defaultPrompts()` — a map from WAV filename to transcript text (e.g., `{"en_female_nova_greeting.wav": "Hello, how can I help you today?"}`)
2. The first entry is registered via `voiceRegistry.register(promptsDir.resolve(filename), transcript)`, running the full CosyVoice3VoiceEncoder pipeline (campplus + speech tokenizer + mel extraction)
3. The resulting `VoiceData` is stored as `defaultVoice` in the VoiceRegistry

This matches Audio8's pattern where `defaultVoiceCodes` are loaded from `reference_codes.npy`, but uses the live encoder pipeline instead of pre-computed data — CosyVoice3's voice data includes the prompt mel and transcript which cannot be meaningfully pre-serialized.

**Per-call memory profile (CosyVoice3):**
- KV-cache: `[48, 1, 2, seq_len, 64]` growing per decode step. At max length (1000 steps for 50-token input): 48 × 2 × 1000 × 64 × 4 bytes ≈ 24 MB
- Flow decoder: Gaussian noise + conditioning + velocity tensors across 10 Euler steps. For 2000 mel frames: ~5 MB transient
- HiFT vocoder: source waveform + STFT intermediates: ~2 MB
- Total per-call: ~30–50 MB transient (allocated in confined arena, freed on call completion)
- Model weights (shared across calls): ~3.8 GB

Concurrent synthesis is safe — each call uses its own confined arena. Memory scales linearly with concurrent call count. The framework exposes `sessionCount()` and `modelName()` for deployment-level memory awareness (D12), but does not impose concurrency limits — that's an application-layer concern.

## CosyVoice3 Implementation

### Stage 1: CosyVoice3Tokenizer

Implements `TtsTokenizer`. Implements a Qwen2-compatible byte-level BPE tokenizer from `vocab.json` + `merges.txt` (same BPE algorithm as GPT-2 but different vocabulary and pre-tokenization patterns). This is a non-trivial new tokenizer implementation — structurally different from the existing `SentencePieceTokenizer` (Viterbi unigram) and `Audio8Tokenizer` (HuggingFace fast tokenizer JSON format).

Implementation requires: byte-level BPE with byte fallback, merge priority resolution, pre-tokenization regex (Qwen2 uses a GPT-2-style pattern), special token handling, and correct Unicode/CJK handling for CosyVoice3's 9+ language support. Must be verified against the Python `transformers` Qwen2 tokenizer on multilingual test cases.

No language tags — CosyVoice3 auto-detects language from text.

```java
final class CosyVoice3Tokenizer implements TtsTokenizer {
    static CosyVoice3Tokenizer load(Path tokenizerDir);  // reads vocab.json + merges.txt
    int[] encode(String text);                            // no special tokens
    String decode(int[] tokens);
}
```

### Stage 2: CosyVoice3Generator

Implements `TtsGenerator`. Manages the full LLM pipeline: text embedding → initial backbone pass → autoregressive decode loop → speech tokens.

**ONNX sessions used:** `text_embedding_fp32.onnx`, `llm_speech_embedding_fp16.onnx`, `llm_backbone_initial_fp16.onnx`, `llm_backbone_decode_fp16.onnx`, `llm_decoder_fp16.onnx`

**Input sequence construction:**
```
[SOS_emb, text_emb(prompt_text + tts_text), TASK_ID_emb, prompt_speech_emb]
```
- SOS (6561) and TASK_ID (6563) embedded via `llm_speech_embedding`
- `text_embedding` processes concatenated `EmbeddingVoiceData.promptText()` + TTS text, tokenized together via `CosyVoice3Tokenizer`
- Prompt speech tokens (from `EmbeddingVoiceData.speechTokens()`) embedded via `llm_speech_embedding`

**KV-cache management:**
- Initial pass: `llm_backbone_initial` processes the full input sequence → hidden states `[1, seq_len, 896]` + KV-cache `[48, 1, 2, seq_len, 64]` (24 layers × 2 key/value)
- Decode loop: `llm_backbone_decode` processes single-token embeddings, updating KV-cache (grows by 1 along sequence axis each step)
- KV-cache allocated per-call via `Arena.ofConfined()` — safe for concurrent use
- Attention mask grows each step: `[1, initial_len + steps_so_far]`

**Token sampling:** Top-k (k=25) — compute log-softmax, take top-25, softmax, categorical sample. Before sampling, check logits for NaN/Inf — fail fast with `SherpaException` rather than propagating garbage tokens through the pipeline. NaN in logits indicates a model-level failure (FP16 overflow, corrupted weights) that cannot be recovered from.

**Termination:** EOS token (6562) after minimum length `max(10, text_token_count * 2)`, or maximum length `min(500, text_token_count * 20)`. If `maxTokens` is reached without EOS, the output is still usable — the model has generated a truncated but valid speech token sequence.

**Output:** `GeneratorOutput` wrapping `int[]` of generated speech tokens.

```java
final class CosyVoice3Generator implements TtsGenerator {
    CosyVoice3Generator(OnnxRuntimeLibrary.Session textEmbedding,
                        OnnxRuntimeLibrary.Session speechEmbedding,
                        OnnxRuntimeLibrary.Session llmInitial,
                        OnnxRuntimeLibrary.Session llmDecode,
                        OnnxRuntimeLibrary.Session llmDecoder,
                        CosyVoice3Manifest manifest,
                        CosyVoice3Tokenizer tokenizer);
    GeneratorOutput generate(int[] tokens, VoiceData voiceData,
                             GeneratorConfig config);
}
```

The generator holds a tokenizer reference because CosyVoice3's text embedding requires `prompt_text + tts_text` tokenized together. The `int[] tokens` parameter from the pipeline carries TTS-only tokens. The generator internally tokenizes `EmbeddingVoiceData.promptText()`, prepends the result to `tokens`, and passes the concatenated sequence to the `text_embedding` model. GPT-2-style BPE pre-tokenizes on whitespace boundaries, so concatenating separately-tokenized segments is equivalent to tokenizing the full concatenated string — no cross-boundary merge issues. The `fromModelDir()` factory creates both the tokenizer and generator, passing the former to the latter during construction.

### Stage 3: CosyVoice3Decoder

Implements `TtsDecoder`. Runs the flow decoder (10-step Euler ODE) followed by HiFT vocoder.

**ONNX sessions used:** `flow_token_embedding_fp16.onnx`, `flow_pre_lookahead_fp16.onnx`, `flow_speaker_projection_fp16.onnx`, `flow.decoder.estimator.fp16.onnx`, `hift_f0_predictor_fp32.onnx`, `hift_source_generator_fp32.onnx`, `hift_decoder_fp32.onnx`

**Flow decoder pipeline:**
1. Concatenate prompt speech tokens + generated tokens → full token sequence
2. `flow_token_embedding` → token embeddings
3. `flow_pre_lookahead` → `h` with repeat_interleave ×2 (token_mel_ratio), shape `[1, total_tokens*2, dim]`
4. Transpose to `mu = [1, dim, mel_len]`
5. L2-normalize speaker embedding → `flow_speaker_projection` → `spks`
6. Build conditioning tensor `conds [1, 80, mel_len]`: initialise as zeros `[1, 80, mel_len]` where `mel_len = total_tokens * token_mel_ratio`. Copy prompt mel (from `EmbeddingVoiceData.promptMel()`) into the first `prompt_tokens * 2` frames using truncation or zero-padding to align:
      - If stored mel has **more** frames than `prompt_tokens * 2` → truncate to `prompt_tokens * 2`
      - If stored mel has **fewer** frames → copy all available, remaining frames stay zero
      - The generated portion (after prompt) remains zero in `conds` — the flow decoder fills it via the diffusion process
      - This alignment is necessary because the speech tokenizer (16kHz, model-determined token count) and mel extraction (24kHz, hop=256) produce counts from different sample rates and parameters
7. **10-step Euler ODE:** initialise `x` from Gaussian noise `[1, 80, mel_len]` using `ThreadLocalRandom` (thread-safe for concurrent synthesis). For deterministic testing, `CosyVoice3Decoder` accepts an optional `Random` seed via constructor — seeded RNG produces reproducible output for unit tests. Batch to size 2, iterate:
   - `t = [i/10, i/10]`
   - `velocity = flow_estimator(x, mask, mu, t, spks, cond)`
   - `x = x + velocity * (1/10)`
8. Slice off prompt portion: `mel = x[:1, :, prompt_mel_frames:]`

**HiFT vocoder pipeline:**
1. `hift_f0_predictor`: mel `[1, 80, mel_len]` → F0 contour `[1, mel_len]`
2. `hift_source_generator`: F0 `[1, 1, mel_len]` → source waveform
3. STFT (Java implementation): source → `[1, 18, stft_frames]` (n_fft=16, hop_length=4, Hann window)
4. `hift_decoder`: mel + source_stft → magnitude `[1, 9, time]` + phase `[1, 9, time]`
5. ISTFT (Java implementation): magnitude + phase → audio, clamped to [-0.99, 0.99]
6. Upsample factor: [8, 5, 3] = 120×, so stft_frames ≈ mel_frames × 120

```java
final class CosyVoice3Decoder implements TtsDecoder {
    CosyVoice3Decoder(OnnxRuntimeLibrary.Session flowTokenEmbedding,
                      OnnxRuntimeLibrary.Session flowPreLookahead,
                      OnnxRuntimeLibrary.Session flowSpeakerProjection,
                      OnnxRuntimeLibrary.Session flowEstimator,
                      OnnxRuntimeLibrary.Session hiftF0,
                      OnnxRuntimeLibrary.Session hiftSource,
                      OnnxRuntimeLibrary.Session hiftDecoder,
                      CosyVoice3Manifest manifest);
    float[] decode(GeneratorOutput output, VoiceData voiceData);
}
```

### Stage 4: CosyVoice3VoiceEncoder

Implements `TtsVoiceEncoder`. Extracts speaker embedding and speech tokens from reference audio, plus precomputes prompt mel for flow conditioning.

**ONNX sessions used:** `campplus.onnx`, `speech_tokenizer_v3.onnx`

**Processing pipeline:**
1. Load reference audio, resample to 16kHz (for campplus and speech tokenizer) and 24kHz (for mel extraction)
2. **Campplus:** compute 80-band mel (n_fft=400, hop=160, fmin=20, fmax=7600, at 16kHz) → log → mean-normalise → `campplus` model → speaker embedding `[1, 192]`
3. **Speech tokenizer:** compute 128-band mel Whisper-style (n_fft=400, hop=160, fmin=0, fmax=8000, at 16kHz) → log10 → clamp → normalise → `speech_tokenizer_v3` model → speech tokens `[1, N]`
4. **Prompt mel:** compute 80-band mel (n_fft=1024, hop=256, fmin=0, fmax=12000, at 24kHz) → log → store for flow conditioning
5. Return `EmbeddingVoiceData(speakerEmbedding, speechTokens, promptMel, transcriptText)`

```java
final class CosyVoice3VoiceEncoder implements TtsVoiceEncoder {
    CosyVoice3VoiceEncoder(OnnxRuntimeLibrary.Session campplus,
                           OnnxRuntimeLibrary.Session speechTokenizer,
                           CosyVoice3Manifest manifest);
    VoiceData encode(byte[] audioData, String transcriptText);
}
```

**Null transcript handling:** `CosyVoice3VoiceEncoder.encode()` throws `IllegalArgumentException` if `transcriptText` is null or blank. CosyVoice3 requires a prompt transcript for the text embedding in the generator — registration without a transcript produces voice data that would silently generate garbage audio. Fail-fast at registration time, not at synthesis time. Callers using `VoiceRegistry.register(Path)` (no transcript) on a CosyVoice3 pipeline hit this validation immediately. Audio8's codec encoder ignores the transcript parameter, so the single-arg overload works correctly for Audio8.

The campplus and speech tokenizer sessions are held persistently (unlike Audio8's transient codec encoder). CosyVoice3's models are smaller (~1 GB combined) and voice cloning requires both models for every registration — transient loading would be wasteful.

### Audio preprocessing utilities

CosyVoice3 needs mel spectrogram extraction with multiple configurations (campplus mel vs Whisper mel vs flow mel). Add a reusable `MelSpectrogram` utility:

```java
record MelConfig(int sampleRate, int nFft, int hopLength, int nMels,
                 float fMin, float fMax) {}

final class MelSpectrogram {
    static float[][] compute(float[] audio, MelConfig config);
    static float[][] logMel(float[][] mel);
    static float[][] whisperNormalize(float[][] logMel);
    static void meanNormalize(float[][] mel);
}
```

This avoids duplicating mel extraction logic across the voice encoder, flow decoder conditioning, and potential future models. Implemented in pure Java with a new FFT implementation (Cooley-Tukey radix-2 for power-of-two sizes). The FFT is the foundational primitive — mel spectrograms require STFT (windowed FFT), which in turn requires Hann window generation, zero-padding, and proper frame assembly. Mel filter banks require frequency-to-mel conversion and triangular filter construction. All DSP implementations must be verified against librosa/scipy reference outputs with tight numerical tolerance.

### Audio resampling

CosyVoice3's voice encoder requires 16kHz (for campplus and speech tokenizer) and 24kHz (for mel extraction) from input audio that may be 44.1kHz or 48kHz. Resampling uses windowed sinc interpolation with anti-aliasing lowpass filter. For integer-ratio cases (48→16 = ÷3, 48→24 = ÷2), polyphase decimation is efficient. For non-integer ratios (44.1→16), rational approximation with interpolation + decimation. Verified against scipy.signal.resample_poly reference.

### STFT/ISTFT for HiFT

HiFT requires a Short-Time Fourier Transform and its inverse with specific parameters (n_fft=16, hop_length=4, Hann window). Pure Java implementation:

```java
final class StftUtils {
    static float[][] stft(float[] signal, int nFft, int hopLength);  // → [n_bins, frames]
    static float[] istft(float[][] magnitude, float[][] phase,
                         int nFft, int hopLength);                   // → audio
}
```

Note: n_fft=16 is extremely small by traditional DSP standards (typical STFT uses 512–4096). This is correct for HiFT's architecture — it uses a learned spectral transform, not traditional spectral analysis. The 16-sample FFT produces 9 frequency bins (n_fft/2 + 1 = 9), matching the decoder output shapes: `[1, 18, stft_frames]` for STFT input (18 = 2 × 9, real+imaginary) and `[1, 9, time]` for magnitude/phase outputs. Verified against the reference Python implementation's parameters. Performance is trivially fast at this size.

## Provisioner Extensions

```java
// CosyVoice3 models from HuggingFace
Provisioner.ensureCosyVoice3Model();  // ~3.8 GB download
```

Model directory layout:
```
~/.casehub/models/cosyvoice3/
├── campplus.onnx
├── speech_tokenizer_v3.onnx
├── text_embedding_fp32.onnx
├── llm_backbone_initial_fp16.onnx
├── llm_backbone_decode_fp16.onnx
├── llm_decoder_fp16.onnx
├── llm_speech_embedding_fp16.onnx
├── flow_token_embedding_fp16.onnx
├── flow_pre_lookahead_fp16.onnx
├── flow_speaker_projection_fp16.onnx
├── flow.decoder.estimator.fp16.onnx
├── hift_f0_predictor_fp32.onnx
├── hift_source_generator_fp32.onnx
├── hift_decoder_fp32.onnx
├── tokenizer/
│   ├── vocab.json
│   ├── merges.txt
│   └── tokenizer_config.json
├── prompts/                              (bundled reference voices)
│   ├── en_female_nova_greeting.wav
│   └── en_male_onyx_greeting.wav
└── pipeline_manifest.json
```

The `pipeline_manifest.json` is authored as part of this issue — it declares the CosyVoice3 stage-to-model mappings, hyperparameters, provider preferences, and default prompt transcripts:

```json
{
  "defaultPrompts": {
    "en_female_nova_greeting.wav": "Hello, how can I help you today?",
    "en_male_onyx_greeting.wav": "Welcome, what can I assist you with?"
  }
}
```

## GPU Execution Providers (design, not implementation)

The manifest carries `ExecutionProviderConfig` with preferred provider and per-stage overrides. The framework reserves these fields but implements CPU-only initially.

When GPU support is added (separate issue):
- Framework probes for available providers at startup (check library availability)
- `OnnxRuntimeLibrary` gains vtable entries for `OrtSessionOptionsAppendExecutionProvider_CUDA` and CoreML equivalents
- Per-stage provider override enables keeping HiFT on FP32/CPU (CosyVoice3 docs: "FP32 required for HiFT numerical stability")
- Fallback chain: manifest preference → auto-detected GPU → CPU

## Integration with SpeechSession

No changes to `SpeechSession` or the WebSocket protocol. `TtsPipeline` implements `TextToSpeechService` — it's a drop-in replacement for any existing TTS engine:

```java
TextToSpeechService tts = TtsPipeline.fromModelDir(cosyVoice3Dir);
TextToSpeechService withLipSync = LipSyncEnricher.wrap(tts,
                                      EspeakPhonemeAligner.withDefaults());
```

The `LipSyncEnricher` composable pipeline from #213 provides lip-sync enrichment (CosyVoice3 returns empty phonemes → enricher fills them via espeak-ng alignment).

## OnnxRuntimeLibrary Extensions

CosyVoice3 requires one new data type constant in `OnnxRuntimeLibrary`:

- **INT32** (`ONNX_TENSOR_ELEMENT_DATA_TYPE_INT32 = 6`) — for attention masks, token IDs, and sequence lengths in transformer stages. The existing `createTensor(data, dataBytes, shape, dataType, arena)` method already accepts arbitrary `int dataType`, so INT32 tensors can be created with just the constant definition. No method changes needed.

Current constants: `FLOAT` (1), `INT64` (7), `BOOL` (9).

**FP16 model files:** 9 of 14 CosyVoice3 ONNX files are `_fp16.onnx`. The ORT C API auto-casts FP32 inputs to FP16 when a model's input spec requires FP16 — no explicit FP16 tensor creation is needed on the Java side. Java has no native float16 type, so all tensor I/O uses FP32; the ORT runtime handles precision conversion internally. This must be verified during the blocking ORT compatibility check (see below). If auto-cast doesn't work for specific stages, those stages can use FP32-only model files (available in the CosyVoice3 repo).

## ORT Version Compatibility

CosyVoice3's docs warn about onnxruntime 1.19+ FP16 issues in Python bindings. Our `OnnxRuntimeLibrary` uses the C API via FFM — a different code path. Verification plan:

1. Run the reference Python inference (`onnx_inference_pure.py`) with onnxruntime 1.18.0 to get a reference output
2. Port each stage to Java, run with our ORT (vtable v1.21.0)
3. Compare outputs at each stage boundary — numerical equivalence within tolerance
4. If FP16 issues surface in the C API, pin to onnxruntime 1.18.0 native library

This is a blocking verification — must confirm before full integration.

## Testing Strategy

| Component | Test type | What it verifies |
|-----------|-----------|-----------------|
| `TtsPipeline` | Unit (mocked stages) | Lifecycle management, manifest parsing, voice delegation |
| `TtsPipelineManifest` | Unit | JSON parsing, sealed hierarchy dispatch |
| `VoiceRegistry` (generalised) | Unit | Register/release lifecycle with VoiceData sealed hierarchy |
| `VoiceData` hierarchy | Unit | Exhaustive switch coverage, serialisation round-trip |
| `CosyVoice3Tokenizer` | Unit | Qwen2 BPE encoding correctness against Python `transformers` reference, multilingual test cases (EN, CJK, mixed) |
| `CosyVoice3Generator` | Unit (mocked sessions) | Input sequence construction, KV-cache protocol, EOS detection, sampling |
| `CosyVoice3Decoder` | Unit (mocked sessions) | Flow token processing, Euler step count, HiFT pipeline wiring |
| `CosyVoice3VoiceEncoder` | Unit (mocked sessions) | Mel extraction config, campplus input shape, speech tokenizer input shape |
| `FFT` | Unit | Forward/inverse FFT against numpy.fft reference, power-of-two and zero-padded sizes |
| `MelSpectrogram` | Unit | Mel band computation against librosa reference for all three configs (campplus, whisper, flow) |
| `StftUtils` | Unit | STFT/ISTFT round-trip, known-signal verification, HiFT n_fft=16 specific |
| `AudioResampler` | Unit | 48→16kHz and 48→24kHz against scipy.signal.resample_poly reference |
| `CosyVoice3 end-to-end` | Integration | Full synthesis with real models (guarded by `-Dspeech.integration=true`) |
| `Provisioner` | Integration | Model download, checksum verification, idempotent re-provisioning |
| Audio8 existing tests | Regression | All existing Audio8 tests pass after mechanical VoiceRegistry API migration (`getVoiceCodes()` → `get()` + `CodecVoiceData` cast). Behavioral assertions unchanged. |

## Future: Audio8 Retrofit

After CosyVoice3 validates the framework, Audio8 is retrofitted as a second pipeline configuration:

- `Audio8Manifest` extends `TtsPipelineManifest` — carries RuntimeManifest's 27 fields
- `Audio8Generator` wraps `DualARLoop` — implements `TtsGenerator`
- `Audio8Decoder` wraps `CodecDecoder` — implements `TtsDecoder`
- `Audio8VoiceEncoder` wraps existing codec encoder logic — produces `CodecVoiceData`
- `Audio8TextToSpeech` becomes a thin wrapper around `TtsPipeline.fromModelDir()`
- All existing Audio8 tests continue passing via the new pipeline

## Future: Streaming

Streaming remains on concrete implementations until a common pattern emerges. CosyVoice3's flow decoder uses 10-step Euler diffusion — whether it can run incrementally on partial token sequences needs investigation. If viable, CosyVoice3 streaming would add a `synthesiseStreaming()` method on a `CosyVoice3Pipeline` subclass or wrapper, similar to Audio8's current approach.

## References

- [HuggingFace ayousanz/cosy-voice3-onnx](https://huggingface.co/ayousanz/cosy-voice3-onnx) — CosyVoice3 ONNX models + reference inference
- [HuggingFace Lourdle/CosyVoice2-0.5B_ONNX](https://huggingface.co/Lourdle/CosyVoice2-0.5B_ONNX) — CosyVoice2 ONNX (incomplete)
- [FunAudioLLM/CosyVoice](https://github.com/FunAudioLLM/CosyVoice) — official CosyVoice repository
- `Audio8TextToSpeech.java` (`speech-sherpa`) — existing DualAR TTS with factory method, synthesis, streaming, voice registration (#213)
- `DualARLoop.java` (`speech-sherpa`) — autoregressive inference with KV-cache + Mamba state (#213)
- `VoiceRegistry.java` (`speech-sherpa`) — existing voice cloning lifecycle: `Map<String, int[]>` with `VoiceEncoder` → to be generalised to `VoiceData`
- `CodecDecoder.java` (`speech-sherpa`) — ONNX codec decoder for Audio8 (#213)
- `OnnxRuntimeLibrary.java` (`speech-sherpa`) — FFM C API bindings for onnxruntime (FLOAT, INT64, BOOL constants)
- `Provisioner.java` (`speech-sherpa`) — model download with SHA-256 verification, includes `provisionFromHuggingFace()` for Audio8
- `LipSyncEnricher.java` (`speech-api`) — composable lip-sync decorator wrapping any `TextToSpeechService` (#213)
- `EspeakPhonemeAligner.java` (`speech-sherpa`) — espeak-ng phoneme alignment implementing `PhonemeAligner` SPI (#213)
- GitHub issue #217 — CosyVoice2 TTS integration
- arXiv 2407.05407 — CosyVoice paper
