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
