# Decisions — Audio8 TTS Integration (#213)

## D1: Model target — Audio8 TTS instead of Fish Speech

**Choice:** Integrate Audio8 TTS (DualAR ONNX derivative) instead of Fish Speech directly
**Alternatives:**
- Fish Speech direct — ONNX export doesn't work (GitHub #600, #903); LLaMA-based Dual-AR has unsupported ONNX operators
- Fish Audio cloud API — 150ms latency, not local inference
- Different TTS engine (Supertonic, etc.) — doesn't leverage Fish Audio's DualAR architecture quality
**Rationale:** Audio8 TTS provides a compact ONNX deployment of the Fish Audio DualAR architecture. CPU-only, ~1GB memory, runs on M2 MacBook. Same architecture lineage as Fish Audio S2 Pro. This introduces autoregressive inference (token-by-token with KV-cache) as a new paradigm — all existing TTS engines are single-pass feedforward. The paradigm shift brings variable latency, degenerate loop risk, and memory growth, but is the only path to DualAR quality levels via ONNX.
**Trade-offs:** Audio8 is a community derivative, not the canonical Fish Speech. Quality may differ from the full S2 Pro model. Maintenance risk — if Audio8 is a small team, ONNX model updates may stall.
**Sources:** [HuggingFace Audio8/audio8-TTS-0.1B-ONNX-INT8](https://huggingface.co/Audio8/audio8-TTS-0.1B-ONNX-INT8), [GitHub fishaudio/fish-speech#903](https://github.com/fishaudio/fish-speech/issues/903), [GitHub fishaudio/fish-speech#600](https://github.com/fishaudio/fish-speech/issues/600)
**Exploration:** deep-analysis
**Status:** captured

## D2: Model variants — both 0.1B and 0.6B

**Choice:** Support both 0.1B (INT8) and 0.6B (INT4) model variants
**Alternatives:**
- 0.1B only — proves pattern but misses higher quality
- 0.6B only — better quality but skips the smallest/fastest option
**Rationale:** User wants to hear what high-quality audio sounds like. Both variants share the same DualAR inference loop — different ONNX files and quantization, minimal extra integration work. The second variant is trivially addable once the inference loop works — just different model paths and Provisioner URLs.
**Trade-offs:** Two sets of model files to provision and test. Both are "Preview" quality. Different quantization (INT8 vs INT4) may need different ORT session options.
**Sources:** [HuggingFace Audio8/Audio8-TTS-Preview-0.6B-ONNX-INT4](https://huggingface.co/Audio8/Audio8-TTS-Preview-0.6B-ONNX-INT4)
**Exploration:** quick
**Status:** captured

## D3: Phoneme timing — forced alignment only (REVISED)

**Choice:** Post-hoc forced alignment for phoneme timing. No frequency-based real-time path.
**Alternatives:**
- Hybrid (frequency-based + forced alignment) — REJECTED by decision review R1-02: SpeechSession is sentence-by-sentence synchronous, not streaming. Complete audio exists before client receives anything. Frequency-based path solves a non-problem.
- Audio-only (no lip-sync) — simplest but avatar mouth doesn't move
- Rule-based timing — crude estimation from text/audio duration
**Rationale:** SpeechSession synthesises per-sentence, dispatches phonemes + audio together. The audio is complete before sending — forced alignment adds zero user-facing latency. No streaming architecture exists to justify a real-time frequency-based path. If streaming TTS is needed in future, that's a separate design decision.
**Trade-offs:** Alignment step adds server-side compute time per sentence. Requires an alignment model or library.
**Sources:** SpeechSession.java (sentence-by-sentence loop), VisemeMapping.java, decision review R1-02
**Exploration:** deep-analysis
**Status:** revised

## D4: Voice cloning — pre-registration with voice IDs (REVISED)

**Choice:** Support zero-shot voice cloning via pre-registration: a separate `registerVoice(Path referenceAudio)` method returns a voice ID string, which is then passed via `SynthesisOptions.voice()`
**Alternatives:**
- Path-as-string — `voice` carries a file path to reference WAV. Fragile, ties API to filesystem layout.
- API extension — `SynthesisOptions` gains a `referenceAudio` field. API-breaking change to speech-api shared dependency.
- Default voice only — skip cloning entirely
**Rationale:** Pre-registration keeps `SynthesisOptions.voice` as a String (no API break). The codec encoder session loads transiently during registration (encode reference → extract voice codes → cache codes → release encoder session), keeping steady-state memory low. Voice IDs are opaque strings mapping to cached codec tokens internally. This is Audio8's own pattern — "Normal synthesis loads only the Slow AR, Fast AR, and codec decoder sessions. Voice registration releases those sessions before loading the optional codec encoder."
**Trade-offs:** Stateful — registered voices live in memory until explicitly released or the service is closed. Need lifecycle management (register, list, release). The registration step adds latency on first use of a new voice.
**Depends on:** D6 (class structure must accommodate registration lifecycle)
**Sources:** [HuggingFace Audio8 model card](https://huggingface.co/Audio8/audio8-TTS-0.1B-ONNX-INT8), decision review R1-03
**Exploration:** quick
**Status:** revised

## D5: Integration approach — study Python implementation first

**Choice:** Read Audio8_TTS Python onnx_runtime code before porting to Java
**Alternatives:**
- Port from HuggingFace docs/model cards — faster start, higher risk of misunderstanding the autoregressive protocol
**Rationale:** The DualAR autoregressive inference loop (KV-cache, token feeding, codec decoding) is a new pattern for this codebase. Understanding the exact protocol from working code reduces risk.
**Trade-offs:** Slower start — need to clone and read the Python repo first.
**Sources:** [GitHub Audio8-AI/Audio8_TTS](https://github.com/Audio8-AI/Audio8_TTS)
**Exploration:** quick
**Status:** captured

## D6: Integration architecture — composition with internal stages (REVISED)

**Choice:** `Audio8TextToSpeech implements TextToSpeechService, AutoCloseable` as the public API, with internal stage classes for testability: `Audio8Tokenizer`, `DualARLoop` (slow AR + fast AR with KV-cache), `CodecDecoder`. Voice registration via `registerVoice()` / `releaseVoice()` methods.
**Alternatives:**
- Single monolithic class — VitsTextToSpeech precedent, but VitsTextToSpeech is 1 session with 1 feedforward pass. DualAR has 3-5 sessions, autoregressive loop, KV-cache, voice registration lifecycle. Not comparable.
- Fully decomposed public pipeline — separate public classes. Over-abstraction — consumers only need TextToSpeechService.
**Rationale:** The DualAR inference loop is qualitatively different from VITS: autoregressive token-by-token generation with KV-cache state, inter-stage hidden state passing (slow AR → fast AR), multiple ONNX sessions with mixed lifecycles (persistent inference sessions vs transient codec encoder for voice registration). Internal decomposition gives unit-testable stages without exposing implementation detail. The autoregressive loop specifically benefits from isolation — it can be tested for degenerate loops, max-length guards, and sampling behaviour independently.
**Trade-offs:** More internal classes than VitsTextToSpeech. But the complexity is inherent in the architecture, not manufactured by decomposition.
**Depends on:** D4 (voice registration lifecycle shapes the class)
**Sources:** VitsTextToSpeech.java (counter-example — why the precedent doesn't apply), decision review R1-04
**Exploration:** quick
**Status:** revised

## D7: Forced alignment — espeak-ng first, wav2vec2 ONNX later (REVISED)

**Choice:** Start with espeak-ng phonemization + duration estimation for alignment. Defer wav2vec2 ONNX to a follow-on issue if accuracy is insufficient.
**Alternatives:**
- wav2vec2 ONNX from the start — high accuracy but significant effort: no ready-made English phoneme alignment ONNX model exists, CTC pipeline is more than ~50 lines (preprocessing, character-phoneme mapping, frame conversion), adds model download dependency
- Shell out to Python aligner (MFA, WhisperX) — external runtime dependency
**Rationale:** espeak-ng is already integrated (`EspeakLibrary` FFM bindings, used by `VitsTextToSpeech`). The platform currently only supports English TTS. espeak-ng can phonemize text and estimate per-phoneme durations, which can be proportionally mapped to the actual audio duration. Marginal effort is near zero vs building a full wav2vec2 pipeline. If lip-sync quality is insufficient, wav2vec2 ONNX alignment can be added as a drop-in replacement behind the same `ForcedAligner` interface — the composable pipeline (D8) makes this a clean swap.
**Trade-offs:** espeak-ng duration estimates are approximate — they're synthesis-model durations, not actual durations from the generated audio. Quality may be lower than true forced alignment. But it's testable immediately with zero new dependencies.
**Depends on:** D3 (forced alignment only), D8 (composable pipeline enables future swap)
**Sources:** EspeakLibrary.java, VitsTextToSpeech.java (existing espeak-ng integration), decision review R1-05
**Exploration:** quick
**Status:** revised

## D8: Composable lip-sync pipeline — reusable across all TTS engines (REVISED)

**Choice:** Lip-sync as a composable pipeline wrapping any TextToSpeechService. Two components (not three — AudioFrequencyAnalyzer dropped per D3 revision):
- `PhonemeAligner` — SPI: `List<PhonemeTiming> align(String text, byte[] audio, int sampleRate)`. Initial impl: espeak-ng duration estimation. Future: wav2vec2 ONNX forced alignment.
- `LipSyncEnricher` — decorator wrapping any `TextToSpeechService`. If the delegate returns empty phonemes, runs `PhonemeAligner` and enriches the result. If the delegate returns non-empty phonemes (e.g. VitsTextToSpeech native timing), passes them through unchanged.
**Alternatives:**
- Engine-specific lip-sync (each TTS impl produces its own PhonemeTiming) — current VitsTextToSpeech approach, but doesn't scale
**Rationale:** Enricher only acts when phonemes are empty — no ambiguity about which phonemes win (R1-08). VitsTextToSpeech native timing is always preferred when available. Kokoro, SherpaOnnx, and Audio8 all return empty phonemes → enricher fills them in. The PhonemeAligner SPI allows swapping espeak-ng for wav2vec2 without changing the enricher or any consumer.
**Trade-offs:** espeak-ng alignment is approximate. But the SPI boundary means upgrading alignment quality is a single-class swap, not a pipeline redesign.
**Depends on:** D3 (forced alignment only), D7 (espeak-ng first)
**Sources:** VisemeMapping.java, SpeechSession.java, decision review R1-02, R1-08
**Exploration:** quick
**Status:** revised
