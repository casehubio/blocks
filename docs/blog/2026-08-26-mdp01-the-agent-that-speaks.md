---
layout: post
title: "The Agent That Speaks"
date: 2026-08-26
entry_type: article
subtype: diary
projects: [casehubio/blocks]
tags: [speech, ffm, panama, personality, social-cognition, sherpa-onnx, streaming]
---

# The Agent That Speaks

Most AI agents are text in, text out. They process strings and return strings. Even when they're modelling complex internal states — beliefs, drives, moods, social relationships — the interface is typed characters on a screen.

Adding speech changes something fundamental. Not because transcription is hard (it isn't, as it turns out), but because voice carries information that text doesn't. Hesitation. Confidence. Warmth. Speed. An agent that can hear you speak and respond with a voice that reflects its personality state is qualitatively different from one that reads your keystrokes and returns markdown.

CaseHub blocks already has the internal machinery for agent personality. What it didn't have was the mouth and ears.

## What blocks already knows about being human

Over the last few months, blocks has built a social cognition stack that's quietly ambitious. The personality evolution system lets agents develop traits over time through signal-driven pressure — not random drift, but bounded change in response to interactions, constrained by the agent's core disposition. The inner life orchestrator runs a background thought loop, generating proactive behaviour when the agent has something to say rather than waiting to be spoken to.

The mental model system gives each agent a Theory of Mind — a per-actor BDI (Beliefs, Desires, Intentions) model that tracks what the agent believes other actors know, want, and intend. The mood system maintains a PAD (Pleasure-Arousal-Dominance) emotional state that decays toward a personality-defined baseline, modulating everything from retrieval to response style. The drive architecture synthesises curiosity, competence, affiliation, and autonomy signals from across the cognitive stack, creating intrinsic motivation that doesn't need an external prompt.

And the narrative identity system constructs an ongoing story of who the agent is — individual episodes, group episodes, and derived themes that form a coherent self-narrative.

All of this was text-only. The agent could think, feel, want, remember, and reflect. It just couldn't hear or speak.

## FFM/Panama: calling native code without the JNI tax

The speech implementation uses Java's Foreign Function & Memory API — the FFM/Panama interfaces stabilised in JDK 22. Instead of writing JNI glue code, you define function handles directly from C function descriptors and call native libraries through type-safe method handles. Memory is managed through arenas that clean up automatically.

The binding targets sherpa-onnx, an ONNX Runtime wrapper from the Next-gen Kaldi project that covers speech-to-text, text-to-speech, streaming recognition, and punctuation restoration — all through a single native library.

Getting the FFM bindings right was instructive. sherpa-onnx's C API uses deeply nested config structs with dozens of model-type sub-configs embedded inline. The offline recognizer config alone is 800+ bytes — it embeds sub-structs for Whisper, Paraformer, Zipformer, Moonshine, SenseVoice, and a dozen other model types we don't use. We discovered this the hard way: our initial struct layout was 280 bytes. The C library read past our allocation into uninitialised memory, crashing with a SIGSEGV in `_platform_strlen`.

The fix was pragmatic. Instead of defining exact struct layouts in Java (which break every time sherpa-onnx adds a model type), we allocate 4096 bytes of zeroed memory and set specific fields at known byte offsets. This matches what C code does — `memset(&config, 0, sizeof(config))` then set the fields you care about. The extra zeros are in our own arena and never touched.

## What the numbers actually look like

The performance question matters because speech processing has to be fast enough that users don't notice it. Here's what we measured on Apple Silicon:

Offline transcription with Whisper tiny runs at 23x real-time — a 16-second audio clip transcribes in 724ms. Text-to-speech with VITS Piper generates 9.6 seconds of audio from 251 characters in 878ms. We round-tripped text through TTS and back through STT and got the original sentence back verbatim.

Streaming recognition with a Zipformer transducer model produces partial results within 200ms of each audio chunk. Words appear as you speak — "The... yellow... lamps... would... light... up..." — building out the sentence in real-time.

We tested CoreML (Apple's GPU/ANE acceleration path) against CPU. CPU won, 3.5x faster. This wasn't a misconfiguration — it's a documented issue with ONNX Runtime's CoreML execution provider. The graph partitioning overhead and CPU↔ANE data transfer roundtrips cost more than they save for these model sizes. Apple Silicon's CPU cores are fast enough that offloading to the Neural Engine is counterproductive.

## The cleanup pipeline nobody wants to think about

Raw speech-to-text output is rough. ALL CAPS, no punctuation, filler words intact. "UM THE UH YELLOW LAMPS WOULD UM LIGHT UP" is not what you want to display.

The cleanup pipeline is a chain of `TextFilter` implementations, each with a declared destructiveness level. Lower destructiveness runs first. The consumer sets a ceiling — everything below the ceiling runs, everything above it doesn't.

```java
SherpaConfig config = SherpaConfig.defaults(sttModelDir)
        .withPunctuation(punctModelDir);
// Or explicitly:
CleanupConfig.upTo(2,
    new CasingFilter(),           // destructiveness 0: lowercase
    new FillerRemovalFilter(),    // destructiveness 1: strip um/uh/er
    new PunctuationFilter(path)); // destructiveness 2: add periods, commas, casing
```

The full pipeline runs in 10ms. The consumer doesn't see it — `partialResult()` returns cleaned text automatically. Input: "UM THE UH YELLOW LAMPS WOULD UM LIGHT UP". Output: "The yellow lamps would light up."

The architecture has slots for GECToR (grammar correction via token tagging, destructiveness 3) and LanguageTool (rule-based grammar, destructiveness 4). Both are non-LLM, both are ONNX-exportable or pure Java, both add 20-100ms per sentence. The SPI is the same — just another `TextFilter` implementation.

## Where speech meets personality

Here's where it gets interesting. An agent with the drive architecture active has a curiosity signal derived from knowledge gaps in its memory. It has a competence signal from engagement trend analysis. It has an affiliation signal from neglected relationships. And it has a mood state — a PAD vector that decays toward its personality baseline.

When that agent speaks, all of those signals can modulate the voice. A curious agent speaks faster, with rising intonation. A low-dominance agent in a high-arousal mood hedges more. An agent with strong affiliation drive asks questions. The TTS system takes text — but the text itself was shaped by the agent's personality, mood, and drives before it ever reached the synthesiser.

And when the agent listens, the streaming transcription feeds into the mental model system. The agent's Theory of Mind updates in real-time as the human speaks — not after they finish, but word by word, belief by belief. The common ground analyser classifies what's been established, what's pending, what's disputed. The convergence detector tracks whether the conversation is progressing or stuck.

This is the convergence that matters. Not speech-to-text as a feature. Not text-to-speech as a feature. But an agent that hears you hesitate and adjusts its confidence. An agent that speaks with a voice shaped by who it is. An agent whose personality evolves through every conversation it has — and whose voice evolves with it.

The mouth and ears are the easy part. Knowing what to say, and how to say it, is what blocks has been building all along.
