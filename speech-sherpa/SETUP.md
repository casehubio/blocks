# speech-sherpa Setup Guide

## Prerequisites

- JDK 22+ (FFM/Panama API is stable from JDK 22)
- sherpa-onnx native library v1.13.6
- Model files (Whisper for STT, VITS/Piper for TTS)

## Quick Start (Local Development)

### 1. Install the native library

Download the shared library for your platform from the
[sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.6):

| Platform | Asset |
|----------|-------|
| macOS ARM64 | `sherpa-onnx-v1.13.6-osx-arm64-shared-lib.tar.bz2` |
| macOS x64 | `sherpa-onnx-v1.13.6-osx-x64-shared-lib.tar.bz2` |
| Linux x64 | `sherpa-onnx-v1.13.6-linux-x86_64-shared-cpu-lib.tar.bz2` |
| Linux ARM64 | `sherpa-onnx-v1.13.6-linux-aarch64-shared-cpu-lib.tar.bz2` |

Extract to the local cache directory:

```bash
# macOS ARM64 example
mkdir -p ~/.casehub/native/sherpa-onnx/1.13.6/osx-arm64
tar xjf sherpa-onnx-v1.13.6-osx-arm64-shared-lib.tar.bz2
cp sherpa-onnx-v1.13.6-osx-arm64-shared-lib/lib/*.dylib \
   ~/.casehub/native/sherpa-onnx/1.13.6/osx-arm64/
```

The library loads in two tiers:
1. System library path (`java.library.path`) — for system-wide installs
2. `~/.casehub/native/sherpa-onnx/{version}/{platform}/` — for user-local installs

Override with `-Dsherpa.native.dir=/path/to/libs`.

### 2. Download models

**STT (Whisper):**
```bash
# Whisper tiny (~39MB) — fastest, good for testing
curl -LO https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2
tar xjf sherpa-onnx-whisper-tiny.tar.bz2
```

Model sizes: tiny (39MB), base (141MB), small (461MB), medium (1.5GB).

**TTS (VITS/Piper):**
```bash
# English VITS model
curl -LO https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2
tar xjf vits-piper-en_US-amy-low.tar.bz2
```

### 3. Run

```java
var config = SherpaConfig.defaults(Path.of("/path/to/sherpa-onnx-whisper-tiny"));
var stt = new SherpaOnnxSpeechToText(config);

TranscriptionResult result = stt.transcribe(
    Path.of("audio.wav"),
    TranscriptionOptions.defaults()
);
System.out.println(result.text());
```

JVM flag required: `--enable-native-access=ALL-UNNAMED` (or the module name).

### 4. Run integration tests

```bash
mvn test -pl speech-sherpa \
    -Dsherpa.model.dir=/path/to/sherpa-onnx-whisper-tiny \
    -Dsherpa.tts.model.dir=/path/to/vits-piper-en_US-amy-low
```

## Automated Provisioning Design

The long-term goal: consumers add the Maven dependency and it works — no manual install.

### Architecture

```
SherpaLibrary.load()
  ├─ Tier 1: System path (SymbolLookup.libraryLookup by name)
  ├─ Tier 2: Local cache (~/.casehub/native/sherpa-onnx/{version}/{platform}/)
  └─ Tier 3: Auto-download from permanent URL → cache (future)
```

### Tier 3 Implementation (not yet built)

Auto-download on first use:

1. Detect platform: `os.name` + `os.arch` → `osx-arm64`, `linux-x86_64`, etc.
2. Download from permanent URL to `~/.casehub/native/sherpa-onnx/{version}/{platform}/`
3. Extract (tar.bz2 for official releases, or zip for casehubio-hosted)
4. Load from extracted path

**Download sources (in priority order):**
- casehubio GitHub Packages — repackaged as platform-specific JARs or zips
- k2-fsa GitHub releases — permanent URLs, tar.bz2 format

**Model provisioning:**
- Models are separate from the native library (39MB–1.5GB)
- Auto-download smallest model (whisper-tiny) on first STT call
- Cache at `~/.casehub/models/sherpa-onnx/{model-name}/`
- Configurable via `SherpaConfig.modelDir()`

### Bundling as Maven Artifacts

For true zero-install, publish platform-specific JARs to GitHub Packages:

```
casehub-blocks-speech-sherpa-native-osx-arm64.jar
casehub-blocks-speech-sherpa-native-osx-x64.jar
casehub-blocks-speech-sherpa-native-linux-x64.jar
casehub-blocks-speech-sherpa-native-linux-arm64.jar
```

Each JAR contains:
```
META-INF/native/{platform}/libonnxruntime.dylib    (~27MB)
META-INF/native/{platform}/libsherpa-onnx-c-api.dylib  (~4MB)
```

Consumer adds the platform-specific dependency:
```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-speech-sherpa-native-osx-arm64</artifactId>
    <version>${project.version}</version>
    <scope>runtime</scope>
</dependency>
```

At runtime, `SherpaLibrary` extracts from the classpath JAR to a temp dir and loads.
This is how SQLite JDBC, Netty native transports, and DJL bundle native libs.

## CI/CD (GitHub Actions)

### Unit tests (no native lib needed)

The unit tests (WAV I/O, config validation, parameter checks) run without
sherpa-onnx. CI just needs JDK 22+:

```yaml
- name: Test speech-sherpa (unit)
  run: mvn test -pl speech-sherpa -am
```

### Integration tests (with native lib)

Add a CI job that installs sherpa-onnx and downloads models:

```yaml
speech-integration:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4

    - uses: actions/setup-java@v4
      with:
        distribution: oracle
        java-version: 22

    - name: Install sherpa-onnx native lib
      run: |
        mkdir -p ~/.casehub/native/sherpa-onnx/1.13.6/linux-x64
        curl -sL https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.6/sherpa-onnx-v1.13.6-linux-x86_64-shared-cpu-lib.tar.bz2 | \
          tar xj --strip-components=2 -C ~/.casehub/native/sherpa-onnx/1.13.6/linux-x64/

    - name: Download Whisper model
      run: |
        curl -sL https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2 | \
          tar xj -C /tmp/

    - name: Integration tests
      run: |
        mvn test -pl speech-sherpa -am \
          -Dsherpa.model.dir=/tmp/sherpa-onnx-whisper-tiny

    - name: Cache native lib
      uses: actions/cache@v4
      with:
        path: ~/.casehub/native/sherpa-onnx
        key: sherpa-onnx-1.13.6-linux-x64
```

### macOS ARM64 CI

For Apple Silicon testing (Metal acceleration):

```yaml
speech-integration-macos:
  runs-on: macos-latest  # ARM64 runners
  steps:
    # Same steps, use osx-arm64 tarball
```

## Version Compatibility

The FFM struct offsets are derived from sherpa-onnx v1.13.6's `c-api.h`.
Adding model types to future versions changes the `OfflineModelConfig` struct
size — but since we allocate 4096 bytes zero-filled and our target fields
(whisper, tokens, num_threads, provider) are at the beginning of the struct,
minor version bumps are unlikely to break the binding. Major struct
reorganizations would require updating `SherpaLayouts`.

Validate compatibility: load the library and call `SherpaOnnxCreateOfflineRecognizer`
with a known config. If it returns non-NULL, the layout is correct.
