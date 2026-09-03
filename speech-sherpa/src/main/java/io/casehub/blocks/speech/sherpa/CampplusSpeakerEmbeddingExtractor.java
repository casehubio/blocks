package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SpeakerEmbedding;
import io.casehub.blocks.speech.SpeakerEmbeddingExtractor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class CampplusSpeakerEmbeddingExtractor implements SpeakerEmbeddingExtractor {

    private static final MelConfig CAMPPLUS_MEL =
            new MelConfig(16000, 400, 160, 80, 20f, 7600f);
    private static final int TARGET_SAMPLE_RATE = 16000;

    private final OnnxRuntimeLibrary.Session session;

    public CampplusSpeakerEmbeddingExtractor(OnnxRuntimeLibrary.Session session) {
        this.session = session;
    }

    @Override
    public SpeakerEmbedding extract(float[] samples, int sampleRate) {
        float[] audio = sampleRate != TARGET_SAMPLE_RATE
                ? AudioResampler.resample(samples, sampleRate, TARGET_SAMPLE_RATE)
                : samples;

        float[][] mel = MelSpectrogram.compute(audio, CAMPPLUS_MEL);
        float[][] logMel = MelSpectrogram.logMel(mel);
        MelSpectrogram.meanNormalize(logMel);

        int nMels = logMel.length;
        int frames = logMel[0].length;
        float[] flat = new float[frames * nMels];
        for (int f = 0; f < frames; f++) {
            for (int m = 0; m < nMels; m++) {
                flat[f * nMels + m] = logMel[m][f];
            }
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocateFrom(ValueLayout.JAVA_FLOAT, flat);
            float[] embedding = session.runFloat(
                    new String[]{"input"},
                    new MemorySegment[]{data},
                    new long[][]{{1, frames, nMels}},
                    new String[]{"output"},
                    arena);
            return new SpeakerEmbedding(embedding, embedding.length);
        }
    }
}
