# Design: STT Accuracy Pipeline

**Issue:** casehubio/blocks#212
**Date:** 2026-08-28
**Branch:** (to be created)

## Problem

The avatar speech pipeline produces inaccurate transcriptions for natural speech. The streaming Zipformer model has limited vocabulary coverage — uncommon words like "limerick" are misrecognized as "relimberate." The cleanup pipeline is narrow (exact filler matching only) and stateless (no conversation context). Punctuation and grammar correction models exist but aren't wired.

## Architecture

Replace the STT engine and build a multi-stage correction pipeline. Two layers — mechanical (ONNX + pure Java, no network calls) and optional LLM polish.

```
                     ┌─────────────────────────────────┐
                     │        whisper.cpp (FFM)         │
Mic PCM ────────────►│  Pseudo-streaming Whisper        │
                     │  initial_prompt: conversation    │
                     │  vocabulary for decoder biasing  │
                     └──────────────┬──────────────────┘
                                    │ raw transcript
                     ┌──────────────▼──────────────────┐
                     │     TranscriptCorrector          │
                     │                                  │
                     │  1. SymSpell (edit distance ≤ 2) │
                     │  2. Phonetic match (Metaphone)   │
                     │  3. N-gram context ranking       │
                     └──────────────┬──────────────────┘
                                    │ corrected transcript
                     ┌──────────────▼──────────────────┐
                     │     CleanupConfig pipeline       │
                     │                                  │
                     │  4. FillerRemovalFilter (ext.)   │
                     │  5. GECToR grammar (ONNX)        │
                     │  6. PunctuationFilter (ONNX)     │
                     │  7. CasingFilter                 │
                     └──────────────┬──────────────────┘
                                    │ clean transcript
                                    ▼
                              SpeechSession
                     (LLM response → TTS → lip-sync)
                                    │
                     ┌──────────────▼──────────────────┐
                     │     Feedback loop                │
                     │  Extract terms from LLM response │
                     │  → update hotword list           │
                     │  → update SymSpell dictionary    │
                     └─────────────────────────────────┘
```

## Components

### 1. WhisperLibrary (FFM/Panama)

FFM bindings for whisper.cpp C API. Fourth binding following the established pattern (SherpaLibrary, OnnxRuntimeLibrary, EspeakLibrary).

**Key downcall handles:**

| Function | Purpose |
|----------|---------|
| `whisper_init_from_file` | Load GGML model |
| `whisper_full_params_by_strategy` | Create default params |
| `whisper_full` | Run inference on audio samples |
| `whisper_full_n_segments` | Get segment count |
| `whisper_full_get_segment_text` | Read segment text |
| `whisper_full_get_segment_t0/t1` | Segment timestamps |
| `whisper_free` | Release model |

**Params configuration:**
- `initial_prompt` — conversation vocabulary string, biases decoder toward expected words
- `language` — "en" (or auto-detect)
- `n_threads` — match CPU count
- `strategy` — `WHISPER_SAMPLING_BEAM_SEARCH` for accuracy

**Provisioning:** `Provisioner.ensureWhisperModel(modelName)` downloads GGML models from huggingface.co/ggerganov/whisper.cpp. SHA-256 verified. Models:
- `ggml-base.en.bin` (~142MB) — recommended default, good accuracy/speed balance
- `ggml-small.en.bin` (~466MB) — higher accuracy, slower
- `ggml-tiny.en.bin` (~75MB) — fastest, lower accuracy

**Module placement:** `speech-sherpa` — alongside existing FFM bindings.

### 2. WhisperSpeechToText

`StreamingSpeechToTextService` implementation using whisper.cpp. Pseudo-streaming architecture: buffers incoming audio, re-runs Whisper periodically, diffs results for partial updates.

```java
public final class WhisperSpeechToText implements StreamingSpeechToTextService, AutoCloseable {
    private final WhisperLibrary lib;
    private final MemorySegment ctx;      // whisper_context* — SHARED across streams
    private final ReentrantLock inferenceLock = new ReentrantLock();

    // Factory
    public static WhisperSpeechToText withDefaults() { ... }
    public static WhisperSpeechToText withDefaults(String modelName) { ... }
}
```

**Shared context, serialized inference:** A single `whisper_context` is created at construction (loading the model once, ~142MB for base.en) and shared across all `RecognitionStream` instances. `whisper_full()` is not thread-safe for the same context, so inference calls are serialized via `ReentrantLock`. For the single-user avatar use case, this is not a bottleneck — only one stream is active at a time.

