---
layout: post
title: "The Tensor Nobody Reads"
date: 2026-08-26
entry_type: note
subtype: diary
projects: [casehubio/blocks]
tags: [speech, tts, phoneme-timing, onnx, ffm, gector]
series: issue-194-talking-avatar-tier1
---

# The Tensor Nobody Reads

Every time VITS synthesises speech, its duration predictor computes exactly
how many spectrogram frames each phoneme should occupy. The model uses this
internally to stretch the latent representation before decoding to audio.
Then it throws the result away. The C API returns samples, sample count,
sample rate — and nothing else.

The avatar lip-sync work needs per-phoneme timing. The obvious path is
forced alignment: synthesise audio, then run a separate STT model to
figure out when each phoneme was spoken. That's a 75MB Whisper download
and a full inference pass on audio we just generated. It works, but it
felt wrong — the timing data already exists inside the model. We're just
not allowed to see it.

## Reading the Graph

ONNX models are protobuf. You can open them, inspect the computation
graph, and trace any intermediate tensor. I loaded a Piper VITS model
and followed the duration predictor's output through four nodes:

```
/dp/Split_output_0  →  /Exp  →  /Mul (× mask)  →  /Mul_1 (× length_scale)  →  /Ceil
```

`/Ceil_output_0` is the final result — integer frame counts per phoneme.
The Piper export script discards everything except the audio waveform:
`output_names=["output"]`. But ONNX lets you add intermediate tensors as
declared outputs after the fact. Three lines of Python:

```python
model.graph.output.append(
    helper.make_tensor_value_info('/Ceil_output_0', TensorProto.FLOAT, None))
onnx.save(model, model_path)
```

Run inference, read both outputs. Duration sum from frames: 220.6ms.
Audio duration: 220.6ms. Exact match.

## Bypassing sherpa-onnx

With the timing data accessible, the question became: how to read it
from Java? sherpa-onnx's C API only returns `SherpaOnnxGeneratedAudio`
— three fields, none of them durations. Even if we patched the model,
sherpa-onnx would ignore the extra output.

The onnxruntime library is already loaded in the process — sherpa-onnx
depends on it. Its C API uses a vtable pattern: one exported symbol
(`OrtGetApiBase`) returns a struct of function pointers. About a dozen
downcall handles cover session creation, tensor I/O, and cleanup. The
FFM bindings follow the same pattern as our existing `SherpaLibrary` —
resolve symbols, wrap in `MethodHandle`, check status codes.

For phonemization (VITS needs phoneme IDs, not text), espeak-ng has a
straightforward C API. Four functions: init, set voice, text to
phonemes, terminate. All `synchronized` because espeak-ng uses global
state.

The result is `VitsTextToSpeech` — a `TextToSpeechService` that
composes espeak-ng phonemization, VITS blank token interspersing, and
onnxruntime inference into a single pass returning audio and exact
phoneme timing.

## The Blank Token Trap

VITS models are trained with pad tokens (ID 0) interspersed between
every phoneme. For N input tokens, the interspersed sequence has 2N+1
elements. Without this, the model receives malformed input — wrong
tensor shape, garbage audio.

The duration predictor outputs one value per element of the interspersed
sequence. The pad durations aren't silence — they represent transition
time between phonemes. They contribute to the cumulative timeline but
don't produce `PhonemeTiming` entries. This means the last phoneme's
end time won't equal the total audio duration. The gap is the trailing
pad durations — the fade-out that VITS allocates after the final phoneme.

## GECToR: Grammar by Tagging

With speech synthesis covered, the transcription cleanup pipeline needs
grammar correction. GECToR is Grammarly's sequence tagger — it predicts
per-token edit operations ($KEEP, $DELETE, $APPEND, $REPLACE, verb
transforms) rather than generating corrected text. Non-autoregressive
inference at 20-50ms per sentence.

The tokenizer question turned out to be the interesting part.
SentencePiece has no C API — it's C++ only. The "pure Java SentencePiece"
library that search results confidently recommend doesn't exist on Maven
Central. Vespa's search engine has a production-tested implementation that
does work — a Viterbi UNIGRAM segmenter in ~400 lines of Java, Apache
2.0 licensed, using protobuf-java to parse `.model` files.

The port simplified the protobuf dependency further. SentencePiece model
files are just protobuf: repeated messages with a piece string, a score
float, and a type enum. Three fields. Using `CodedInputStream` to read
them directly is about 30 lines — no proto compilation step, no generated
classes, no new build dependency.

The ORT singleton was the other interesting refactor. With GECToR adding
a second concurrent ONNX session alongside VitsTextToSpeech, the
per-session environment had to go. The env now lives at `load()` time in
`Arena.global()` — created once, shared across every session for the
process lifetime. Sessions create and release their own ORT sessions but
never touch the env.
