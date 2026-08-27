# Avatar Web Component — Tier 1 Design

> **Issue:** casehubio/blocks#195
> **Date:** 2026-08-27
> **Status:** Design
> **Parent:** casehubio/blocks#194 (Critical Path: Talking Avatar with Personality)

## 1. Problem Statement

CaseHub needs a browser-based talking avatar that users can speak to and receive spoken responses from — with real-time lip-sync, streaming transcription, and grammar-corrected text. This is the runnable demo that proves the speech pipeline works end-to-end.

Tier 1 focuses on the speech loop only: mic → STT → cleanup → LLM → TTS → lip-sync. Social cognition orchestrators (personality, mood, drives, mental models) are deferred to Tier 2 with separate issues.

## 2. Architecture

```
blocks repo (Java)                          blocks-ui repo (TypeScript)
┌─────────────────────────┐                ┌──────────────────────────────┐
│ speech-ws (new module)  │                │ packages/avatar/             │
│   SpeechWebSocket       │                │   <casehub-avatar>           │
│   AvatarConfig          │                │   <casehub-transcript>       │
│   VisemeMapping         │                │   <casehub-speech>           │
│   Protocol DTOs         │◄──── WSS ────►│   TalkingHead integration    │
│                         │                │                              │
│ Composes:               │                │ examples/avatar-demo/        │
│   speech-api SPIs       │                │   Quarkus app (integration)  │
│   AgentProvider (LLM)   │                └──────────────────────────────┘
└─────────────────────────┘
```

### Module placement (D15, revised)

| Artifact | Repo | Module/Package | Why here |
|----------|------|----------------|----------|
| WebSocket endpoint, protocol DTOs, viseme mapping | blocks | `speech-ws` (new Maven module) | Composes speech-api SPIs + AgentProvider. Quarkus-aware with Jandex. Depends on speech-api (not sherpa) — any STT/TTS backend works. |
| `<casehub-avatar>`, `<casehub-transcript>`, `<casehub-speech>` | blocks-ui | `packages/avatar/` | All shared Lit web components live in blocks-ui. Published as Maven SNAPSHOT with META-INF/resources/. |
| Demo app | blocks-ui | `examples/avatar-demo/` | Thin Quarkus app pulling both. Integration tests. Aggregated into casehub/examples/. |

### Dependency direction

```
blocks-ui/examples/avatar-demo (Quarkus app)
  ├── blocks-ui/packages/avatar (Lit components, META-INF/resources/)
  ├── casehub-blocks-speech-ws (WebSocket endpoint, CDI bean)
  ├── casehub-blocks-speech-sherpa (runtime STT/TTS implementation)
  └── casehub-blocks-speech-api (SPIs — transitive)
```

speech-ws depends on speech-api interfaces only. Consumer chooses the runtime implementation (sherpa, cloud, etc.).

## 3. WebSocket Protocol (D16)

Native binary + text frame separation. No custom framing.

### Client → Server

**Text frames (JSON):**

```json
{"type": "start", "sampleRate": 16000}
```
Begin recording. Server creates a `RecognitionStream`.

```json
{"type": "stop"}
```
End recording. Server finalizes the stream and begins processing.

**Binary frames:**

Raw PCM audio samples — 32-bit float, mono, at the sample rate declared in `start`. Sent as `ArrayBuffer` from Web Audio API's `AudioWorkletProcessor`.

### Server → Client

**Text frames (JSON):**

```json
{"type": "partial", "text": "I was thinking about"}
```
Streaming STT partial result. Displayed as-you-speak in the transcript.

```json
{"type": "transcript", "text": "I was thinking about the project."}
```
Final cleaned transcript (after TextFilter pipeline). Replaces the partial text in the transcript.

```json
{"type": "response", "text": "That sounds interesting. Tell me more about it."}
```
LLM response text. Displayed in the transcript as the avatar's response.

```json
{
  "type": "phonemes",
  "data": [
    {"viseme": "DD", "startMs": 0, "endMs": 80},
    {"viseme": "aa", "startMs": 80, "endMs": 160},
    {"viseme": "SS", "startMs": 160, "endMs": 220}
  ]
}
```
Viseme timing for lip-sync. Sent before the audio so the client can prepare the animation timeline. Uses Oculus viseme IDs (see §4).

```json
{"type": "error", "message": "STT service unavailable"}
```
Error messages.

**Binary frames:**

TTS audio — WAV format (16-bit PCM, mono). Client plays via Web Audio API and synchronizes lip-sync to the viseme timeline.

### Conversation flow