**WhisperRecognitionStream** (inner class implementing `RecognitionStream`):
- `acceptSamples()` — appends to a bounded `float[]` buffer (max 30 seconds / 480K samples at 16kHz). If the buffer exceeds the cap, older samples are discarded (windowed approach to avoid O(n²) re-inference on unbounded buffers).
- `partialResult()` — acquires the inference lock, runs Whisper on the current buffer, diffs against previous result. Throttled to every 500ms.
- `finalResult()` — acquires lock, runs Whisper on the complete buffer with `initial_prompt` set from conversation context.
- Buffer is allocated at stream creation, released at close.

**initial_prompt integration:**
The `RecognitionStream` accepts a vocabulary hint via `TranscriptionOptions` (extended with a new `vocabularyHint` field). `SpeechSession` populates this from the conversation's hotword list.

### 3. TranscriptCorrector (compositor)

Composes pluggable `CorrectionStrategy` instances into a single `TextFilter`. Each strategy proposes candidates for unknown words; the corrector ranks all candidates and picks the best. Adding a new correction source (name detection, domain jargon, etc.) = adding a new strategy.

```java
@FunctionalInterface
public interface CorrectionStrategy {
    List<Candidate> candidates(String word, CorrectionContext context);

    record Candidate(String word, double confidence, String source) {}
}
```

```java
public record CorrectionContext(
    String previousWord,
    String nextWord,
    Set<String> conversationVocabulary
) {}
```

```java
public final class TranscriptCorrector {
    private final List<CorrectionStrategy> strategies;
    private final NgramModel ngramModel;
    private final Set<String> dictionary;
    private final Set<String> conversationVocabulary;

    public String correct(String text) { ... }
    public void addVocabulary(String... words) { ... }
}
```

**Built-in strategies (this issue):**
- `SymSpellStrategy` — edit-distance candidates (max distance 2)
- `PhoneticStrategy` — Double Metaphone sound-alike candidates

**Future strategies (separate issues):**
- `NameCorrectionStrategy` — matches against known person/place names
- `DomainJargonStrategy` — domain-specific terminology
- `AcronymStrategy` — expands/corrects acronym misrecognitions

**Correction algorithm per word:**
1. If word is in dictionary → keep (no correction needed)
2. **Collect candidates** from ALL registered strategies (union, deduplicated)
3. **Rank by n-gram context** — for each candidate, score `P(candidate | prev_word) × P(next_word | candidate)` using bigram frequencies. Highest-scoring candidate wins.
4. **Confidence threshold** — only accept correction if the best candidate's score exceeds a minimum (prevents spurious corrections on valid unusual words).

**Sub-components:**

#### 3a. SymSpellIndex

Pure Java implementation of the Symmetric Delete algorithm. Pre-computes a delete dictionary from a word frequency list at startup.

- **Dictionary source:** A pre-built English word frequency list (~80K words, ~2MB). Standard source: `frequency_dictionary_en_82_765.txt` from the SymSpell project.
- **Max edit distance:** 2 (covers ~95% of single-word ASR errors)
- **Dynamic vocabulary:** `add(word, frequency)` method for adding conversation terms at runtime
- **Lookup:** `List<Candidate> lookup(String word)` returns candidates sorted by distance then frequency

#### 3b. PhoneticIndex

Maps dictionary words to phonetic codes for sound-alike matching.

- **Algorithm:** Double Metaphone (pure Java, no native dependency). Generates primary and alternate codes per word.
- **Index structure:** `Map<String, List<String>>` — phonetic code → list of words
- **Lookup:** `List<String> lookup(String word)` — compute Metaphone code for input, return all words sharing that code
- **Built from the same dictionary as SymSpell** — one dictionary, two index structures

#### 3c. NgramModel

Bigram/trigram frequency model for contextual candidate ranking.

- **Source:** Pre-built English bigram frequencies (~50MB compressed). Standard sources: Google Ngrams subset, or COCA corpus bigrams.
- **Storage:** `LongFloatHashMap` (Eclipse Collections) or equivalent primitive map — avoids boxing overhead. Keyed by hashed bigram pairs.
- **API:** `float score(String prev, String candidate, String next)` — returns log-probability of the candidate in context
- **Fallback:** unigram frequency when bigram not found (smoothed with add-k)

### 4. Extended FillerRemovalFilter

Extends current `FillerRemovalFilter` with discourse markers and false starts.

**New patterns (in addition to existing um/uh/er/hm/ah/oh/eh/mhm):**
- Discourse markers: `\b(like|you know|I mean|basically|actually|literally|right)\b` when used as fillers (not as content words)
- False starts: repeated word sequences (`"I I went"` → `"I went"`)
- STT filler misrecognitions: `\bam\b` at sentence start (common "um" misrecognition)
- Hedge phrases: `\b(sort of|kind of)\b` when not followed by a noun

**Destructiveness:** 2 (up from 1 — discourse marker removal is more aggressive than exact filler matching)

**Context sensitivity:** Some patterns need sentence position awareness. "like" as a filler differs from "like" as a verb ("I like cats"). Heuristic: "like" preceded by a verb or "was" is a filler; "like" preceded by a subject pronoun is content.

