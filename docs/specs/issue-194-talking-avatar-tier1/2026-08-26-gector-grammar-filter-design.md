# Design: GECToR Grammar Correction TextFilter

**Issue:** casehubio/blocks#179 (epic: #178)
**Date:** 2026-08-26
**Branch:** issue-194-talking-avatar-tier1

## Problem

Live speech transcription output lacks grammar correction. The clean
transcription pipeline (#178) needs a `TextFilter` that fixes grammatical
errors at destructiveness level 3 — structural changes to word order, verb
forms, articles, and prepositions.

## Approach

GECToR (Grammatical Error Correction: Tag, Not Rewrite) is Grammarly's
sequence tagger. It predicts per-token edit operations ($KEEP, $DELETE,
$APPEND, $REPLACE, morphological transforms) rather than generating
corrected text autoregressively. Non-autoregressive inference is 10x
faster than seq2seq — 20-50ms per sentence on CPU.

Reuse `OnnxRuntimeLibrary` from #186 for inference. Add `SentencePieceLibrary`
for tokenization (same FFM pattern as `EspeakLibrary`).

## Architecture

```
Text
 │
 ├─► sentence split + word-level pre-tokenization
 │
 ├─► SentencePieceLibrary (FFM) ──► subword IDs + [CLS]/[SEP] + attention mask
 │
 └─► OnnxRuntimeLibrary ──► GECToR ONNX model
                              │
                         tag logits [1, seq_len, num_tags]
                              │
                    softmax → threshold filter → argmax
                              │
                    subword→word aggregation (first-subword-wins)
                              │
                    GectorTagApplier.apply(wordTokens, wordTags)
                              │
                    corrected words
                              │
                    [iterate up to N times if changes detected]
                              │
                    join with spaces → corrected text
```

### New Classes (speech-sherpa module)

| Class | Responsibility |
|-------|---------------|
| (SentencePiece4J) | Pure Java SentencePiece tokenizer via Maven dependency `io.github.eix128:sentencepiece4j`. Reads `.model` protobuf files. Supports BPE and UNIGRAM. Used by `GectorFilter` for subword tokenization. No FFM class needed — just a library call. Thread-safe per instance. |
| `GectorConfig` | Record: `modelDir`, `maxIterations` (default 5), `keepConfidence` (default 0.0), `minErrorProb` (default 0.0), `numThreads`, `provider`. Factory `fromModelDir(Path)` parses `labels.txt` (tag vocabulary) and `spiece.model` path. |
| `GectorFilter` | `TextFilter` implementation (destructiveness 3, name "grammar"). Composes `SentencePieceLibrary` + `OnnxRuntimeLibrary`. Iterative inference loop. `AutoCloseable` — owns ORT session. Constructor: `(GectorConfig, OnnxRuntimeLibrary, SentencePieceLibrary)`. |
| `GectorTagApplier` | Stateless utility: `apply(List<String> tokens, int[] tagIds, GectorConfig) → List<String>`. Applies edit operations from predicted tags. Handles $KEEP, $DELETE, $APPEND_{word}, $REPLACE_{word}, and morphological transforms ($TRANSFORM_VERB, $TRANSFORM_CASE, $TRANSFORM_AGREEMENT). Uses a verb dictionary (`verb-form-vocab.txt`) for verb transforms. |

### Reused from #186

- `OnnxRuntimeLibrary` — exact same vtable bindings, creates a separate ORT session for the GECToR model
- `Provisioner` — extended with GECToR model + SentencePiece provisioning

### Export Script

`speech-sherpa/scripts/export_gector_onnx.py` — exports gotutiyan/gector-deberta-{base,large}-5k to ONNX using `torch.onnx.export`. Outputs: model.onnx + labels.txt + spiece.model + verb-form-vocab.txt.

## Model Selection

Consumer-driven. Two pre-exported models available:

| Model | Size | Speed | Quality |
|-------|------|-------|---------|
| `gector-deberta-base-5k` | ~500MB | ~20ms/sentence | Good |
| `gector-deberta-large-5k` | ~2GB | ~50ms/sentence | Best (F0.5=65.3) |

`Provisioner.ensureGectorModel(String modelName)` downloads the selected model.
`GectorConfig.fromModelDir(Path)` is model-agnostic — works with any exported GECToR model.

## Data Flow

### Provisioning (one-time)

1. `Provisioner.ensureGectorModel(modelName)` — downloads pre-exported ONNX model bundle (model.onnx + labels.txt + spiece.model + verb-form-vocab.txt). SentencePiece tokenization is handled by SentencePiece4J (pure Java Maven dependency) — no native library provisioning needed for the tokenizer.

### Inference (per `apply()` call)

1. **Sentence split:** Split input into sentences (period/question/exclamation
   boundaries). Each sentence is processed independently — DeBERTa max
   sequence length is 512 subwords.
2. **Pre-tokenize:** Split each sentence into word-level tokens. Must handle
   contractions ("don't" → ["do", "n't"]), possessives ("cat's" →
   ["cat", "'s"]), and punctuation separation ("hello," → ["hello", ","]).
   Use a rule-based tokenizer matching GECToR's training preprocessing
   (spaCy-compatible rules).
3. **Subword tokenize:** For each word token, SentencePiece4J `encode()`
   → subword IDs. Build word-to-subword alignment map. Prepend [CLS] token
   ID, append [SEP] token ID. Truncate to 512 subwords if exceeded.
4. **Inference:** Feed subword IDs + attention mask to ORT session → tag
   logits `[1, seq_len, num_tags]`
5. **Softmax + threshold:** Compute softmax over tag dimension. For each
   position: if P($KEEP) > `keepConfidence`, force $KEEP. If
   (1 - P($KEEP)) < `minErrorProb`, force $KEEP. Otherwise take argmax.
6. **Aggregate:** Map subword-level predictions back to word-level using
   first-subword-wins strategy. [CLS]/[SEP] positions are excluded.
   In SentencePiece, `▁` marks word-INITIAL positions (not continuations).
7. **Apply tags:** `GectorTagApplier.apply(wordTokens, wordTags)` →
   corrected word list. Verb transforms fall back to original word if
   not found in dictionary.
8. **Iterate:** If any corrections were made and iteration < maxIterations,
   go to step 2 with corrected text
9. **Return:** Join corrected words with spaces (NOT SentencePiece
   detokenize — GECToR operates at word level)

### Tag Application Rules

| Tag | Action |
|-----|--------|
| `$KEEP` | Keep token unchanged |
| `$DELETE` | Remove token |
| `$APPEND_{word}` | Keep token, insert `word` after it |
| `$REPLACE_{word}` | Replace token with `word` |
| `$TRANSFORM_VERB_{form}` | Look up verb form in verb dictionary |
| `$TRANSFORM_CASE_CAPITAL` | Capitalize first letter |
| `$TRANSFORM_CASE_LOWER` | Lowercase entire token |
| `$TRANSFORM_CASE_UPPER` | Uppercase entire token |
| `$TRANSFORM_AGREEMENT_SINGULAR` | Apply singular form |
| `$TRANSFORM_AGREEMENT_PLURAL` | Apply plural form |
| `$MERGE_HYPHEN` | Merge with next token via hyphen |
| `$MERGE_SPACE` | Merge with next token (no space) |

## Constraints

### Word-Subword Alignment

GECToR operates at word level but DeBERTa tokenizes at subword level.
The alignment strategy: predict on subwords, take the first subword's
tag for each word. [CLS] and [SEP] tokens are excluded from alignment
(they don't correspond to input words). In SentencePiece, `▁` (U+2581)
marks word-INITIAL positions — the first subword of a new word. Tokens
WITHOUT `▁` are subword continuations (e.g., "running" → `▁run` + `ning`).

### SentencePiece → DeBERTa ID Mapping

The export script must ensure that raw SentencePiece `tokenize()` IDs
are exactly the model's expected input embedding indices. DeBERTa v3's
`DebertaV2Tokenizer` wraps SentencePiece with potential vocabulary
offsets. The exported `spiece.model` must produce IDs matching the
model's embedding layer. This is verified by a round-trip test in the
export script: `SPM.tokenize(text)` → model input → sensible output.

### Sequence Length

DeBERTa max sequence length is 512 subwords. Input is split at sentence
boundaries before tokenization. If a single sentence exceeds 512 subwords
after tokenization (rare for spoken language), it is truncated with a
warning log.

### Iterative Convergence

The iteration loop terminates when either: (a) no corrections were made
in the last pass, or (b) maxIterations reached. This prevents infinite
loops on pathological inputs where corrections oscillate.

### Thread Safety

SentencePiece4J is thread-safe per processor instance.
`GectorFilter.apply()` is safe for concurrent use — each call creates
its own `Arena.ofConfined()` for tensor memory.

### ORT Environment Sharing

`OnnxRuntimeLibrary.createSession()` currently creates a new ORT env
per session. With GECToR adding a second concurrent ORT session alongside
`VitsTextToSpeech`, the env should be shared. Refactor `createSession()`
to reuse a singleton env with a configurable log ID. This is design debt
from #186 — address during GECToR implementation.

## Testing

| Test | Type | Native deps | What it verifies |
|------|------|-------------|-----------------|
| `GectorTagApplierTest` | Unit | None | Tag application: $KEEP, $DELETE, $APPEND, $REPLACE, morphological transforms. Uses hardcoded tag IDs. |
| `SentencePieceTokenizerTest` | Unit | None (SentencePiece4J) | Tokenize/detokenize round-trip with a test spiece.model. Known text → expected token IDs. |
| `GectorConfigTest` | Unit | None | Parse labels.txt tag vocabulary. Config defaults. |
| `GectorFilterTest` | Integration | onnxruntime + sentencepiece + model | End-to-end: grammatically incorrect text → corrected text. Known test cases from GECToR paper. `@DisabledIf` any dep unavailable. |
| `GectorFilterIterationTest` | Integration | same | Verifies iterative correction (multi-pass). Input requiring 2+ passes to fully correct. |

## References

- [GECToR paper](https://arxiv.org/abs/2005.12592) — architecture and tag vocabulary
- [gotutiyan/gector](https://github.com/gotutiyan/gector) — PyTorch implementation with DeBERTa support
- [gotutiyan/gector-deberta-large-5k](https://huggingface.co/gotutiyan/gector-deberta-large-5k) — pre-trained model
- [Grammarly GECToR blog](https://www.grammarly.com/blog/engineering/experimenting-with-gector/) — large model experiments
- [HF Optimum ONNX export](https://huggingface.co/docs/optimum-onnx/onnx/usage_guides/export_a_model) — export guidance for DeBERTa
- `OnnxRuntimeLibrary.java` — existing FFM onnxruntime bindings (reused)
- `EspeakLibrary.java` — FFM pattern template for SentencePieceLibrary
- `PunctuationFilter.java` — existing TextFilter with native model (pattern reference)
- `TextFilter.java` — SPI interface
