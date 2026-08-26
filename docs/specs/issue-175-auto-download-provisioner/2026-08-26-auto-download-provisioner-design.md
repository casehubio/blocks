# Auto-Download Provisioner — Native Library + Model

> **Issue:** casehubio/blocks#175
> **Date:** 2026-08-26
> **Status:** Design
> **Parent:** casehubio/blocks#194 (Talking Avatar Tier 1), casehubio/blocks#174 (Zero-Install Speech)

## 1. Problem Statement

Speech capabilities require a native library (~31MB sherpa-onnx C API) and at least one model (~39MB whisper-tiny for STT). Currently both must be manually downloaded and placed in specific directories before speech works. This is the biggest barrier to adoption — users need 3 manual setup steps before `SpeechToTextService` returns a result.

The goal: `SherpaOnnxSpeechToText.withDefaults()` just works, downloading what it needs automatically at construction time.

## 2. Design

### 2.1 Provisioner Class

A single package-private `Provisioner` class in `io.casehub.blocks.speech.sherpa` with two entry points:

```java
final class Provisioner {
    static Path ensureNativeLibrary()       // returns cache dir containing native libs
    static Path ensureModel(String name)    // returns model dir
}
```

Both share the same internal download + verify + extract pipeline:

1. Clean up orphaned temp dirs in parent (`".provisioning-*"` pattern) from prior crashes
2. Check if target directory exists → return immediately if so
3. Acquire in-process lock (`synchronized` on per-path key) then `FileLock` on `{parentDir}/.provisioning.lock`
   - `FileLock` handles cross-process safety (kernel auto-releases on crash)
   - `synchronized` handles in-process safety (POSIX `FileLock` is per-process, not per-thread)
   - After acquiring both locks, re-check if target dir exists (another thread/process may have completed)
4. Download archive to temp directory (sibling of target, same filesystem) via `java.net.http.HttpClient` with `BodyHandlers.ofInputStream()` (streaming — per GE-20260630-e18bed). HttpClient configured with `connectTimeout(30s)`.
5. Verify SHA-256 checksum against embedded known-good hash (per D5)
6. Extract via `ProcessBuilder` + system `tar xjf` with 5-minute timeout (per D1)
7. `Files.move(tempDir, targetDir, ATOMIC_MOVE)` — atomic rename (same filesystem guaranteed)
8. Release `FileLock`, close `FileChannel`

On transient network failure: single retry after 2s delay before giving up.

Download base URL is configurable via system property `-Dcasehub.speech.download-url` — replaces the GitHub base URL (`https://github.com/k2-fsa/sherpa-onnx/releases/download/`); path suffixes (`v{VERSION}/`, `asr-models/`) are always appended.

### 2.2 Download URLs

Native library URLs follow the k2-fsa GitHub release asset pattern. Must download the `-shared-lib.tar.bz2` assets (C API), NOT `-native-lib-*.jar` (JNI) — per GE-20260826-3608ec.

```
https://github.com/k2-fsa/sherpa-onnx/releases/download/v{VERSION}/{ASSET_NAME}
```

Platform-to-asset mapping (version 1.13.6):

| Platform ID | Asset name | SHA-256 |
|-------------|-----------|---------|
| `osx-arm64` | `sherpa-onnx-v1.13.6-osx-arm64-shared-lib.tar.bz2` | (computed at build time) |
| `osx-x64` | `sherpa-onnx-v1.13.6-osx-x86_64-shared-lib.tar.bz2` | (computed at build time) |
| `linux-x64` | `sherpa-onnx-v1.13.6-linux-x86_64-shared-cpu-lib.tar.bz2` | (computed at build time) |
| `win-x64` | `sherpa-onnx-v1.13.6-win-x64-shared-MD-Release-lib.tar.bz2` | (computed at build time) |

Note: the asset naming is inconsistent across platforms — `osx-arm64` matches our platform ID but Linux uses `x86_64` (not `x64`) and has a `-cpu-` suffix, Windows has `-MD-Release-`. This is a static lookup table, not a pattern.

Model download URLs:

| Model | Asset name | SHA-256 |
|-------|-----------|---------|
| `sherpa-onnx-whisper-tiny` | `sherpa-onnx-whisper-tiny.tar.bz2` | (computed at build time) |

Model URL base: `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/`

### 2.3 Checksum Verification

SHA-256 hashes for each platform's archive are embedded as constants in `Provisioner`. After downloading the archive (before extraction), compute the SHA-256 of the downloaded file and compare against the embedded hash.

On mismatch: delete the downloaded file and throw `SherpaException` with both expected and actual hashes for diagnosis. This prevents loading tampered native code into the JVM.

Hashes must be updated when bumping the sherpa-onnx version — same cadence as updating `SherpaLayouts` byte offsets and `SherpaLibrary.VERSION`.

### 2.4 Cache Directories