### 5. Existing Components (wire into pipeline)

| Component | Current State | Action |
|-----------|---------------|--------|
| GectorFilter | Built, model download fails | Fix Provisioner URL or bundle model |
| PunctuationFilter | Built, not wired | Add to CleanupConfig chain |
| CasingFilter | Active | Keep as-is |

### 6. ConversationVocabulary

Session-scoped vocabulary manager. Feeds into three systems:
1. `WhisperSpeechToText` — `initial_prompt` for decoder biasing
2. `TranscriptCorrector` — dynamic SymSpell + phonetic dictionary
3. `FillerRemovalFilter` — (no direct feed, but context-aware patterns benefit from knowing expected vocabulary)

```java
public final class ConversationVocabulary {
    private final Set<String> terms = ConcurrentHashMap.newKeySet();

    public void addFromText(String text) { ... }  // extract terms
    public String asPromptHint() { ... }          // for Whisper initial_prompt
    public Set<String> terms() { ... }
}
```

**Term extraction:** Split on whitespace, keep words ≥ 4 characters, exclude stop words. Applied to:
- LLM response text (after generation)
- User transcript (after correction — feeds back corrected terms)

### 7. TranscriptionOptions Extension

Add `vocabularyHint` field to support conversation context in the STT SPI. The existing 3-arg constructor is preserved via a canonical-to-compact bridge — all existing callers continue to compile unchanged.

```java
public record TranscriptionOptions(
    String audioFormat,
    String languageHint,
    String modelSize,
    @Nullable String vocabularyHint
) {
    public TranscriptionOptions(String audioFormat, String languageHint, String modelSize) {
        this(audioFormat, languageHint, modelSize, null);
    }

    public static TranscriptionOptions defaults() {
        return new TranscriptionOptions("wav", null, "base.en", null);
    }

    public TranscriptionOptions withVocabularyHint(String hint) {
        return new TranscriptionOptions(audioFormat, languageHint, modelSize, hint);
    }
}
```

The `defaults()` factory now returns `"base.en"` as modelSize (previously `"tiny"`) — Whisper base is the new default. The `withVocabularyHint()` builder enables fluent construction without breaking existing code.

### 8. Pipeline Integration in SpeechSession

`TranscriptCorrector` is session-scoped — created in `SpeechSession`'s constructor alongside the existing `CleanupConfig`. The two-stage pipeline runs correction first, then cleanup.

```java
// SpeechSession constructor:
this.transcriptCorrector = new TranscriptCorrector(
    List.of(new SymSpellStrategy(symSpellIndex), new PhoneticStrategy(phoneticIndex)),
    ngramModel);

// In handleStop():
TranscriptionOptions opts = TranscriptionOptions.defaults()
    .withVocabularyHint(conversationVocabulary.asPromptHint());
stream = sttService.startStream(opts);
// ... accept audio, poll partials ...

// After STT finalResult:
String raw = stream.finalResult().text();

// Stage 1 — correction (stateful, session-scoped):
String corrected = transcriptCorrector.correct(raw);

// Stage 2 — cleanup (stateless TextFilter chain):
String clean = cleanupConfig.apply(corrected);

send(new AvatarMessage.Transcript(clean));

// After LLM response — feedback loop:
conversationVocabulary.addFromText(responseText);
transcriptCorrector.addVocabulary(
    conversationVocabulary.terms().toArray(String[]::new));
```

**Graceful degradation:** If any pipeline component fails (SymSpell dictionary missing, n-gram model not loaded, Whisper model unavailable), the pipeline degrades by skipping that component — never blocks the transcript. `TranscriptCorrector.correct()` catches strategy exceptions individually and returns uncorrected text for failed words. `CleanupConfig` already skips filters that exceed `maxDestructiveness`.

## Data Files

| File | Size | Source | Purpose |
|------|------|--------|---------|
| `frequency_dictionary_en_82_765.txt` | ~2MB | SymSpell project | Word frequencies for SymSpell + phonetic index |
| `bigrams_en.bin` | ~50MB | Google Ngrams subset / COCA | Bigram frequencies for contextual ranking |
| `ggml-base.en.bin` | ~142MB | huggingface.co/ggerganov/whisper.cpp | Whisper base English model |

All auto-provisioned via `Provisioner` with SHA-256 verification, same as existing TTS/STT models.

## Pipeline Ordering

Two stages — correction (stateful, session-scoped) then cleanup (stateless `TextFilter` chain via `CleanupConfig`).

**Stage 1 — TranscriptCorrector** (runs BEFORE CleanupConfig, not inside it):
- Stateful: holds `conversationVocabulary`, `SymSpellIndex`, `PhoneticIndex`
- Session-scoped: created per `SpeechSession`, not a CDI singleton
- `correct(String text) → String`