```
Client                          Server
  │── start ──────────────────────►│
  │── binary (PCM chunks) ────────►│
  │◄──────────── partial ──────────│  (streaming, multiple)
  │── stop ───────────────────────►│
  │◄──────────── transcript ───────│  (final, cleaned)
  │                                │  ... LLM generates response ...
  │◄──────────── response ─────────│  (LLM text)
  │◄──────────── phonemes ─────────│  (viseme timing)
  │◄──────────── binary (WAV) ─────│  (TTS audio)
  │                                │
  │── start ──────────────────────►│  (next turn)
```

## 4. Viseme Mapping (phoneme → Oculus viseme)

TalkingHead uses 15 Oculus visemes. Our VITS TTS produces IPA phonemes via espeak-ng. The `VisemeMapping` class in speech-ws maps IPA → Oculus viseme ID.

| Oculus Viseme | IPA Phonemes | Description |
|---------------|-------------|-------------|
| `sil` | (silence) | Mouth closed |
| `PP` | p, b, m | Bilabial |
| `FF` | f, v | Labiodental |
| `TH` | θ, ð | Dental |
| `DD` | t, d, n, l | Alveolar |
| `kk` | k, g, ŋ | Velar |
| `CH` | tʃ, dʒ, ʃ, ʒ | Postalveolar |
| `SS` | s, z | Sibilant |
| `nn` | n, ŋ | Nasal (overlap with DD/kk) |
| `RR` | ɹ, r | Rhotic |
| `aa` | ɑ, æ, ʌ, a | Open vowels |
| `E` | ɛ, e, ə | Mid vowels |
| `I` | ɪ, i | Close front vowels |
| `O` | ɔ, o, ɒ | Back rounded vowels |
| `U` | ʊ, u, w | Close back vowels |

The mapping is a static `Map<String, String>` — IPA phoneme string to Oculus viseme ID. Unknown phonemes fall back to `sil`.

speech-ws performs the mapping server-side so the WebSocket protocol sends viseme IDs directly — the client doesn't need IPA knowledge.

## 5. Server-Side Components (speech-ws module)

### 5.1 SpeechWebSocket

```java
@WebSocket(path = "/ws/avatar")
public class SpeechWebSocket {
    @Inject StreamingSpeechToTextService sttService;
    @Inject TextToSpeechService ttsService;
    @Inject CleanupConfig cleanupConfig;
    @Inject Instance<AgentProvider> agentProvider;

    // Per-connection state via UserData
    // - RecognitionStream (created on "start", closed on "stop")
    // - Conversation history (for LLM context)
}
```

Annotations:
- `@OnTextMessage` — dispatches on `type` field: start, stop
- `@OnBinaryMessage` — feeds PCM samples to `RecognitionStream.acceptSamples()`
- `@OnOpen` / `@OnClose` — lifecycle management

The `start` message creates a `RecognitionStream` via `sttService.startStream()`. Binary frames feed audio into it. A virtual thread polls `partialResult()` every 100ms and sends partial transcription messages when the text changes. When `isEndpointDetected()` returns true or `stop` is received, `finalResult()` is called, the cleanup pipeline runs, and the cleaned transcript is sent.

LLM response generation uses `AgentProvider` (from platform-agent-api, injected as `Instance<AgentProvider>` — optional). If no `AgentProvider` is available, the endpoint sends an error message and skips response generation. The prompt assembly is a composable step — a `PromptAssembler` functional interface — so social cognition can be injected in Tier 2 without changing the endpoint.

```java
@FunctionalInterface
public interface PromptAssembler {
    String assemble(String userMessage, List<ConversationTurn> history);
}
```

Default implementation: simple system prompt + conversation history. Tier 2 replaces with personality/mood/drive-enriched assembly.

### 5.2 AvatarConfig

```java
@ConfigMapping(prefix = "casehub.avatar")
public interface AvatarConfig {
    @WithDefault("16000")
    int sampleRate();

    @WithDefault("2")
    int maxDestructiveness();  // TextFilter pipeline threshold

    Optional<String> systemPrompt();  // LLM system prompt override
}
```

### 5.3 VisemeMapping

Static utility class: `String toViseme(String ipaPhoneme)`. Returns Oculus viseme ID.

Also provides `List<VisemeFrame> convert(List<PhonemeTiming> phonemes)` — converts the full PhonemeTiming list to viseme frames with timing, collapsing consecutive identical visemes.

```java
public record VisemeFrame(String viseme, long startMs, long endMs) {}
```

### 5.4 Protocol DTOs

Records for JSON serialization of WebSocket messages:
- `AvatarMessage` — sealed interface with variants for each message type
- `StartMessage`, `StopMessage` — client commands
- `PartialMessage`, `TranscriptMessage`, `ResponseMessage`, `PhonemeMessage`, `ErrorMessage` — server responses

Using gson (already in speech-sherpa) for JSON. No additional dependency.

### 5.5 Module dependencies

