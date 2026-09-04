package io.casehub.blocks.speech;

public record DiarizedSegment(long startMs, long endMs, String speakerLabel,
                               float[] samples, int sampleRate) {}
