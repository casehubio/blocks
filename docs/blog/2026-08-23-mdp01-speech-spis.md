---
title: "Speech SPIs — voice in, voice out"
date: 2026-08-23
author: mdp
tags: [blocks, speech, spi, sherpa-onnx, ffm]
---

# Speech SPIs — voice in, voice out

Speech belongs in blocks. Not because every CaseHub app needs it today, but because the interface shape is generic — transcribe audio to text, synthesise text to audio — and the implementation choice behind it matters enormously. Latency between a local Whisper model and a cloud API call is two orders of magnitude. Quality between model sizes is measurable and context-dependent. The consumer shouldn't care which provider is wired in.

Two SPIs: `SpeechToTextService` takes a `Path` and returns a `TranscriptionResult` (text, detected language, confidence score). `TextToSpeechService` takes a `String` and returns a `SynthesisResult` (audio bytes, format, and optionally a list of `PhonemeTiming` records). The phoneme timing is there because avatar lip-sync (blocks#154) needs mouth positions aligned to millisecond offsets — baking that into the SPI now avoids a breaking change later.

`Path` rather than `byte[]` for the STT input was a deliberate choice. Audio files are large — a five-minute recording at 16kHz mono is around 10MB uncompressed. Loading that into a Java byte array just to hand it to a native library that reads files directly is wasteful. sherpa-onnx, the default implementation, takes a file path through its FFM bindings. So does every cloud SDK that supports file upload. The abstraction matches what implementations actually want.

The default implementation uses sherpa-onnx via Java FFM/Panama — one native binding covers both directions. Whisper models for STT (from 39MB tiny to 1.5GB large), VITS/Piper for TTS with phoneme extraction. Apple Silicon gets Metal acceleration out of the box. The model management is straightforward: configurable storage path, download on first use, model size selection per consumer. Alternative providers (Deepgram, ElevenLabs, Google) slot in via CDI `@Alternative` — same SPI, different latency/quality/cost profile.

This triggers a structural change in the blocks repo. casehub-blocks has been a single Maven module since inception — every block depends on qhorus-api, work-api, and engine-api. Speech doesn't need any of those. The SPI module (`blocks-speech-api`) is pure Java with zero foundation dependencies. A consumer that only needs the interface shouldn't pull in the entire foundation stack. So blocks becomes a multi-module reactor: the existing module keeps its dependencies, `blocks-speech-api` stays clean, and `blocks-speech-sherpa` adds the native implementation.

DraftHouse (drafthouse#117) is the first consumer — voice-first drafting where the user speaks and the document updates. The SPIs will live in blocks; the session wiring lives in DraftHouse.
