---
layout: post
title: "The Voice Engine That Doesn't Export"
date: 2026-08-28
entry_type: note
subtype: diary
projects: [casehubio/blocks]
tags: [tts, onnx, lip-sync, audio8, fish-speech, composable-pipeline]
---

I went into this session planning to integrate Fish Speech — the open-source TTS model that benchmarks closest to human parity. The issue had it tagged as S/Low: "follows established ONNX TTS integration pattern." Straightforward port, same shape as Kokoro and VITS.

It isn't.

Fish Speech's architecture is a Dual-AR transformer — two autoregressive stages built on a LLaMA-style backbone. Multiple GitHub issues document failed ONNX export attempts: the operators LLaMA needs don't have ONNX equivalents. The Fish Audio team knows this and has invested in SGLang and vLLM acceleration instead. ONNX is a dead path for the canonical model.

But the architecture itself — the DualAR pattern that makes Fish Audio's quality possible — has been successfully exported by a community project called Audio8 TTS. They took the DualAR architecture, trained compact variants (0.1B and 0.6B parameters), and published fully quantised ONNX packages that run on CPU in about a gigabyte of memory. An M2 MacBook Air handles it.

This changes what we're building, but not what the user hears. The quality target stays the same. The implementation underneath is fundamentally different from every TTS engine in the codebase.

## From feedforward to autoregressive

Every existing TTS implementation — VITS, Kokoro, SherpaOnnx — works the same way: one forward pass through the model, complete audio comes out. The entire `synthesise()` method is essentially a single `session.run()` call.

DualAR doesn't work like that. It generates audio token by token. A slow AR predicts one semantic token per audio frame. A fast AR then fills in the codec codebooks for that frame, conditioned on the slow AR's hidden state. Each step feeds back into the next. KV-cache state grows with every token. The model can get stuck in degenerate loops. Latency scales linearly with output length instead of being roughly constant.

This is closer to running an LLM than running a TTS model. Three simultaneous ONNX sessions (slow AR, fast AR, codec decoder), KV-cache management across steps, temperature and top-k sampling. I studied the Audio8 Python reference implementation in detail — the inference loop alone is about 150 lines of careful tensor manipulation.

## The lip-sync insight

The existing codebase has a lip-sync pipeline built around `VisemeMapping` — it maps IPA phonemes to Oculus viseme shapes with per-viseme weights and minimum durations. But only one TTS engine actually produces phoneme timing: `VitsTextToSpeech`, which extracts durations from the VITS model's duration predictor. Kokoro and SherpaOnnx return empty phoneme lists. The avatar's mouth doesn't move when using them.

Audio8 won't produce phoneme timing either. Neither will Dia, the emotion-capable TTS that's next on the list. So I had a choice: bake lip-sync into each TTS engine individually, or make it composable.

The composable approach turned out clean. A `PhonemeAligner` SPI takes text and audio, returns timing. A `LipSyncEnricher` decorator wraps any `TextToSpeechService` — if the delegate returns empty phonemes, the enricher fills them in; if the delegate has native timing (like VITS), it passes through unchanged. Two classes, and every TTS engine in the codebase gets lip-sync.

```java
TextToSpeechService tts = Audio8TextToSpeech.withDefaults("0.6b");
TextToSpeechService withLipSync = LipSyncEnricher.wrap(tts, aligner);
```

The initial aligner uses espeak-ng — already integrated in the codebase, zero new dependencies. It phonemises the text and distributes timing proportionally across the audio duration. Approximate, but immediate. A wav2vec2 ONNX forced aligner can drop in behind the same SPI later if the quality needs upgrading.

## The premise that wasn't true

I'd originally designed a hybrid lip-sync system: frequency-based analysis for real-time streaming, forced alignment for pre-rendered content. The streaming path would analyse audio frequencies directly — low Hz maps to open vowels, consonant spikes to bilabials — giving immediate mouth movement with zero model dependency.

Claude caught the flaw during decision review. `SpeechSession` synthesises sentence by sentence. Each sentence is fully synthesised before any audio or phoneme data is sent to the client. There is no streaming. The complete audio exists before the client receives anything. The frequency-based path was solving a problem that doesn't exist in the current architecture.

I dropped it. Forced alignment alone works with zero user-facing latency when the audio is already complete.

## Dia validates the pattern

The next TTS integration after Audio8 is Dia — a dialogue-native model that handles emotions and nonverbal cues via inline tokens like `[laugh]` and `[sigh]`. I checked whether the composable architecture would hold.

Dia's ONNX export already exists on HuggingFace (6.45 GB — considerably larger than Audio8's 1 GB). Its architecture is encoder-decoder transformer with a DAC codec — different internals, same `TextToSpeechService` contract. The lip-sync enricher works unchanged. Voice cloning uses the same pre-registration pattern. The only new piece is an `EmotionInjector` decorator that maps the avatar's mood state to Dia tokens before synthesis — a text-preprocessing stage that composes naturally in front of the TTS engine.

```java
TextToSpeechService dia = DiaTextToSpeech.withDefaults();
TextToSpeechService withEmotions = EmotionInjector.wrap(dia, moodOrchestrator);
TextToSpeechService withLipSync = LipSyncEnricher.wrap(withEmotions, aligner);
```

The composable pipeline isn't just a convenience for Audio8. It's the universal TTS integration pattern — new engine, implement `TextToSpeechService`, wrap with whatever enrichment the use case needs.
