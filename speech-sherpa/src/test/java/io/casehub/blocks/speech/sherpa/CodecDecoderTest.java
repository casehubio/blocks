package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodecDecoderTest {

    @Test
    void sampleRateReturnsConfiguredValue() {
        var decoder = new CodecDecoder(null, 44100);
        assertThat(decoder.sampleRate()).isEqualTo(44100);
    }

    @Test
    void reshapeCodecTokensTransposesFramesToCodebooks() {
        // 3 frames, 2 codebooks: frame layout [cb0_f0, cb0_f1, cb0_f2, cb1_f0, cb1_f1, cb1_f2]
        // → ORT layout [1, numCodebooks, numFrames] = [1, 2, 3]
        int numCodebooks = 2;
        int numFrames = 3;
        int[][] frames = {
                {10, 20}, // frame 0: codebook0=10, codebook1=20
                {11, 21}, // frame 1: codebook0=11, codebook1=21
                {12, 22}, // frame 2: codebook0=12, codebook1=22
        };

        long[] reshaped = CodecDecoder.reshapeForOrt(frames, numCodebooks);

        // Expected: codebook-major order [10, 11, 12, 20, 21, 22]
        assertThat(reshaped).containsExactly(10, 11, 12, 20, 21, 22);
    }

    @Test
    void reshapeRejectsEmptyFrames() {
        assertThatThrownBy(() -> CodecDecoder.reshapeForOrt(new int[0][], 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