```xml
<dependencies>
    <dependency>casehub-blocks-speech-api</dependency>           <!-- SPIs -->
    <dependency>casehub-platform-agent-api (provided)</dependency> <!-- AgentProvider -->
    <dependency>quarkus-websockets-next</dependency>              <!-- WebSocket -->
    <dependency>com.google.code.gson:gson</dependency>            <!-- JSON -->
    <dependency>org.jspecify:jspecify</dependency>                 <!-- Annotations -->
</dependencies>
```

JDK target: 17 (no FFM needed — this module uses SPIs, not native bindings).

## 6. Client-Side Components (blocks-ui avatar package)

### 6.1 `<casehub-avatar>`

The main component. Composes TalkingHead for 3D rendering and manages the WebSocket connection.

```html
<casehub-avatar
  ws-url="/ws/avatar"
  avatar-url="/avatar/default-avatar.glb">
</casehub-avatar>
```

**Properties:**
- `wsUrl` — WebSocket endpoint URL (default: `/ws/avatar`)
- `avatarUrl` — GLB model URL (default: bundled avatar)

**Internal structure:**
- Canvas element for Three.js/TalkingHead rendering
- Embedded `<casehub-transcript>` for conversation display
- Embedded `<casehub-speech>` for mic controls

**Lifecycle:**
- `connectedCallback()` — initializes TalkingHead, opens WebSocket
- `disconnectedCallback()` — closes WebSocket, disposes TalkingHead

**Lip-sync flow:**
1. Receive `phonemes` message → build viseme timeline
2. Receive binary WAV → decode with Web Audio API
3. Start audio playback + drive TalkingHead visemes in sync via `requestAnimationFrame`

### 6.2 `<casehub-transcript>`

Scrolling conversation display with replace-in-place updates (D18).

```html
<casehub-transcript></casehub-transcript>
```

**State:**
- `turns: ConversationTurn[]` — array of {role: 'user'|'avatar', text: string, status: 'partial'|'final'}
- New user turn added on `start`, updated with `partial` messages, finalized with `transcript`
- Avatar turn added with `response` message

**Rendering:**
- Chat-bubble style — user on right, avatar on left
- Partial text shown in lighter style, replaced smoothly on final
- Auto-scrolls to latest

### 6.3 `<casehub-speech>`

Mic capture controls using Web Audio API.

```html
<casehub-speech></casehub-speech>
```

**Behaviour:**
- Push-to-talk button (hold to record, release to stop)
- Audio level indicator (VU meter from `AnalyserNode`)
- `AudioWorkletProcessor` captures PCM samples, sends as binary frames
- Mic permission request on first interaction

**AudioWorklet pipeline:**
```
navigator.mediaDevices.getUserMedia({audio: true})
  → MediaStreamSource
  → AudioWorkletProcessor (captures Float32Array chunks)
  → WebSocket.send(chunk.buffer)  // binary frame
```

### 6.4 TalkingHead integration

The avatar package depends on `talkinghead` (npm). TalkingHead is instantiated with a canvas element and loaded with the avatar GLB model.

Viseme driving:
- Receive `phonemes` JSON → parse into timeline array
- On audio playback start, advance through the timeline using `requestAnimationFrame`
- Call TalkingHead's viseme/blend shape API at each frame based on `audioContext.currentTime`
- When audio ends, return to `sil` (idle) viseme

### 6.5 Default avatar model

Bundle a GLB model with ARKit + Oculus viseme blend shapes in the package's static resources. Ready Player Me's free avatar creator produces compatible models — create one without registration features (direct GLB download from their API). Alternatively, use one of the sample GLB avatars included in the TalkingHead repository. Users can override via the `avatarUrl` property. The specific model is selected during implementation based on file size and visual quality.

## 7. Demo App (blocks-ui/examples/avatar-demo/)

Thin Quarkus application:
- `pom.xml` — depends on speech-ws, speech-sherpa, avatar package (via Maven SNAPSHOT)
- `application.properties` — configures speech model paths, system prompt
- `index.html` — single page importing `<casehub-avatar>`
- No custom Java code — the WebSocket endpoint auto-discovers from speech-ws via Jandex

First-run experience:
```
cd examples/avatar-demo
mvn quarkus:dev
# Browser opens http://localhost:8080
# "Downloading speech models (39MB)... done"  (Provisioner auto-download)
# Mic prompt appears, you talk, avatar responds with lip-sync
```

## 8. Testing Strategy (D21)

### 8.1 blocks (speech-ws) — JUnit 5 + Mockito