```
~/.casehub/
  native/
    sherpa-onnx/
      .provisioning.lock          ← FileLock for cross-process safety
      1.13.6/
        osx-arm64/                ← native lib cache (existing Tier 2 location)
          libonnxruntime.dylib
          libsherpa-onnx-c-api.dylib
  models/
    sherpa-onnx/
      .provisioning.lock          ← FileLock for model downloads
      sherpa-onnx-whisper-tiny/   ← model cache (new)
        tiny-encoder.onnx
        tiny-decoder.onnx
        tiny-tokens.txt
```

The native lib cache location is already defined by `SherpaLibrary.defaultCacheDir()`. The model cache is new: `~/.casehub/models/sherpa-onnx/{model-name}/`.

### 2.5 Integration Points

**SherpaLibrary.load() — Tier 2 fix + Tier 3:**

The existing `resolveNativeDir()` has a version fallback that searches for the lexicographically latest version subdirectory. With Tier 3, this fallback is dangerous — it can load mismatched native libraries against the wrong `SherpaLayouts` byte offsets, causing SIGSEGV. Remove the version fallback: Tier 2 checks only `defaultCacheDir()` for the exact version. Tier 3 provides the safety net the fallback was designed to be.

```java
static SherpaLibrary load() {
    // Tier 1: system library path (unchanged)
    // Tier 2: local cache — exact version only (remove version fallback)

    // Tier 3: auto-download
    if (Provisioner.isAutoDownloadEnabled()) {
        Path cacheDir = Provisioner.ensureNativeLibrary();
        // load from cacheDir (same as Tier 2)
    }

    throw new UnsatisfiedLinkError(...);
}
```

**SherpaConfig — no-arg defaults():**

```java
public static SherpaConfig defaults() {
    return new SherpaConfig(Provisioner.defaultModelDir(), 2, "cpu", null, null);
}
```

`isAutoDownloadEnabled()` and `defaultModelDir()` live in `Provisioner`, not `SherpaConfig`. SherpaConfig stays a pure data record.

**SherpaOnnxSpeechToText — factory method with provisioning:**

```java
public static SherpaOnnxSpeechToText withDefaults() {
    SherpaLibrary lib = SherpaLibrary.load();  // native lib first (Tier 3 downloads if needed)
    SherpaConfig config = SherpaConfig.defaults();
    if (Provisioner.isAutoDownloadEnabled() && !Files.isDirectory(config.modelDir())) {
        Provisioner.ensureModel("sherpa-onnx-whisper-tiny");
    }
    return new SherpaOnnxSpeechToText(config, lib);
}
```

Native lib downloads first (via `SherpaLibrary.load()` → Tier 3). If that fails, the model download is skipped — no wasted bandwidth on a 39MB model that can't be used without the native library.

