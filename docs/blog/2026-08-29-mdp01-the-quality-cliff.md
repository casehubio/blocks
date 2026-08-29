---
layout: post
title: "The Quality Cliff"
date: 2026-08-29
entry_type: note
subtype: diary
series: issue-213-fish-speech-tts
projects: [casehubio/blocks]
tags: [tts, onnx, audio8, quality, cosyvoice, streaming, lip-sync, avatar]
---

The pipeline works. Audio8 DualAR synthesis end-to-end through the avatar WebSocket — tokenize text, build prompt with default voice, run slow AR and fast AR inference, decode codec frames to 44.1kHz audio, enrich with espeak phoneme timing, convert to visemes, send to the 3D avatar. Ninety-eight viseme frames driving lip-sync on a ReadyPlayerMe head. The mouth moves in time with the speech.

The speech sounds terrible.

## What "0.1B INT8" actually means

I'd been working with the architecture — KV-cache management, Mamba SSM states, codec decoding — without hearing the output. When I finally ran the demo app with Audio8 selected in the dropdown, the result was metallic and robotic. MOS somewhere around 2.8. Kokoro, which sits in the same dropdown at MOS 4.3, is in a different universe.

The gap makes sense in hindsight. Audio8's 0.1B variant is a 100-million parameter model quantised to INT8. The DualAR architecture — the same lineage that makes Fish Audio's online service sound near-human — needs scale and precision to do its thing. Strip both and you get the structure without the substance. The autoregressive generation still works correctly: semantic tokens, codec codebooks, EOS detection, degenerate loop guards. It just doesn't sound good at this scale.

The 12-second latency per sentence compounds the problem. Autoregressive inference is O(n) by nature — every token requires a full slow AR forward pass with KV-cache scatter updates. On an M2 CPU, that's fundamentally slow. We implemented streaming synthesis with windowed overlap-subtract codec decoding — the same progressive chunk delivery that the Python reference uses — but haven't wired it into the WebSocket yet. Streaming would hide the latency by delivering audio chunks as they're generated rather than waiting for the full sentence. The mechanism is there; the wiring is not.

## What the session actually delivered

The WebSocket integration is complete. `SpeechProducers` registers Audio8 alongside Kokoro and SherpaOnnx. Every non-VITS engine is now wrapped with `LipSyncEnricher` — Kokoro and SherpaOnnx gained lip-sync for free, not just Audio8. The `EspeakPhonemeAligner.withDefaults()` factory handles provisioning automatically.

The codec encoder is provisioned from HuggingFace's `registration/` subdirectory, so voice cloning works out of the box. `ModelProvisioningService` downloads Audio8 models in the background alongside Kokoro and VITS.

Claude caught a resource leak during the close-out code review: `Audio8TextToSpeech.close()` was releasing the voice registry but not the ONNX sessions themselves. The `Generator` and `Decoder` functional interfaces hid the lifecycle — the `DualARLoop` and `CodecDecoder` created in `fromModelDir()` were captured in lambdas and never closed. We added an `ownedResources` array to track closeables across the abstraction boundary.

## The pivot

I tried Fish Audio's online demo before this issue started. The quality was stunning — indistinguishable from a human voice in casual listening. I was hoping Audio8's ONNX port would get close. It doesn't.

The architecture validated here — composable `TextToSpeechService`, `LipSyncEnricher` decorator, `PhonemeAligner` SPI, streaming synthesis, voice registry — is engine-agnostic. A better model slots in without changing the surrounding infrastructure. The question is which model.

CosyVoice2 from Qwen looks strong. The 0.5B model has pre-exported ONNX available on HuggingFace, reports MOS above 5.0 on their scale, supports streaming with 150ms first-chunk latency, and is half the download size of Audio8. CosyVoice3 ONNX also exists with fully PyTorch-free inference. The composable pipeline is ready — the next engine just needs to implement `TextToSpeechService` and plug in.
