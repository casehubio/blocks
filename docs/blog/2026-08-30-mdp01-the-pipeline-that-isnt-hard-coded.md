---
layout: post
title: "The Pipeline That Isn't Hard-Coded"
date: 2026-08-30
entry_type: note
subtype: diary
projects: [casehubio/blocks]
tags: [speech, tts, onnx, cosyvoice, pipeline-framework]
---

# The Pipeline That Isn't Hard-Coded

Audio8 proved that autoregressive ONNX TTS works in Java via FFM. But its implementation is monolithic — one factory method wires a tokenizer, a DualAR loop with Mamba state, a codec decoder, and a voice registry. Adding a second TTS engine would mean duplicating all the session lifecycle boilerplate while writing completely different inference logic.

CosyVoice3 forced the issue. Its architecture is nothing like Audio8's: an LLM backbone with transformer KV-cache generates speech tokens, a flow decoder runs 10-step Euler diffusion to produce mel spectrograms, and a HiFT vocoder converts mels to audio via STFT/ISTFT. Fourteen ONNX files across four stages, versus Audio8's three sessions in one loop. Same `TextToSpeechService` contract, completely different internals.

The original plan was CosyVoice2. That fell apart immediately — the only available ONNX export (Lourdle/CosyVoice2-0.5B_ONNX) is incomplete. It has the flow decoder and HiFT vocoder but not the LLM backbone that actually generates speech tokens from text. Without the LLM, you have a mel-to-audio converter with no text input. CosyVoice3 (ayousanz/cosy-voice3-onnx) has the full pipeline — 14 models, 3.8 GB, with a reference Python implementation to port from.

## What the framework does

The pipeline framework is manifest-driven. A `pipeline_manifest.json` in the model directory declares stages, ONNX files, hyperparameters, and execution provider preferences. Four stage SPIs — `TtsTokenizer`, `TtsGenerator`, `TtsDecoder`, `TtsVoiceEncoder` — define the contracts. The framework manages everything around them: session creation and closure, model provisioning, voice registry, and eventually GPU provider detection.

The interesting design choice was voice data. Audio8's voice cloning produces codec tokens (an `int[]`). CosyVoice3's produces a speaker embedding, speech tokens, a precomputed mel spectrogram, and a transcript — four fields with three different types. A `VoiceData` sealed hierarchy with exhaustive `switch` gives compile-time coverage without the stringly-typed `Map<String, byte[]>` that was the first instinct. Same principle applied to generator output: `SpeechTokenOutput` for CosyVoice3's LLM, `CodecFrameOutput` for Audio8's DualAR.

The manifest is also a sealed hierarchy. CosyVoice3's manifest carries 15 hyperparameters (hidden dim, LLM layers, flow steps, HiFT FFT size). Audio8's RuntimeManifest already has 27 fields. A flat union would be configuration sprawl. Each model family gets its own record under a common `PipelineHeader`.

## What changed during review

The design review revised three decisions that would have caused problems:

**Retrofit sequencing.** The original plan was to refactor Audio8 into the pipeline framework simultaneously with CosyVoice3. The review argued this risks over-fitting the framework to Audio8's existing patterns — backward-compatibility pressure from existing tests would constrain the new abstraction. CosyVoice3 goes first; Audio8 retrofit validates generalisation afterward.

**GPU timing.** The OnnxRuntimeLibrary has zero GPU infrastructure today — no CUDA vtable entries, no CoreML provider, CPU-only session creation. Adding GPU support while simultaneously validating a new pipeline framework couples two unrelated concerns. The manifest carries provider preference fields from day one (essentially free), but actual GPU implementation is a follow-up. CosyVoice3 on CPU takes 73–226 seconds per utterance — slow, but sufficient to prove the pipeline works.

**Streaming scope.** Audio8's streaming uses windowed overlap-subtract tightly coupled to codec frame timing — hop length, context frames, guard frames from its model manifest. CosyVoice3's flow decoder runs 10-step Euler diffusion, which may not support incremental processing at all. Designing a "streaming pipeline" abstraction from one working example is speculation. Streaming stays on concrete implementations until a second model proves a common pattern.

## The VoiceRegistry generalisation

The most satisfying refactor was VoiceRegistry. It stored `Map<String, int[]>` with a `VoiceEncoder` returning `int[]`. Now it stores `Map<String, VoiceData>` with a `TtsVoiceEncoder` returning the sealed hierarchy. Audio8's callers needed a one-line migration:

```java
VoiceData vd = voiceRegistry.get(options.voice());
int[] voiceCodes = ((VoiceData.CodecVoiceData) vd).codecTokens();
```

CosyVoice3's voice encoder will produce `EmbeddingVoiceData` with the speaker embedding, speech tokens, prompt mel, and transcript — same registry, different data shape.

## What's ahead

The pipeline framework compiles and all existing tests pass. Next is the DSP work: a Cooley-Tukey FFT, mel spectrogram extraction with three different configurations (campplus at 16kHz, Whisper-style at 16kHz, flow conditioning at 24kHz), STFT/ISTFT for HiFT's vocoder, and audio resampling. None of this exists in speech-sherpa today. Then the four CosyVoice3 stages — the Qwen2 BPE tokenizer, the voice encoder, the LLM generator with KV-cache, and the flow+HiFT decoder. Each one is a port from the reference Python, verified against it stage by stage.
