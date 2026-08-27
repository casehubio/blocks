# TTS Model Filename Auto-Detection

**Issue:** casehubio/blocks#177
**Scale:** XS | **Complexity:** Low
**Date:** 2026-08-26

## Summary

Replace hardcoded `model.onnx` in `SherpaOnnxTextToSpeech.buildTtsConfig()` with directory scanning. VITS Piper models use model-specific filenames (e.g. `en_US-amy-low.onnx`).

## Change

Add private `findTtsModel(Path modelDir)` to `SherpaOnnxTextToSpeech`:
- List `.onnx` files in `modelDir`
- Single file → use it
- Multiple files → prefer `model.onnx` if present, else use first alphabetically
- Zero files → throw `SherpaException`

Replace line 101: `modelDir.resolve("model.onnx")` → `findTtsModel(modelDir)`

## Test

- Directory with `model.onnx` → returns `model.onnx` (backward compat)
- Directory with `en_US-amy-low.onnx` → returns it
- Directory with both → prefers `model.onnx`
- Empty directory → throws SherpaException

## References

- SherpaOnnxTextToSpeech.java:95-113 — buildTtsConfig with hardcoded path
- SherpaOnnxStreamingSpeechToText.java:118-133 — existing findModel pattern
- D7 in decisions.md
