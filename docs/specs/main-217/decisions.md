# Decisions — CosyVoice TTS Pipeline Framework (#217)

## D1: Model target — CosyVoice3 instead of CosyVoice2

**Choice:** Integrate CosyVoice3 (ayousanz/cosy-voice3-onnx) instead of CosyVoice2 (Lourdle/CosyVoice2-0.5B_ONNX)
**Alternatives:**
- CosyVoice2 ONNX — incomplete export (only flow+hift, missing LLM backbone). Cannot do text-to-speech without the LLM.
- Wait for complete CosyVoice2 export — blocks the issue indefinitely
- Skip to Dia (#214) — viable but CosyVoice3's 4-stage architecture is a stronger genericisation test
**Rationale:** CosyVoice3 has a complete 14-file ONNX pipeline (campplus, speech tokenizer, LLM backbone, flow decoder, HiFT vocoder). 3.8GB total. Apache 2.0. Includes reference Python inference (`onnx_inference_pure.py`) for porting. The 4-stage pipeline is architecturally different from Audio8's DualAR — proving the pipeline framework works for both validates the abstraction. CosyVoice2's ONNX export is missing the LLM backbone; the Lourdle repo only exported flow+hift.
**Trade-offs:** CosyVoice3 is 3.8GB (vs Audio8's 1GB). CPU performance is 73-226s per utterance (addressed by D4). onnxruntime 1.18.0 constraint documented for Python bindings — C API via FFM may not hit same issue (blocking verification: must confirm ORT C API compatibility before committing to integration, as version incompatibilities can silently produce incorrect inference or crash with opaque FFM errors). Community ONNX export requires validation: run reference Python inference, compare output against Java port at each pipeline stage, verify numerical equivalence within tolerance before committing to this model.
**Sources:** [HuggingFace Lourdle/CosyVoice2-0.5B_ONNX](https://huggingface.co/Lourdle/CosyVoice2-0.5B_ONNX), [HuggingFace ayousanz/cosy-voice3-onnx](https://huggingface.co/ayousanz/cosy-voice3-onnx), GitHub issue #217
**Exploration:** deep-analysis
**Status:** captured

## D2: Pipeline abstraction — manifest-driven with stage SPIs

**Choice:** Pipeline descriptor approach — a `TtsPipelineManifest` (JSON in model directory) declares stages, ONNX files, tensor mappings, hyperparameters, and execution provider. Four typed stage SPIs: `TtsTokenizer`, `TtsGenerator`, `TtsDecoder`, `TtsVoiceEncoder`. Framework manages session lifecycle, provisioning, and manifest-driven wiring.
**Alternatives:**
- Functional composition (builder DSL) — more flexible but "adding a model" means writing code, not configuration. Builder DSL becomes complex with stage interdependencies.
- Template method per model family — too rigid for architectural diversity between Audio8 (DualAR + Mamba) and CosyVoice3 (transformer KV-cache + flow diffusion + HiFT vocoder)
- Shared interface + model impls (no manifest) — less ambitious, doesn't genericise session lifecycle or provisioning
**Rationale:** The framework's primary value is lifecycle management — session creation/configuration/closure, model provisioning, manifest-driven wiring — not making stage logic generic. The four stage SPIs are typed contracts that standardise the interface between framework and model-specific logic, enabling the framework to manage the surrounding infrastructure uniformly. The generator stage is always model-specific (DualAR with Mamba state vs transformer KV-cache); the SPI standardises the contract around it, not the logic inside it. Audio8 already uses equivalent functional interfaces (`Generator`, `Decoder`) — the pipeline SPIs formalise these into named, framework-managed contracts with manifest-driven configuration. Adding a new model = write stage implementations + create manifest JSON.
**Trade-offs:** Manifest parsing adds a layer of indirection. Stage SPIs are thin by design — their value is in enabling framework lifecycle management, not in abstracting model internals.
**Sources:** Audio8TextToSpeech.java, DualARLoop.java, CosyVoice3 inference pipeline
**Exploration:** deep-analysis
**Status:** captured

## D3: Retrofit scope — CosyVoice3 first, Audio8 retrofit second

**Choice:** Implement CosyVoice3 as the first pipeline model. Retrofit Audio8 into the framework as a separate follow-up task, validating that the abstraction generalises.
**Alternatives:**
- Audio8 + CosyVoice3 simultaneously — risks over-fitting framework design to Audio8's existing patterns due to backward-compatibility pressure (existing tests must keep passing, 27-field RuntimeManifest influences new manifest design)
- Parallel but separate — leave Audio8 as-is, both share TextToSpeechService contract already
**Rationale:** Design the framework with both architectures in mind (informed by D1's analysis of CosyVoice3 and existing Audio8 code), but implement CosyVoice3 first. This gives a clean framework design unconstrained by backward compatibility with existing Audio8 code. CosyVoice3 serves as the first validation (the new, more complex pipeline — 14 ONNX files, 4 architecturally distinct stages). Audio8 retrofit then proves the abstraction generalises. Audio8's existing tests continue passing unchanged until the retrofit occurs. The "abstraction validated by only one model is untested" concern applies to shipping, not to development sequencing — the framework is designed for both architectures from day one.
**Trade-offs:** Audio8 retrofit becomes a separate task, delaying full validation. Risk that the framework needs adjustment when Audio8 is retrofitted — mitigated by designing with Audio8's patterns (DualAR, Mamba state, codec decoder, streaming overlap-subtract) explicitly in mind during framework design.
**Sources:** Audio8TextToSpeech.java, DualARLoop.java, CodecDecoder.java, VoiceRegistry.java
**Exploration:** quick
**Status:** revised (R1-03: changed from simultaneous retrofit to CosyVoice3-first sequencing)

## D4: Performance — GPU execution strategy

**Choice:** Design the pipeline manifest to carry execution provider preferences (provider type, per-stage overrides, fallback chain) from day one. Implement CPU-only first. Add GPU execution provider support (CUDA, CoreML) as a follow-up once the pipeline framework is stable. When GPU support is added: framework auto-detects available providers at startup, manifest can override with preferred provider and per-stage provider (e.g., keeping HiFT on FP32/CPU for numerical stability), fallback chain: manifest preference → auto-detected GPU → CPU.
**Alternatives:**
- GPU from day one — couples GPU complexity with framework correctness validation. OnnxRuntimeLibrary currently has zero GPU infrastructure (no CUDA/CoreML vtable entries, CPU-only session creation via OrtMemTypeCPU/OrtDeviceAllocator, no device memory management). Adding GPU requires new vtable calls, provider-specific library loading, device memory management, per-session provider configuration, and fallback semantics.
- CPU-only permanently — validates framework but CosyVoice3 is unusable for interactive speech at 73-226s per utterance
- Explicit-only provider config — no auto-detection, manifest must declare provider. Requires users to know hardware.
**Rationale:** GPU acceleration is orthogonal to pipeline abstraction correctness. The manifest carries provider preferences as JSON fields (essentially free to add), but actual GPU implementation requires significant new infrastructure in OnnxRuntimeLibrary. Decoupling these concerns lets the framework stabilise on CPU first. Per-stage override enables keeping HiFT on CPU for numerical stability if needed (CosyVoice3 docs note FP32 is "required for HiFT numerical stability").
**Trade-offs:** CosyVoice3 remains slow on CPU until GPU support lands. Manifest schema carries provider fields before they're implemented — but this is a feature, not a cost.
**Depends on:** D2 (manifest carries provider config)
**Sources:** [OnnxRuntime Execution Providers docs](https://onnxruntime.ai/docs/execution-providers/), OnnxRuntimeLibrary.java (ORT_API_VERSION = 18, CPU-only vtable entries), CosyVoice3 precision strategy notes
**Exploration:** quick
**Status:** revised (R1-04, R1-13: merged D9 into D4; changed from "GPU from day one" to "design for GPU, implement CPU-first")

## D5: Voice cloning — unified VoiceRegistry with pluggable VoiceEncoder SPI

**Choice:** Extend VoiceRegistry with a pluggable `VoiceEncoder` SPI. Audio8 encodes to codec tokens; CosyVoice3 encodes to speaker embedding + speech tokens. Registry caches whatever the encoder produces. Same register/release/get API for consumers.
**Alternatives:**
- Per-model voice cloning — each pipeline handles its own cloning. Duplicates lifecycle management.
- Defer voice cloning — CosyVoice3 ships with default voice only. Reduces scope but loses a key feature.
**Rationale:** VoiceRegistry already has the right lifecycle (register → cache → release). The only model-specific part is encoding reference audio to a model-native representation. A pluggable `VoiceEncoder` makes the registry generic. CosyVoice3's encoder runs campplus (speaker embedding) + speech_tokenizer (speech tokens) — a different representation from Audio8's codec tokens, but the same lifecycle. VoiceRegistry is a standalone class with ConcurrentHashMap storage and UUID-based registration — its generalization (changing VoiceEncoder's return type and cache value type) is independent of the pipeline framework.
**Trade-offs:** VoiceEncoder output type must use the sealed VoiceData hierarchy (D8) since Audio8 produces codec tokens and CosyVoice3 produces embedding + speech tokens.
**Depends on:** D8 (VoiceData representation)
**Sources:** VoiceRegistry.java, CosyVoice3 campplus + speech_tokenizer architecture
**Exploration:** quick
**Status:** revised (R1-08: removed false dependency on D2 — VoiceRegistry generalization is independent of pipeline framework. New dependency on D8 for voice data representation.)

## D6: Streaming — batch pipeline first, extract common patterns later

**Choice:** Pipeline operates in batch mode. Individual model implementations handle their own streaming internally (as Audio8 already does via `synthesiseStreaming()`). If a common streaming pattern emerges from having two or more models with working streaming, extract it into the framework.
**Alternatives:**
- Streaming-aware pipeline from day one — stages declare incremental processing capability, framework manages chunking and delivery. Premature: CosyVoice3's flow decoder uses 10-step Euler diffusion that may not support incremental processing, and Audio8's overlap-subtract streaming is tightly coupled to codec frame timing (codecHopLength, contextFrames, guardFrames from StreamingConfig).
- Stream at TextToSpeechService level only — keep streaming external. This is effectively the current state.
**Rationale:** Designing a streaming framework that immediately falls back to batch for one of its two consumers is a sign the abstraction is premature. Audio8's streaming works because its overlap-subtract logic is tightly coupled to DualAR-specific parameters: codec hop length, context frames, guard frames — all derived from RuntimeManifest and used in the windowed decode loop (Audio8TextToSpeech lines 189-245). CosyVoice3's streaming (if feasible given the 10-step Euler diffusion in the flow decoder) would work completely differently. There is no common "streaming pipeline" infrastructure to extract from one working example. Follow the "extract, don't speculate" pattern.
**Trade-offs:** Streaming remains model-specific until a common pattern is proven. New models must implement their own streaming logic rather than inheriting it from the framework.
**Depends on:** D2 (pipeline framework architecture)
**Sources:** Audio8TextToSpeech.synthesiseStreaming() (lines 189-245), DualARLoop.iterateFrames(), StreamingConfig record
**Exploration:** quick
**Status:** revised (R1-05: changed from streaming-aware pipeline to batch-first with extract-later pattern)

## D7: Manifest structure — sealed hierarchy with common header

**Choice:** Typed manifest per model family using a sealed hierarchy. A common `PipelineHeader` record carries framework-level concerns (stage declarations, ONNX file lists, execution provider preferences, sample rate). Model-specific manifest records (e.g., `Audio8Manifest`, `CosyVoice3Manifest`) extend with their own hyperparameters. The framework reads the common header; stage implementations access their model-specific manifest for configuration.
**Alternatives:**
- Flat union manifest — all model-specific parameters in one record/map. Configuration sprawl grows with each model family. RuntimeManifest already has 27 Audio8-specific fields (mambaChunkSize, mambaDConv, mambaDState, etc.); CosyVoice3 would add its own set. Not type-safe.
- Stage-declared file lists only (original D7) — stages declare ONNX file lists, stage implementations handle internal wiring. Doesn't address hyperparameter sprawl across model families.
- Nested stage hierarchy — stages contain sub-stages. Over-complex manifest schema.
**Rationale:** The existing RuntimeManifest demonstrates the configuration sprawl problem: 27 fields, all Audio8-specific. CosyVoice3 would add transformer config, flow decoder steps, HiFT parameters. A sealed hierarchy keeps model-specific parameters type-safe and out of the framework's generic manifest schema. Consistent with D8's sealed VoiceData hierarchy — the same design principle (type-safe model-specific data via sealed interfaces) applied to configuration.
**Trade-offs:** Adding a new model family requires a new manifest record and extending the sealed hierarchy. This is a feature — it forces explicit declaration of each model's configuration needs.
**Depends on:** D2 (manifest-driven pipeline)
**Sources:** RuntimeManifest.java (27 Audio8-specific fields), CosyVoice3 architecture (14 ONNX files, 4 stages)
**Exploration:** quick
**Status:** revised (R1-10: changed from flat stage file groups to sealed manifest hierarchy with common header)

## D8: Voice type variance — sealed VoiceData hierarchy

**Choice:** Sealed `VoiceData` hierarchy using Java 21 records:
```java
sealed interface VoiceData permits CodecVoiceData, EmbeddingVoiceData {
    record CodecVoiceData(int[] codecTokens) implements VoiceData {}
    record EmbeddingVoiceData(float[] speakerEmbedding, int[] speechTokens,
                              float[] promptMel) implements VoiceData {}
}
```
Audio8's VoiceEncoder produces `CodecVoiceData`; CosyVoice3's produces `EmbeddingVoiceData` (including precomputed prompt mel for flow decoder conditioning). Generator stage uses exhaustive `switch` to consume model-specific data.
**Alternatives:**
- `VoiceData(Map<String, byte[]> segments)` — generic but stringly-typed keys, silent null on typos, unnecessary byte[] serialization ceremony (encoding int[] → byte[] for storage, decoding byte[] → int[] for consumption)
- Raw Object with casts — minimal framework code but no type safety at registry boundary
**Rationale:** On Java 21, a sealed hierarchy is strictly superior to Map<String, byte[]>. Two model families means two records — trivial code, maximum type safety. Compile-time exhaustiveness (`switch (voiceData)`) forces handling every variant. No serialization ceremony — VoiceRegistry currently stores `int[]` directly, and CodecVoiceData preserves that native type. No silent null on typos — `voiceData instanceof EmbeddingVoiceData e` is compile-checked. Adding a new model family adds a `permits` clause and a new record — the compiler then flags every unhandled case.
**Trade-offs:** Adding a new model requires extending the sealed hierarchy. Minimal cost for maximum type safety.
**Sources:** VoiceRegistry.java (Map<String, int[]> cache, VoiceEncoder returning int[]), CosyVoice3 campplus (float[] embedding) + speech_tokenizer (int[] speech tokens)
**Exploration:** quick
**Status:** revised (R1-01: changed from Map<String, byte[]> to sealed hierarchy — eliminates stringly-typed keys, serialization ceremony, and silent null on typos)

## D9: (Merged into D4)

GPU provider configuration (auto-detect with manifest override) is now part of D4's unified GPU execution strategy.
**Status:** revised (R1-13: merged into D4)

## D10: Module placement — all in speech-sherpa

**Choice:** Pipeline framework lives in `speech-sherpa` alongside all ONNX-based TTS implementations. Stage SPIs, manifest parsing, session lifecycle, GPU provider detection — all in the same module.
**Alternatives:**
- New speech-pipeline module — cleaner separation but adds a module for a single-consumer abstraction
- Split SPIs to speech-api — the stage contracts (TtsTokenizer, TtsGenerator, TtsDecoder, TtsVoiceEncoder) could technically be ORT-independent, but they're thin enough (essentially typed Function signatures) that splitting them into speech-api creates a module for a handful of interfaces with no implementation and no current consumer outside speech-sherpa
**Rationale:** The pipeline framework is ONNX-specific — it manages ORT sessions, GPU providers, and model provisioning. The stage contracts could be written without ORT references, but the abstraction overhead of a separate module isn't justified for three or four thin interfaces with no consumer outside speech-sherpa. The existing pattern — `TextToSpeechService` in speech-api, concrete implementations in speech-sherpa — already provides the right public abstraction boundary. Pipeline SPIs are internal framework contracts, not public API.
**Trade-offs:** speech-sherpa grows larger. But the pipeline framework replaces boilerplate currently in Audio8TextToSpeech — net size increase is moderate.
**Depends on:** D2 (pipeline framework architecture)
**Sources:** speech-sherpa module structure, speech-api module (TextToSpeechService, PhonemeAligner, LipSyncEnricher)
**Exploration:** quick
**Status:** captured

## D11: Pipeline framework scope — direct-ORT multi-stage models only

**Choice:** The pipeline framework targets multi-stage models that use `OnnxRuntimeLibrary` (direct FFM C API to onnxruntime). SherpaLibrary-based models (Kokoro, SherpaOnnx) and hybrid models (Vits with espeak) are not in scope — they continue using their existing integration paths via `TextToSpeechService`.
**Alternatives:**
- Universal TTS pipeline framework — cover all TextToSpeechService implementations. Unnecessary: SherpaLibrary models are single-step wrappers around sherpa-onnx's C API with no multi-stage pipeline. Forcing them into a pipeline abstraction adds complexity without value.
- ORT-only with migration path for SherpaLibrary models — would require reimplementing Kokoro and SherpaOnnx as multi-stage ORT pipelines. No benefit unless their sherpa-onnx integration becomes a maintenance burden.
**Rationale:** The four TextToSpeechService implementations use two fundamentally different integration paths: (1) `OnnxRuntimeLibrary` — direct FFM C API calls managing individual ONNX sessions (Audio8, Vits), and (2) `SherpaLibrary` — FFM bindings to sherpa-onnx's higher-level C API that handles session management internally (Kokoro, SherpaOnnx). The pipeline framework manages ORT session lifecycle, multi-stage wiring, and manifest-driven configuration — concerns that only exist for direct-ORT multi-stage models. SherpaLibrary models delegate all of this to sherpa-onnx itself. Vits is a single-session ORT model with an espeak dependency — it could eventually be a pipeline, but has no current need. The pipeline framework's initial scope is Audio8 and CosyVoice3.
**Trade-offs:** Future models using SherpaLibrary won't benefit from the pipeline framework. Acceptable — SherpaLibrary already provides its own lifecycle management.
**Sources:** KokoroTextToSpeech.java (SherpaLibrary), SherpaOnnxTextToSpeech.java (SherpaLibrary), VitsTextToSpeech.java (ORT + EspeakLibrary), Audio8TextToSpeech.java (ORT)
**Exploration:** implicit decision surfaced in review
**Status:** captured

## D12: Session lifecycle, concurrency, and pipeline error semantics

**Choice:** Pipeline sessions are created once at pipeline construction and held until `close()`. Mutable inference state (KV caches, Mamba state, flow diffusion intermediaries) is allocated per-synthesis-call using confined arenas. Multiple concurrent synthesis requests share sessions with isolated state. Pipeline construction is all-or-nothing: if any ONNX file fails to load, the entire pipeline fails cleanly.
**Alternatives:**
- Per-request session creation — safe isolation but expensive (ORT session creation is heavyweight)
- Lazy session creation with eviction — reduces memory footprint but adds complexity and unpredictable latency
- Partial initialization with degraded capability — load what's available, skip failed stages. Dangerous for multi-stage pipelines where stages depend on each other's output.
**Rationale:** Mirrors Audio8's existing pattern: `fromModelDir()` creates all sessions (slowAr, fastAr, codecDecSession) and holds them until `close()`. DualARLoop allocates mutable state (SlowState with cacheKeys, cacheValues, convStates, ssmStates) per call using `Arena.ofConfined()` — safe for concurrent use. CosyVoice3 follows the same pattern: LLM KV-cache and flow diffusion state allocated per-call. ORT sessions are thread-safe for `Run()` with different `RunOptions`. All-or-nothing construction is the right default for a 14-file pipeline — partial initialization creates confusing error states where some stages work and others silently fail.
**Trade-offs:** Memory budget is a deployment concern: Audio8 ~1GB, CosyVoice3 ~3.8GB. Multiple loaded pipelines may require memory-aware scheduling at the application layer. The framework should expose session count and model size metadata to support such policies.
**Sources:** Audio8TextToSpeech.fromModelDir() (lines 111-166), DualARLoop.iterateFrames() (per-call Arena allocation), ORT session thread-safety
**Exploration:** implicit decision surfaced in review
**Status:** captured

## D13: Streaming on TextToSpeechService interface — deferred

**Choice:** Do not add a streaming method to `TextToSpeechService` now. Streaming remains on concrete implementations (e.g., `Audio8TextToSpeech.synthesiseStreaming()`). Revisit when two or more models have working streaming with a common signature.
**Alternatives:**
- Add `synthesiseStreaming()` to `TextToSpeechService` now — promotes streaming to first-class service concern. Premature: only Audio8 supports streaming, and its streaming signature is architecture-specific (AudioChunkCallback with codec-frame-aligned chunk counts).
- Add streaming as a separate `StreamingTextToSpeechService` interface — allows opt-in without burdening non-streaming implementations. Viable but premature for the same reason.
**Rationale:** Consistent with D6 (batch pipeline first). Adding streaming to the service interface before a second model supports it risks baking Audio8's streaming semantics into the public API. When CosyVoice3 streaming is investigated (if the flow decoder can run incrementally), the common streaming contract can be designed with both models' requirements in mind.
**Trade-offs:** Consumers wanting streaming must downcast to the concrete implementation. This is the current state and acceptable until streaming is proven across multiple models.
**Sources:** TextToSpeechService (speech-api: synthesise() only), Audio8TextToSpeech.synthesiseStreaming() (lines 183-245)
**Exploration:** implicit decision surfaced in review
**Status:** captured