Provisioning happens at construction time via `withDefaults()`, not inside `transcribe()`. The `transcribe()` method stays a pure computation — no network I/O, no filesystem mutation. Consumers using the existing constructor with an explicit `modelDir` bypass auto-download entirely. Callers should cache the returned instance (see #168 for recognizer caching).

### 2.6 Opt-Out

Auto-download is **on by default** (zero-install story). Disable via:

- System property: `-Dcasehub.speech.auto-download=false`
- When disabled, `SherpaLibrary.load()` throws `UnsatisfiedLinkError` as before (Tier 1+2 only)
- When disabled, `withDefaults()` throws `SherpaException` if model dir is missing

Override download URL via:

- System property: `-Dcasehub.speech.download-url=https://mirror.internal/sherpa-onnx/`
- Replaces the GitHub base URL for both native libs and models

### 2.7 Progress Reporting

Downloads are ~31MB (native) and ~39MB (model). For first-use experience:

- Use `System.Logger` (JDK 9+, always available since we require JDK 22+) at `INFO` level:
  ```
  [casehub-speech] Downloading sherpa-onnx native library (31 MB)...
  [casehub-speech] Downloading sherpa-onnx-whisper-tiny model (39 MB)...
  [casehub-speech] Done.
  ```
- No progress bar — single message before download, single message after. The download takes 5-10 seconds on a typical connection; a progress bar adds complexity for a one-time operation.

### 2.8 Error Handling

| Failure | Behaviour |
|---------|-----------|
| Network unreachable | `SherpaException` with message naming the URL and suggesting manual download |
| HTTP 404 (asset not found for platform) | `SherpaException` with platform ID and URL |
| Checksum mismatch | `SherpaException` with expected vs actual SHA-256; downloaded file deleted |
| Download interrupted | Clean up temp dir, release lock, propagate exception |
| `tar` not found | `SherpaException` suggesting manual installation |
| `tar` extraction fails | `SherpaException` with tar stderr output |
| Disk full | Let `IOException` propagate naturally |

### 2.9 tar.bz2 Extraction

The tarball archives contain a top-level directory (e.g., `sherpa-onnx-v1.13.6-osx-arm64-shared-lib/lib/`). The extraction uses `--strip-components` to flatten:

```bash
tar xjf archive.tar.bz2 --strip-components=2 -C /target/dir/
```

The `--strip-components=2` removes both the release directory and the `lib/` subdirectory, placing `libsherpa-onnx-c-api.dylib` and `libonnxruntime.dylib` directly in the target directory — matching the Tier 2 expected layout.

For models, `--strip-components=1` removes just the top-level directory.

### 2.10 Concurrency

**In-process:** Two layers of synchronization:
- **Native lib:** `SherpaLibrary.load()` already uses DCL with `volatile INSTANCE`. `Provisioner.ensureNativeLibrary()` runs inside the `synchronized` block — only one thread downloads.
- **Model:** `Provisioner.ensureModel()` uses its own `synchronized` block (keyed on model name). POSIX `FileLock` is per-process, not per-thread — `synchronized` is needed for in-process safety.

**Cross-process:** `FileLock` on `.provisioning.lock` in the parent directory. `FileChannel.tryLock()` returns null if another process holds the lock → fall back to blocking `FileChannel.lock()`, then re-check if target directory was created by the other process. Lock auto-releases on process crash (kernel reclaims).

### 2.11 Testing Strategy

| Test | What it verifies | Native lib needed? |
|------|-----------------|-------------------|
| `ProvisionerTest.platformAssetName_*` | URL construction for each platform | No |
| `ProvisionerTest.checksumVerification_match` | SHA-256 match passes | No |
| `ProvisionerTest.checksumVerification_mismatch` | SHA-256 mismatch throws, cleans up | No |
| `ProvisionerTest.fileLockPreventsParallelDownload` | Second thread waits on lock | No |
| `ProvisionerTest.tempDirIsSiblingOfTarget` | Temp dir on same filesystem as target | No |
| `ProvisionerTest.downloadAndExtract_nativeLib` | Full end-to-end (downloads real archive) | Integration only |
| `ProvisionerTest.downloadAndExtract_model` | Full end-to-end (downloads real model) | Integration only |
| `ProvisionerTest.autoDownloadDisabled` | System property disables Tier 3 | No |
| `ProvisionerTest.networkFailure_clearsState` | Temp dir cleaned up, lock released on error | No (mocked HTTP) |
| `ProvisionerTest.customDownloadUrl` | URL override via system property | No |
| `SherpaConfigTest.defaultsNoArg` | No-arg `defaults()` returns correct model path | No |
| `SherpaOnnxSpeechToTextTest.withDefaults_provisionsModel` | Factory method calls ensureModel | No (mocked) |

Unit tests mock the HTTP client. Integration tests (CI-only, `@EnabledIf`) test real downloads.

## 3. Files Changed

| File | Change |
|------|--------|
| `Provisioner.java` (new) | Download + verify + extract logic, FileLock handling, URL mapping, SHA-256 constants, `isAutoDownloadEnabled()`, `defaultModelDir()` |
| `SherpaLibrary.java` | Add Tier 3 call to `Provisioner.ensureNativeLibrary()` in `load()`, remove version fallback from `resolveNativeDir()` |
| `SherpaConfig.java` | Add no-arg `defaults()` overload (delegates to `Provisioner.defaultModelDir()`) |
| `SherpaOnnxSpeechToText.java` | Add `withDefaults()` factory method — native lib first, then model provisioning |
| `ProvisionerTest.java` (new) | Unit + integration tests for provisioning |
| `SherpaConfigTest.java` | Tests for no-arg `defaults()` |

## 4. Not In Scope

- TTS model auto-download — TTS models vary by voice/language; no single default. Deferred to #177 (TTS model auto-detection).
- Streaming model auto-download — streaming uses a different model (Zipformer). Deferred to #183.
- Maven-packaged native JARs — separate issue #176.

## References

- [SherpaLibrary.java] — existing Tier 1+2 loading mechanism
- [SherpaConfig.java] — existing config record
- [docs/research/2026-08-26-speech-spi-research.md §11] — provisioning tier architecture
- [GE-20260826-3608ec] — JNI vs C API library gotcha
- [GE-20260630-e18bed] — streaming download pattern
- [GE-20260420-b9c06c] — Java stdlib doesn't support bzip2
- [casehubio/blocks#174] — parent epic (Zero-Install Speech)
- [casehubio/blocks#194] — Tier 1 epic (Talking Avatar)
- Decision review R1-10 — SPI contract violation (moved provisioning out of transcribe())
- Decision review R1-14, R1-15 — FileLock + atomic sibling rename (replaced marker file)
- Decision review R1-18 — SHA-256 checksum verification
- Spec review R1-04 — remove version fallback in resolveNativeDir()
- Spec review R1-05 — in-process synchronization for model downloads
- Spec review R1-06 — download native lib before model
- Spec review R1-09 — download timeout
- Spec review R1-10 — move isAutoDownloadEnabled() to Provisioner
