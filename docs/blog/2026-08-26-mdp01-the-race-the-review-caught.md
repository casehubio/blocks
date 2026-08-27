---
layout: post
title: "The Race the Review Caught"
date: 2026-08-26
entry_type: note
subtype: diary
projects: [casehubio/blocks]
tags: [speech, threading, ffm, sherpa-onnx, design-review]
series: issue-194-talking-avatar-tier1
---

# The Race the Review Caught

The microphone capture utility was straightforward — a background thread reading PCM chunks from `javax.sound.sampled.TargetDataLine`, converting 16-bit samples to float, feeding them to `RecognitionStream.acceptSamples()`. We built it in an hour. The interesting part came after.

We'd designed `MicrophoneCapture` as a pure audio feeder — it captures and feeds, the caller polls `RecognitionStream` for partial results. Clean separation. The CLI `listen` command demonstrates the pattern: capture thread writes samples, main thread polls `partialResult()` and `isEndpointDetected()` in a 100ms loop. Live transcription, streaming, done.

Except every one of those native calls — `decodeOnlineStream`, `getOnlineStreamResult`, `isEndpoint` — operates on the same sherpa-onnx recognizer and stream memory segments. `SherpaRecognitionStream` had zero synchronisation. No locks, no atomics, nothing beyond a `volatile boolean closed`. The existing `SpeechCli.stream()` command had been single-threaded — read a chunk, feed it, check the result, repeat — so the absence of synchronisation was invisible.

Claude's design review caught it. The reviewer traced the concurrent access paths: capture thread calling `acceptSamples()` → `decode()` while the main thread calls `partialResult()` → `readResult()`. Both hit native FFM downcalls on shared `MemorySegment` pointers. sherpa-onnx's C++ `OnlineStream` maintains mutable internal state — audio buffer, decoder state, feature extractor. Concurrent access is a data race on native memory. The consequences: corrupted results, SIGSEGV, use-after-free in `destroyOnlineRecognizerResult`.

The fix was `ReentrantLock`, not `synchronized`. On JDK 22, `synchronized` pins virtual threads to carrier threads — and while we'd already switched `MicrophoneCapture` to a platform daemon thread (for a related reason: `TargetDataLine.read()` also uses `synchronized` internally, which pins), the `SherpaRecognitionStream` might be used with virtual threads elsewhere. `ReentrantLock` is virtual-thread-friendly and costs nothing extra.

The same review also flagged a subtlety with `Arena.ofConfined()`. A confined arena restricts memory segment access to the creating thread. The stream was created on the main thread but accessed from the capture thread — a `WrongThreadException` waiting to happen. Switching to `Arena.ofShared()` alongside the lock fixed both the access restriction and the race.

What made this catch non-obvious: the code worked perfectly in the single-threaded case. Every existing test and the existing CLI `stream` command exercised the exact same native calls — just never concurrently. The multi-threaded usage pattern was new, introduced by `MicrophoneCapture`, and the race would only surface under real concurrent load. A unit test with mock `TargetDataLine` wouldn't trigger it because the mocks don't touch native memory.

This is the kind of bug that ships to production and manifests as intermittent SIGSEGV — the worst kind. Having a fresh reviewer trace the concurrent access paths before the code left the branch was the difference between catching it now and diagnosing a native crash later.