| Test | What it verifies |
|------|-----------------|
| `VisemeMappingTest` | IPA → Oculus viseme mapping correctness, unknown phoneme fallback |
| `VisemeFrameConversionTest` | PhonemeTiming list → VisemeFrame list, consecutive viseme collapsing |
| `ProtocolSerializationTest` | JSON round-trip for all message types |
| `SpeechWebSocketTest` | Message dispatch (mock speech services), conversation flow, error handling |
| `PromptAssemblerTest` | Default prompt assembly, conversation history formatting |

No Quarkus container — plain JUnit 5 with Mockito mocks for speech services and AgentProvider.

### 8.2 blocks-ui (avatar package) — vitest

| Test | What it verifies |
|------|-----------------|
| `casehub-avatar` rendering | Component mounts, canvas created, TalkingHead initialized |
| WebSocket mock | Messages dispatched correctly, binary frames handled |
| Viseme timeline | Timeline built from phonemes JSON, correct viseme at each time point |
| `casehub-transcript` | Partial → final replacement, conversation turn ordering, auto-scroll |
| `casehub-speech` | Button states (idle/recording), AudioWorklet setup |

TalkingHead mocked at the module boundary for unit tests. Integration test with real TalkingHead + mock WebSocket for visual verification.

### 8.3 Demo (avatar-demo) — @QuarkusTest + Playwright

| Test | What it verifies |
|------|-----------------|
| WebSocket connectivity | Browser connects, receives welcome/ready message |
| Audio round-trip | Send PCM samples, receive transcription + TTS audio |
| Lip-sync pipeline | Phonemes received, avatar viseme state changes during playback |

Uses garden gotchas: GE-20260620-768950 (wait for @OnOpen before sending), GE-20260813-482707 (ws:// scheme for @TestHTTPResource).

## 9. Tier 2/3 Issues to Create

Social cognition integration — one issue per orchestrator:

| Issue | Tier | What it adds |
|-------|------|-------------|
| Personality modulation | 2 | PersonalityEvolutionOrchestrator traits in prompt assembly |
| Mood-affected responses | 2 | MoodOrchestrator PAD state modulates tone and word choice |
| Drive-influenced initiation | 2 | DriveOrchestrator curiosity/affiliation triggers proactive speech |
| Mental model awareness | 2 | MentalModelOrchestrator tracks what user knows, avoids repetition |
| User model personalization | 2 | UserModelOrchestrator remembers user across sessions |
| Strategy learning | 2 | StrategyLearningOrchestrator adapts interaction style |
| Narrative identity | 3 | NarrativeOrchestrator gives avatar a coherent self-story |
| Transcript diff mode | 3 | Toggle to show raw vs cleaned text with highlights |
| Custom avatar upload | 3 | User uploads own GLB model via UI |
| Voice selection | 3 | Choose from multiple Piper TTS voices |

## 10. Scope Boundary

**In scope (Tier 1):**
- speech-ws Maven module (WebSocket endpoint, protocol, viseme mapping)
- blocks-ui avatar package (3 Lit components + TalkingHead integration)
- Demo app with integration tests
- End-to-end speech loop: mic → STT → cleanup → LLM → TTS → lip-sync
- Default avatar model bundled

**Out of scope (Tier 2/3):**
- Social cognition orchestrator integration
- Voice/avatar customization UI
- Multi-language support
- Conversation persistence
- Authentication (GE-20260703-e4a6b0 documents HttpUpgradeCheck pattern for Tier 2)

## References

- [TalkingHead](https://github.com/met4citizen/TalkingHead) — 3D avatar renderer (MIT)
- [Oculus Viseme Reference](https://developer.oculus.com/documentation/unity/audio-ovrlipsync-viseme-reference/) — viseme set
- [lipsync-engine](https://github.com/Amoner/lipsync-engine) — evaluated, not selected (D17)
- GE-20260814-c351f2 — Quarkus reactor rejects two packaging:quarkus modules
- GE-20260819-3e7715 — Quinoa dev WebSocket proxy intercepts backend WS endpoints
- GE-20260816-e89cda — Composable Lit reactive controllers pattern
- GE-20260813-193670 — quarkus-websockets-next uses plural package name
- GE-20260703-e4a6b0 — WebSockets Next ignores JAX-RS filters; use HttpUpgradeCheck
- GE-20260620-768950 — buildAsync().join() completes before @OnOpen fires
- GE-20260813-482707 — @TestHTTPResource gives http:// not ws:// for WebSocket
- GE-20260806-10d369 — blocks-ui-core EventStreamController is WebSocket-based
- speech-api SPIs: StreamingSpeechToTextService, RecognitionStream, TextToSpeechService, SynthesisResult, PhonemeTiming, TextFilter, CleanupConfig
- Epic #194 — Critical Path: Talking Avatar with Personality
- Epic #196 — Tier 2: Clean (blocked by Tier 1)
- Epic #197 — Tier 3: Polished (blocked by Tier 2)
