## D1: tar.bz2 extraction mechanism

**Choice:** ProcessBuilder + system `tar`
**Alternatives:**
- Apache Commons Compress — portable but adds a runtime dependency (~800KB) for one extraction call; module currently has only annotation-only compile deps (jspecify)
- Pure Java tar + bzip2 — complex, error-prone, not worth maintaining
**Rationale:** Module is already platform-specific (requires native libs + JDK 22+). System `tar` with bzip2 support is universally available on all target platforms (macOS, Linux, Windows 10+). Zero runtime dependencies added. ProcessBuilder varargs form avoids shell interpolation.
**Trade-offs:** Fails if `tar` is somehow absent — but so does the entire module without native libraries.
**Sources:** GE-20260420-b9c06c (Java ZipInputStream can't handle bzip2), research doc §11
**Exploration:** quick
**Status:** captured

## D2: Provisioner architecture

**Choice:** Single `Provisioner` class with two entry points (`provisionNativeLibrary()`, `provisionModel()`)
**Alternatives:**
- Split `NativeProvisioner` + `ModelProvisioner` — cleaner separation but duplicates download/extract logic for no consumer benefit
- SPI in speech-api — over-engineered; provisioning is entirely sherpa-onnx-specific (URLs, archive format, directory layout)
- SPI within speech-sherpa using @DefaultBean — module is intentionally CDI-free (pure Java); adding CDI infrastructure for one override point is wrong direction
**Rationale:** Download + extract is the same operation for both native libs and models — only the URL and target directory differ. Package-private. Download URL is configurable via system property for air-gapped/mirror environments.
**Trade-offs:** Single class couples native and model provisioning — acceptable since both are sherpa-onnx-specific and use the same mechanism. Package-private means no consumer override, but system property URL override covers the real use cases (CI, mirrors, air-gapped).
**Sources:** SherpaLibrary.java (existing Tier 1+2 loading), research doc §11
**Exploration:** quick
**Status:** captured

## D3: Model provisioning trigger and discovery

**Choice:** Explicit provisioning at construction time via factory method, not inside `transcribe()`
**Alternatives:**
- Lazy inside `transcribe()` — violates SPI contract; `transcribe()` is pure computation, hiding a 5-10s HTTP download is a surprise; ambiguous error semantics (download failure vs transcription failure)
- Eager on `SherpaLibrary.load()` — conflates library loading with model selection; TTS-only users shouldn't download a Whisper model
- Separate `SherpaModelRegistry` — adds abstraction for a problem with exactly one model per use case
**Rationale:** `Provisioner.ensureModel("sherpa-onnx-whisper-tiny")` returns `Path` — explicit, downloads if needed. Factory `SherpaOnnxSpeechToText.withDefaults()` calls this internally for zero-install convenience. `SherpaConfig.defaults()` no-arg overload returns config with default model path. Provisioning is visible at construction site, not hidden in the SPI method.
**Trade-offs:** Requires explicit `withDefaults()` or manual `ensureModel()` call — adds one step vs the "hidden in transcribe()" approach. But this step makes the network dependency visible and testable.
**Depends on:** D2 (provisioner architecture)
**Sources:** research doc §4 (Whisper model details), research doc §11 (model provisioning), review R1-10 (SPI contract violation)
**Exploration:** quick (revised after decision review)
**Status:** revised

## D4: Concurrency handling for simultaneous first-use calls

**Choice:** `FileLock` + atomic sibling rename
**Alternatives:**
- Marker file with staleness heuristic — reimplements `FileLock` poorly; marker persists after crash, requires age-based cleanup, polling parameters hard to tune
- JVM-level synchronization only — handles in-process but not cross-process (two JVMs starting simultaneously)
**Rationale:** `FileChannel.tryLock()` on a lock file in the target parent directory. Auto-releases on process crash (kernel reclaims). `tryLock()` returns null if held — no polling needed. Temp dir created as sibling of target (same filesystem) to ensure `Files.move(ATOMIC_MOVE)` succeeds. `/tmp` → `~/.casehub/` would fail `ATOMIC_MOVE` on Linux (tmpfs vs ext4).
**Trade-offs:** Slightly more complex API (FileChannel lifecycle), but correct by construction — no staleness heuristics, no stale markers, no polling.
**Depends on:** D2 (provisioner architecture)
**Sources:** Review R1-14 (cross-filesystem ATOMIC_MOVE), R1-15 (FileLock vs marker file)
**Exploration:** quick (revised after decision review)
**Status:** revised

## D5: Checksum verification of downloaded native code

**Choice:** Embed SHA-256 hashes per platform, verify after download
**Alternatives:**
- Trust the download (HTTPS only) — MITM on HTTPS is hard but mirrors or supply-chain compromise at the source are real risks
- GPG signature verification — requires GPG tooling; k2-fsa releases are not GPG-signed
- Skip verification — unacceptable for native code loaded into JVM process
**Rationale:** SHA-256 hashes for each platform's archive are known at build time (one version of sherpa-onnx per release of speech-sherpa). Embed as constants in `Provisioner`. Verify the downloaded archive before extraction. If mismatch, delete and throw with hash comparison for diagnosis.
**Trade-offs:** Hashes must be updated when bumping sherpa-onnx version — but so do the byte offsets in `SherpaLayouts`, so this is already a manual step.
**Sources:** Review R1-18 (supply-chain attack vector)
**Exploration:** quick (added after decision review)
**Status:** captured

## D6: MicrophoneCapture — API shape and placement

**Choice:** Pure audio feeder in speech-sherpa with injectable TargetDataLine
**Alternatives:**
- Callback-driven (MicrophoneCapture polls stream and notifies listener) — couples capture to result polling; caller can poll RecognitionStream directly
- Self-contained (MicrophoneCapture manages RecognitionStream lifecycle) — over-couples; violates single responsibility
- Place in speech-api — speech-api is pure SPIs with zero dependencies; MicrophoneCapture is a concrete utility
**Rationale:** Simplest composable design — MicrophoneCapture only bridges TargetDataLine → RecognitionStream.acceptSamples(). Caller manages RecognitionStream and polls for results. Constructor takes TargetDataLine for testability; factory openDefault() for convenience. Virtual thread for the capture loop (blocking I/O). Package matches WavReader/WavWriter placement.
**Trade-offs:** Caller must manage RecognitionStream lifecycle and polling separately — acceptable since RecognitionStream already has a clean API for this.
**Sources:** RecognitionStream.java (speech-api), SpeechCli.stream() (chunking pattern), issue #183
**Exploration:** quick
**Status:** captured

## D7: TTS model filename auto-detection approach

**Choice:** Private `findTtsModel(Path)` method in SherpaOnnxTextToSpeech
**Alternatives:**
- Shared utility class consolidating STT and TTS model discovery — over-engineering for XS; STT has different semantics (component filtering, int8 preference)
**Rationale:** Simplest change. Scan directory for .onnx files: single file → use it, multiple → prefer model.onnx for backward compat, zero → throw SherpaException.
**Trade-offs:** Duplication with STT findModel() — acceptable since the filtering logic differs.
**Sources:** SherpaOnnxTextToSpeech.java:101, SherpaOnnxStreamingSpeechToText.java:118-133, issue #177
**Exploration:** quick
**Status:** captured

## D8: Phoneme timing extraction method

**Choice:** Extract exact per-phoneme frame counts from the VITS model's internal duration predictor output (`/Ceil_output_0` tensor)
**Alternatives:**
- espeak-ng heuristic with proportional scaling — approximate (±30-50ms), no additional native deps, but timing doesn't come from the actual model
- Post-synthesis forced alignment via STT — requires separate Whisper model download (~75MB), only gives word-level not phoneme-level, significant overhead
- Wait for sherpa-onnx issue #3705 — blocks avatar lip-sync indefinitely
**Rationale:** Verified empirically: modifying the Piper VITS ONNX model to expose `/Ceil_output_0` as a second output gives exact per-phoneme frame counts. Total duration from frames matches audio duration exactly (220.6ms = 220.6ms in test). The duration predictor already runs during normal inference — we're reading its output instead of discarding it. Zero additional CPU cost.
**Trade-offs:** Requires ONNX model modification and direct onnxruntime inference (bypassing sherpa-onnx TTS API). Node name `/Ceil_output_0` is specific to Piper VITS export — other VITS variants may differ.
**Sources:** [sherpa-onnx #3705](https://github.com/k2-fsa/sherpa-onnx/issues/3705), [Piper export_onnx.py](https://github.com/rhasspy/piper/blob/master/src/python/piper_train/export_onnx.py), empirical model inspection of en_US-lessac-medium.onnx
**Exploration:** deep-analysis
**Status:** captured

## D9: TTS inference engine

**Choice:** Direct onnxruntime via Java FFM, bypassing sherpa-onnx TTS C API entirely
**Alternatives:**
- Dual-path (sherpa-onnx for audio + onnxruntime for durations) — runs model twice, needs phonemization for both paths
- Conditional (sherpa-onnx normally, onnxruntime when phonemes requested) — two code paths to maintain
**Rationale:** onnxruntime is already loaded as a native lib by SherpaLibrary. Its C API uses a vtable pattern — `OrtGetApiBase()` returns a struct of function pointers. Only ~10 function bindings needed for inference. Single pass gives both audio and exact durations. No model double-execution.
**Trade-offs:** New FFM binding layer for onnxruntime C API. New `TextToSpeechService` implementation alongside existing `SherpaOnnxTextToSpeech` (which remains for users who don't need phoneme timing).
**Sources:** onnxruntime C API (`OrtGetApiBase`, vtable pattern), SherpaLibrary.java (already loads libonnxruntime)
**Exploration:** deep-analysis
**Depends on:** D8 (duration extraction requires onnxruntime to read the extra output)
**Status:** captured

## D10: Text-to-phoneme conversion (phonemization)

**Choice:** Provision espeak-ng shared library via Provisioner, call `espeak_TextToPhonemes()` via FFM
**Alternatives:**
- espeak-ng binary via ProcessBuilder — simpler code but adds system dependency, ~50ms process overhead per call
- Pure Java CMU Pronouncing Dictionary — no native deps but English-only, may not match espeak-ng output
**Rationale:** Piper VITS models use espeak-ng for phonemization internally. By provisioning the espeak-ng library (from [espeakng-loader releases](https://github.com/thewh1teagle/espeakng-loader/releases)), we get exact phoneme-to-ID matching. espeak-ng-data directory already ships with Piper models. ~2MB additional download. Same provisioning pattern as sherpa-onnx.
**Trade-offs:** Additional native library to provision and bind. Only 4-5 FFM function bindings needed (init, set voice, text to phonemes, terminate).
**Depends on:** D9 (bypassing sherpa-onnx means we need our own phonemization)
**Sources:** espeak-ng C API (speak_lib.h), Piper model config JSON (phoneme_id_map, espeak.voice)
**Exploration:** quick
**Status:** captured

## D11: ONNX model modification method

**Choice:** protobuf-java to parse and modify the ONNX model in pure Java
**Alternatives:**
- Python script via ProcessBuilder — simpler code but adds Python+onnx runtime dependency; ProcessBuilder in a library is surprising; wrong for a shipping design (review R1-05)
- Minimal protobuf byte manipulation — ~100 lines, fragile if format changes
**Rationale:** ONNX format is Protocol Buffers. protobuf-java (~1.7MB) is a common Java dependency. The modification is ~30 lines: parse ModelProto, append ValueInfoProto for /Ceil_output_0 to graph.output, serialize. No Python, no process spawning. Fully testable in unit tests with synthetic ONNX models.
**Trade-offs:** Adds protobuf-java compile dependency (~1.7MB). Requires the ONNX proto schema (stable, versioned).
**Sources:** Design review R1-05, ONNX proto3 schema
**Exploration:** quick
**Status:** revised (from Python, per design review R1-05)

## D12: GECToR model selection — consumer-driven

**Choice:** Support both DeBERTa-base (~500MB) and DeBERTa-large (~2GB). Model selection is a consumer choice via config, not hardcoded.
**Alternatives:**
- Large only — best quality but 2GB download, slower inference
- Base only — faster but lower quality
**Rationale:** Different consumers have different trade-offs. Live transcription cleanup may prefer speed (base). Document editing may prefer quality (large). The filter takes a model directory; the Provisioner offers both as named models.
**Trade-offs:** Two models to export, test, and maintain checksums for.
**Sources:** [gotutiyan/gector](https://github.com/gotutiyan/gector), issue #179
**Exploration:** quick
**Status:** captured

## D13: GECToR tokenizer — SentencePiece4J (pure Java)

**Choice:** Use SentencePiece4J (`io.github.eix128:sentencepiece4j`) — pure Java SentencePiece implementation
**Alternatives:**
- SentencePiece via FFM — SentencePiece has no C API (C++ only), FFM can't call C++ classes
- HuggingFace tokenizers (Rust) — no official C API, would need mlc-ai/tokenizers-cpp wrapper
- Pure Java from scratch — ~250 lines of UNIGRAM Viterbi, risk of mismatches
- ProcessBuilder with spm_encode — ~50ms overhead per call, requires system install
**Rationale:** SentencePiece4J is officially listed by Google's SentencePiece repo. SMILE (haifengl) removed its own SentencePiece class in v5.0.2 in favor of this library. Pure Java, reads .model protobuf files, supports BPE and UNIGRAM. Zero native deps — just a Maven dependency. No SentencePieceLibrary FFM class needed.
**Trade-offs:** Third-party dependency. Must validate that its output matches the native SentencePiece library exactly for DeBERTa models.
**Sources:** [SentencePiece4J](https://github.com/eix128/sentencepiece4j), [Google SentencePiece Java listing](https://github.com/google/sentencepiece)
**Exploration:** deep-analysis
**Status:** revised (from FFM, after discovering SentencePiece has no C API)

## D14: GECToR ONNX export — script + pre-export

**Choice:** Python export script in speech-sherpa/scripts/, pre-exported models published to releases
**Alternatives:**
- Pre-export only — simpler but not reproducible
- Consumer exports — adds Python/PyTorch requirement for consumers
**Rationale:** Script ensures reproducibility. Pre-exported ONNX avoids PyTorch dependency for consumers. Export is one-time per model version. Script documents the exact export configuration (opset, dynamic axes, input shapes).
**Trade-offs:** Maintaining an export script. Pre-exported models need re-export when upstream model is updated.
**Sources:** [HF Optimum ONNX export](https://huggingface.co/docs/optimum-onnx/onnx/usage_guides/export_a_model), gotutiyan/gector architecture
**Exploration:** quick
**Status:** captured

## D15: Avatar module placement — split across blocks and blocks-ui

**Choice:** New `speech-ws` Maven module in blocks reactor for the WebSocket endpoint. Depends on speech-api (not sherpa) — implementation-agnostic. Lit web components (`<casehub-avatar>`, `<casehub-transcript>`, `<casehub-speech>`) in blocks-ui repo as a new package. Demo app in blocks-ui/examples/.
**Alternatives:**
- WebSocket in speech-sherpa — speech-sherpa is pure Java FFM (no CDI, no Quarkus); adding quarkus-websockets-next would pollute it with Quarkus dependencies. Rejected by decision review.
- All in a new `blocks-ui/` Maven module inside blocks reactor — naming collision with the existing blocks-ui TypeScript repo; inverts the dependency direction (blocks-ui is downstream of blocks, not part of it)
- WebSocket endpoint in demo app only — simpler for Tier 1 but not reusable; every consumer would need to copy the integration code
**Rationale:** speech-ws is small (endpoint, config, protocol DTOs, viseme mapping) but properly Quarkus-aware with Jandex. Depends on speech-api interfaces, not speech-sherpa — so any STT/TTS backend works. speech-sherpa stays pure Java. Lit components in blocks-ui follow platform patterns. Demo in blocks-ui/examples/ for the "clone and run" experience.
**Trade-offs:** Adds one module to the reactor. Feature spans two repos — development requires coordinating blocks and blocks-ui changes. Mitigated by a clean WebSocket protocol contract between them.
**Sources:** blocks-ui CLAUDE.md (line 78: "UI parallel to blocks"), GE-20260814-c351f2 (Quarkus reactor rejects two packaging:quarkus modules), speech-sherpa/pom.xml (pure Java, no Quarkus deps)
**Exploration:** quick
**Status:** revised (from speech-sherpa, after decision review flagged purity violation)

## D16: WebSocket protocol — binary + text frame separation

**Choice:** Use WebSocket's native frame types: binary frames for audio (PCM samples up, WAV audio down), text frames for JSON messages (transcription results, phoneme timing, control commands).
**Alternatives:**
- All-JSON with base64 audio — simpler protocol but ~33% bandwidth overhead on audio and encode/decode CPU cost
- Custom binary framing with type byte prefix — most efficient but requires custom encoder/decoder on both sides, harder to debug
**Rationale:** WebSocket spec already distinguishes binary and text frames — no custom framing needed. Browser `WebSocket.send(ArrayBuffer)` for audio, `WebSocket.send(JSON.stringify(...))` for control. Server dispatches on `@OnBinaryMessage` vs `@OnTextMessage`. Simple, browser-native, no encoding overhead.
**Trade-offs:** Must handle two frame types in both client and server. Trivial since Quarkus WebSockets Next provides separate annotations for each.
**Sources:** GE-20260813-193670 (quarkus-websockets-next package naming), GE-20260703-e4a6b0 (WebSocket auth via HttpUpgradeCheck)
**Exploration:** quick
**Status:** captured

## D17: Avatar renderer — TalkingHead (Three.js 3D)

**Choice:** Use TalkingHead (MIT license, by met4citizen) for 3D avatar rendering with viseme-based lip-sync. Map PhonemeTiming phonemes to Oculus viseme blend shapes. Bundle a free default avatar GLB model.
**Alternatives:**
- lipsync-engine SVG mouth + custom SVG face — lightweight but "a mouth and no head" looks incomplete; not a foundation to build on
- Mascotbot SDK — polished 2D with Rive rendering but commercial dependency
- CSS-only minimal face — too simple for a foundation; would need replacement
**Rationale:** TalkingHead is the most complete open-source browser avatar solution. Full 3D face with idle animations, eye blinks, head movement, expressions — looks alive. Three.js is already used in the platform (quarkmind-sc2). MIT licensed, no registration needed. Our PhonemeTiming data maps directly to its Oculus viseme system. Foundation-grade choice that Tier 2/3 build on.
**Trade-offs:** Three.js dependency (~600KB gzipped). Lives in its own blocks-ui package so doesn't bloat other components.
**Sources:** [TalkingHead GitHub](https://github.com/met4citizen/TalkingHead), [lipsync-engine GitHub](https://github.com/Amoner/lipsync-engine)
**Exploration:** deep-analysis
**Status:** captured

## D18: Transcript UX — replace in-place

**Choice:** Show partial STT results as they stream, then smoothly replace with cleaned text when the TextFilter pipeline completes. Single text area, no visual clutter.
**Alternatives:**
- Side-by-side raw/clean — transparent but doubles the space, distracting for conversation
- Diff highlights (green additions, strikethrough removals) — shows what changed but more complex to implement
**Rationale:** Conversation flow matters more than transparency about corrections. Users see their words appear in real-time (partial results), then grammar/filler corrections happen seamlessly. Diff highlights or side-by-side are better for a Tier 3 "debug mode" toggle.
**Trade-offs:** User doesn't see what was corrected. Acceptable for conversation UX; a debug/diff mode can be added in Tier 3.
**Sources:** speech-api TextFilter/CleanupConfig (destructiveness-ordered pipeline)
**Exploration:** quick
**Status:** captured

## D19: Tier 1 scope — speech pipeline only

**Choice:** Tier 1 focuses on the end-to-end speech loop: mic → STT → cleanup → LLM response (via AgentProvider) → TTS → phoneme timing → lip-sync. Social cognition orchestrators (personality, mood, drives, mental models) deferred to Tier 2 with separate issues for each.
**Alternatives:**
- Full social cognition stack from day one — significantly larger scope, may not fit Tier 1
- Personality only (PersonalityEvolutionOrchestrator) — middle ground but still adds scope
**Rationale:** Getting the full speech loop working end-to-end is the Tier 1 goal ("it works"). Social cognition is purely additive — inject orchestrators, call tick()/currentState(), feed into prompt assembly. The WebSocket endpoint's prompt assembly must be a composable step (not hardcoded) so wiring is clean. Architecture doesn't change when cognition is added. Issues created for Tier 2 (personality, mood, drives, mental models, narrative, strategy) and Tier 3 (polished UX).
**Trade-offs:** Avatar has no personality in Tier 1 — responses are generic LLM output.
**Sources:** Epic #194 (Tier navigation table), blocks social cognition packages
**Exploration:** quick
**Status:** captured

## D20: Demo app — blocks-ui/examples/

**Choice:** Thin Quarkus app in blocks-ui/examples/avatar-demo/ that depends on both blocks (speech-sherpa WebSocket) and blocks-ui (avatar Lit components). Integration tests live here.
**Alternatives:**
- Separate demo repo — another repo to maintain, overkill
- Example in blocks repo — blocks-ui components would need Maven SNAPSHOT coordination
**Rationale:** blocks-ui/examples/ is the natural home — blocks-ui already depends on blocks via Maven SNAPSHOT. Examples get aggregated into casehub/examples/. The demo app is a Quarkus application that serves the Lit components from META-INF/resources/ and provides the WebSocket endpoint via classpath inclusion.
**Trade-offs:** Requires blocks to be installed to local Maven repo first (`mvn install -DskipTests` in blocks before building the demo).
**Sources:** blocks-ui CLAUDE.md (Frontend Dependencies section — Maven SNAPSHOT pattern)
**Exploration:** quick
**Status:** captured

## D21: Testing strategy — meaningful tests at each level

**Choice:** Three test levels, each independently meaningful:
1. **blocks (speech-sherpa)**: JUnit 5 + Mockito. Mock speech services, test WebSocket message protocol (JSON serialization, binary frame handling), test phoneme-to-viseme mapping, test prompt assembly pipeline. No Quarkus container.
2. **blocks-ui (avatar package)**: vitest. Mock WebSocket connection, test Lit component rendering, test TalkingHead integration (viseme scheduling from PhonemeTiming data), test transcript streaming updates.
3. **blocks-ui/examples/ (demo)**: @QuarkusTest + Playwright. End-to-end: browser connects WebSocket, sends audio, receives transcription + TTS + phoneme timing, avatar lip-syncs. Proves the full pipeline.
**Alternatives:**
- Test only at integration level — misses unit-level regressions, slow feedback loop
- Test only at unit level — misses protocol compatibility between repos
**Rationale:** Each level catches different classes of bugs. Unit tests in blocks verify protocol correctness without a browser. Unit tests in blocks-ui verify rendering without a server. Integration tests verify the contract between them.
**Trade-offs:** Three test suites to maintain. But each is focused and fast — only the integration tests are slow.
**Sources:** blocks CLAUDE.md (Testing section — plain JUnit 5 with Mockito), blocks-ui CLAUDE.md (Build Commands — yarn test)
**Exploration:** quick
**Status:** captured
