package io.casehub.blocks.speech.sherpa;

import org.jspecify.annotations.Nullable;

final class CosyVoice3VoiceEncoder implements TtsVoiceEncoder {

    static final MelConfig CAMPPLUS_MEL = new MelConfig(16000, 400, 160, 80, 20f, 7600f);
    static final MelConfig WHISPER_MEL = new MelConfig(16000, 400, 160, 128, 0f, 8000f);
    static final MelConfig FLOW_MEL = new MelConfig(24000, 1024, 256, 80, 0f, 12000f);

    private final SpeakerExtractor speakerExtractor;
    private final TokenExtractor tokenExtractor;
    private final CosyVoice3Manifest manifest;

    @FunctionalInterface
    interface SpeakerExtractor {
        float[] extract(float[][] logMel);
    }

    @FunctionalInterface
    interface TokenExtractor {
        int[] extract(float[][] logMel);
    }

    CosyVoice3VoiceEncoder(SpeakerExtractor speakerExtractor,
                           TokenExtractor tokenExtractor,
                           CosyVoice3Manifest manifest) {
        this.speakerExtractor = speakerExtractor;
        this.tokenExtractor = tokenExtractor;
        this.manifest = manifest;
    }

    static CosyVoice3VoiceEncoder fromSessions(OnnxRuntimeLibrary.Session campplus,
                                               OnnxRuntimeLibrary.Session speechTokenizer,
                                               CosyVoice3Manifest manifest) {
        SpeakerExtractor speaker = logMel -> {
            int nMels = logMel.length;
            int frames = logMel[0].length;
            float[] flat = new float[frames * nMels];
            for (int f = 0; f < frames; f++) {
                for (int m = 0; m < nMels; m++) {
                    flat[f * nMels + m] = logMel[m][f];
                }
            }
            try (var arena = java.lang.foreign.Arena.ofConfined()) {
                var data = arena.allocateFrom(java.lang.foreign.ValueLayout.JAVA_FLOAT, flat);
                return campplus.runFloat(
                        new String[]{"fbank"},
                        new java.lang.foreign.MemorySegment[]{data},
                        new long[][]{{1, frames, nMels}},
                        new String[]{"embs"},
                        arena);
            }
        };

        TokenExtractor tokens = logMel -> {
            int nMels = logMel.length;
            int frames = logMel[0].length;
            float[] flat = new float[nMels * frames];
            for (int m = 0; m < nMels; m++) {
                System.arraycopy(logMel[m], 0, flat, m * frames, frames);
            }
            try (var arena = java.lang.foreign.Arena.ofConfined()) {
                var data = arena.allocateFrom(java.lang.foreign.ValueLayout.JAVA_FLOAT, flat);
                float[] raw = speechTokenizer.runFloat(
                        new String[]{"mel"},
                        new java.lang.foreign.MemorySegment[]{data},
                        new long[][]{{1, nMels, frames}},
                        new String[]{"codes"},
                        arena);
                int[] result = new int[raw.length];
                for (int i = 0; i < raw.length; i++) result[i] = Math.round(raw[i]);
                return result;
            }
        };

        return new CosyVoice3VoiceEncoder(speaker, tokens, manifest);
    }

    @Override
    public VoiceData encode(byte[] audioData, @Nullable String transcriptText) {
        if (transcriptText == null || transcriptText.isBlank()) {
            throw new IllegalArgumentException(
                    "CosyVoice3 voice cloning requires a transcript of the reference audio");
        }

        WavData wav;
        try {
            wav = WavReader.parse(audioData);
        } catch (java.io.IOException e) {
            throw new SherpaException("Failed to parse WAV audio data", e);
        }
        float[] audio16k = resampleIfNeeded(wav.samples(), wav.sampleRate(), 16000);
        float[] audio24k = resampleIfNeeded(wav.samples(), wav.sampleRate(), 24000);

        float[] speakerEmbedding = extractSpeakerEmbedding(audio16k);
        int[] speechTokens = extractSpeechTokens(audio16k);
        float[] promptMel = extractPromptMel(audio24k);

        return new VoiceData.EmbeddingVoiceData(speakerEmbedding, speechTokens,
                promptMel, transcriptText);
    }

    private float[] extractSpeakerEmbedding(float[] audio16k) {
        float[][] mel = MelSpectrogram.compute(audio16k, CAMPPLUS_MEL);
        float[][] logMel = MelSpectrogram.logMel(mel);
        MelSpectrogram.meanNormalize(logMel);
        return speakerExtractor.extract(logMel);
    }

    private int[] extractSpeechTokens(float[] audio16k) {
        float[][] mel = MelSpectrogram.compute(audio16k, WHISPER_MEL);
        float[][] logMel = whisperLog10Normalize(mel);
        return tokenExtractor.extract(logMel);
    }

    private float[] extractPromptMel(float[] audio24k) {
        float[][] mel = MelSpectrogram.compute(audio24k, FLOW_MEL);
        float[][] logMel = MelSpectrogram.logMel(mel);
        int frames = logMel[0].length;
        int nMels = logMel.length;
        float[] flat = new float[nMels * frames];
        for (int m = 0; m < nMels; m++) {
            System.arraycopy(logMel[m], 0, flat, m * frames, frames);
        }
        return flat;
    }

    private static float[][] whisperLog10Normalize(float[][] mel) {
        float[][] result = new float[mel.length][];
        for (int m = 0; m < mel.length; m++) {
            result[m] = new float[mel[m].length];
            for (int f = 0; f < mel[m].length; f++) {
                result[m][f] = (float) Math.log10(Math.max(mel[m][f], 1e-10));
            }
        }
        float maxVal = Float.NEGATIVE_INFINITY;
        for (float[] row : result) {
            for (float v : row) {
                if (v > maxVal) maxVal = v;
            }
        }
        float floor = maxVal - 8.0f;
        for (float[] row : result) {
            for (int i = 0; i < row.length; i++) {
                row[i] = (Math.max(row[i], floor) + 4.0f) / 4.0f;
            }
        }
        return result;
    }

    private static float[] resampleIfNeeded(float[] audio, int sourceRate, int targetRate) {
        if (sourceRate == targetRate) return audio;
        return AudioResampler.resample(audio, sourceRate, targetRate);
    }
}
