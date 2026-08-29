# Decisions — STT Accuracy Pipeline (#212)

## D1: Correction mechanism

**Choice:** SymSpell edit-distance spelling correction (pure algorithmic, no neural model)
**Alternatives:**
- Neural contextual spelling correction (ONNX seq2seq) — 51% WER reduction in research, but no pre-built model available; would need training
- LLM-based zero-shot correction — research shows this often increases errors via over-correction
**Rationale:** Zero model weight, microsecond latency, proven approach (phone keyboards). Combined with contextual vocabulary, handles most ASR errors without network calls or large models.
**Trade-offs:** Won't handle complex phonetic confusions where the word sounds completely different. Hotwords and contextual ranking mitigate this.
**Sources:** arXiv:2108.07493, SymSpell algorithm, sherpa-onnx#2570
**Exploration:** deep-analysis
**Status:** captured

## D2: Pipeline architecture

**Choice:** Mechanical layer first (ONNX + pure Java), LLM optional overlay
**Alternatives:**
- LLM-primary pipeline — highest accuracy but adds 200-500ms latency per correction
- Neural-only pipeline — requires training/finding ASR-specific ONNX models
**Rationale:** Mechanical processing is fast, deterministic, and works offline. LLM adds quality when available but isn't required.
**Trade-offs:** Mechanical layer has a ceiling — some errors only an LLM can fix. The optional LLM layer addresses this.
**Sources:** Research summary on zero-shot LLM over-correction, GECToR architecture precedent
**Exploration:** deep-analysis
**Status:** captured

## D3: Contextual candidate ranking

**Choice:** N-gram language model for ranking SymSpell candidates in context
**Alternatives:**
- Unigram frequency only — simpler but context-blind
- Neural language model — more accurate but heavier
**Rationale:** Bigram/trigram frequencies give contextual ranking at negligible cost. "hear a limerick" ranks higher than "hear a relimberate" because "a limerick" is a known bigram.
**Trade-offs:** N-gram models need a corpus. A pre-built English n-gram table (~50MB) covers general vocabulary. Domain terms added dynamically from conversation.
**Sources:** SymSpell documentation, standard n-gram language modeling
**Exploration:** quick
**Status:** captured

## D4: Hotword source

**Choice:** Dynamic hotwords from conversation context + static domain vocabulary
**Alternatives:**
- Static vocabulary only — misses conversation-specific terms
- LLM response mining only — reactive, not proactive
**Rationale:** Conversation terms (from both user and avatar responses) build the hotword list dynamically. Static domain vocabulary covers expected terms from the start.
**Trade-offs:** Hotwords feed into Whisper's initial_prompt (decoder conditioning) and SymSpell dictionary. First-occurrence words still need the correction pipeline.
**Sources:** sherpa-onnx hotwords documentation, whisper.cpp initial_prompt
**Exploration:** quick
**Status:** captured

## D5: STT engine

**Choice:** whisper.cpp via FFM/Panama, replacing Zipformer as the primary STT
**Alternatives:**
- Keep Zipformer + heavy correction — lower base accuracy, correction pipeline compensates
- CTranslate2 / faster-whisper — 4x faster with GPU batching, but C++ API (no clean C for FFM), and GPU batching irrelevant for single-user avatar
- WhisperLive (Python sidecar) — adds Python process dependency, operational complexity
**Rationale:** whisper.cpp has a clean C API designed for embedding (proven FFM pattern — 4th binding). Better base accuracy than Zipformer. initial_prompt provides vocabulary biasing at the decoder level. CPU performance slightly better than CTranslate2 for sequential single-user inference.
**Trade-offs:** Pseudo-streaming (periodic re-inference on growing buffer) rather than true frame-by-frame streaming. Acceptable for conversational avatar — partials update every ~500ms.
**Depends on:** D4 (hotwords feed into initial_prompt)
**Sources:** whisper.cpp C API, faster-whisper GPU benchmarks, CTranslate2 C++ API limitation
**Exploration:** deep-analysis
**Status:** captured

## D6: Correction pipeline as primary differentiator

**Choice:** Dictionary + statistical token model (SymSpell + n-gram) is the core accuracy mechanism, not the STT model alone
**Alternatives:**
- STT-model-only (rely on Whisper accuracy without post-correction) — leaves residual errors unfixed
- Neural correction model — no pre-built ASR-specific ONNX model available
**Rationale:** Even Whisper makes errors on uncommon words. The correction pipeline (edit-distance candidates ranked by n-gram context) is what turns "pretty good" into "reliable." The pipeline is also where conversation context has the most impact — SymSpell dictionary grows with conversation terms.
**Trade-offs:** N-gram tables add ~50MB memory. SymSpell delete dictionary adds ~20MB. Acceptable for desktop/server deployment.
**Sources:** SymSpell algorithm, n-gram language modeling, user feedback on residual Whisper errors
**Exploration:** quick
**Status:** captured

## D7: Phonetic similarity matching

**Choice:** Add phonetic candidate matching alongside SymSpell edit-distance, using Double Metaphone (pure Java) + optional EspeakLibrary IPA
**Alternatives:**
- Edit distance only — misses sound-alike errors where spelling diverges (edit distance too high)
- IPA-only via EspeakLibrary — accurate but adds native dependency; Double Metaphone is sufficient for English and is pure Java
**Rationale:** ASR errors are phonetically motivated — the model heard something that SOUNDS right but spelled it differently. "relimberate" and "limerick" share phonetic features that edit distance (7) can't match but Metaphone codes can. Combined with n-gram ranking, this catches the hardest ASR errors.
**Trade-offs:** Double Metaphone is English-centric. Multi-language support would need IPA via EspeakLibrary (already available).
**Depends on:** D1 (SymSpell), D3 (n-gram ranking)
**Sources:** arXiv:2508.07285 survey (phonetic similarity in ASR correction), Double Metaphone algorithm
**Exploration:** deep-analysis
**Status:** captured
