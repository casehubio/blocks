package io.casehub.blocks.speech.sherpa;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class CodecDecoder implements AutoCloseable {

    private final OnnxRuntimeLibrary.Session session;
    private final int sampleRate;

    CodecDecoder(OnnxRuntimeLibrary.Session session, int sampleRate) {
        this.session = session;
        this.sampleRate = sampleRate;
    }

    int sampleRate() {
        return sampleRate;
    }

    float[] decode(int[][] frames) {
        if (frames == null || frames.length == 0) {
            throw new SherpaException("No codec frames to decode");
        }
        int    numCodebooks = frames[0].length;
        long[] codes        = reshapeForOrt(frames, numCodebooks);

        try (var arena = Arena.ofConfined()) {
            long[]        shape = {1, numCodebooks, frames.length};
            MemorySegment data  = arena.allocateFrom(ValueLayout.JAVA_LONG, codes);
            MemorySegment tensor = session.createTensor(
                    data, codes.length * ValueLayout.JAVA_LONG.byteSize(),
                    shape, OnnxRuntimeLibrary.INT64, arena);

            MemorySegment[] outputs = session.runRaw(
                    new String[]{"codes"}, new MemorySegment[]{tensor},
                    new String[]{session.outputName(0)}, arena);

            MemorySegment audioData = session.getTensorData(outputs[0], arena);
            long          count     = session.tensorElementCount(outputs[0], arena);
            float[] audio = audioData
                                    .reinterpret(count * ValueLayout.JAVA_FLOAT.byteSize())
                                    .toArray(ValueLayout.JAVA_FLOAT);

            for (MemorySegment out : outputs) {session.releaseValue(out);}
            session.releaseValue(tensor);
            return audio;
        }}

    @Override
    public void close() {
        if (session != null) session.close();
    }

    static long[] reshapeForOrt(int[][] frames, int numCodebooks) {
        if (frames.length == 0) {
            throw new IllegalArgumentException("frames must not be empty");
        }
        int numFrames = frames.length;
        long[] result = new long[numCodebooks * numFrames];
        for (int cb = 0; cb < numCodebooks; cb++) {
            for (int t = 0; t < numFrames; t++) {
                result[cb * numFrames + t] = frames[t][cb];
            }
        }
        return result;
    }
}