**Stage 2 — CleanupConfig** (stateless TextFilter chain, sorted by destructiveness):

| Filter | Destructiveness | What it does |
|--------|-----------------|--------------|
| CasingFilter | 0 | Fix ALL-CAPS STT output |
| FillerRemovalFilter | 1 | Remove um/uh/er + extended discourse markers |
| PunctuationFilter | 2 | Restore punctuation (ONNX model) |
| GectorFilter | 3 | Grammar correction (ONNX model) |

`CleanupConfig` sorts by destructiveness ascending — CasingFilter runs first (least destructive), GECToR last (most destructive). Consumers can cap with `CleanupConfig.upTo(maxDestructiveness)`.

**Why TranscriptCorrector is separate:** It's stateful (conversation vocabulary grows per session) and context-aware (n-gram ranking uses surrounding words). The `TextFilter` SPI is `String apply(String)` — stateless, no context parameter. Forcing TranscriptCorrector into TextFilter would violate the SPI contract.

## Testing

| Test | Type | What it verifies |
|------|------|-----------------|
| `SymSpellIndexTest` | Unit | Dictionary loading, edit-distance lookup, dynamic vocabulary |
| `PhoneticIndexTest` | Unit | Metaphone coding, sound-alike candidate retrieval |
| `NgramModelTest` | Unit | Bigram scoring, smoothing, context ranking |
| `TranscriptCorrectorTest` | Unit | End-to-end correction: "relimberate" → "limerick" in context "read a ___" |
| `FillerRemovalFilterExtendedTest` | Unit | Discourse markers, false starts, STT misrecognitions |
| `ConversationVocabularyTest` | Unit | Term extraction, prompt hint generation, dynamic growth |
| `WhisperLibraryTest` | Integration | FFM binding lifecycle: load model, transcribe known audio, verify text |
| `WhisperSpeechToTextTest` | Integration | Pseudo-streaming: feed audio chunks, verify partials converge to final |
| `PipelineIntegrationTest` | Integration | Full pipeline: known audio → Whisper → correction → cleanup → verify clean output |

## Constraints

### Whisper Pseudo-Streaming Latency

Whisper processes complete audio segments, not streaming frames. Pseudo-streaming re-runs inference on the buffered audio every ~500ms. Buffer is capped at 30 seconds (480K samples at 16kHz) to bound compute cost:
- For a 5-second utterance: ~10 inference runs, total ~15s of audio processed
- For a 30-second utterance (cap): ~60 runs, total ~465s of audio processed — still under 10s wall-clock on Apple Silicon with base.en
- Utterances exceeding 30s: oldest samples discarded, transcript covers the most recent 30s window

With base.en model on Apple Silicon: Whisper processes ~5s of audio in ~0.3s. Per-partial overhead is acceptable.

### N-gram Model Memory

Bigram frequency table for English: ~50MB compressed in memory. Loaded once at startup, shared across sessions. Acceptable for desktop/server deployment.

### SymSpell Dictionary Memory

Delete dictionary for 80K words at max_edit_distance=2: ~20MB. Loaded once, augmented dynamically per session.

### whisper.cpp Thread Safety

`whisper_full()` is NOT thread-safe for the same context. A single `whisper_context` is shared across all streams (loading the ~142MB model once), with inference serialized via `ReentrantLock`. For the single-user avatar, this is not a bottleneck — only one stream is active at a time. Multi-user deployment would need a context pool (out of scope for this issue).

## References

- [SymSpell algorithm](https://github.com/wolfgarbe/symspell) — Symmetric Delete spelling correction
- [Whisper-LM](https://arxiv.org/html/2503.23542v1) — n-gram integration during Whisper beam search (23-51% WER reduction)
- [Survey on Non-Intrusive ASR Refinement](https://arxiv.org/html/2508.07285v1) — four-stage correction pipeline with phonetic matching
- [Whisper: Courtside Edition](https://arxiv.org/html/2602.18966v1) — SymSpell + Damerau-Levenshtein with domain LLM agents
- [Contextual Spelling Correction for ASR](https://arxiv.org/abs/2108.07493) — NAR seq2seq model, 51% WER reduction
- [whisper.cpp](https://github.com/ggerganov/whisper.cpp) — C API, initial_prompt support
- [Double Metaphone](https://en.wikipedia.org/wiki/Metaphone#Double_Metaphone) — phonetic coding algorithm
- Existing: `SherpaLibrary.java`, `OnnxRuntimeLibrary.java`, `EspeakLibrary.java` — FFM binding pattern
- Existing: `GectorFilter.java`, `PunctuationFilter.java`, `CasingFilter.java` — TextFilter implementations
- Existing: `Provisioner.java` — auto-download with SHA-256 verification
