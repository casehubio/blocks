package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorOutputTest {

    @Test void speechTokenOutputWrapsTokens() {
        var out = new GeneratorOutput.SpeechTokenOutput(new int[]{100, 200});
        assertThat(out.speechTokens()).containsExactly(100, 200);
        assertThat(out).isInstanceOf(GeneratorOutput.class);
    }

    @Test void codecFrameOutputWrapsFrames() {
        var out = new GeneratorOutput.CodecFrameOutput(new int[][]{{1, 2}, {3, 4}});
        assertThat(out.codecFrames().length).isEqualTo(2);
        assertThat(out.codecFrames()[0]).containsExactly(1, 2);
    }

    @Test void exhaustiveSwitchCoversAllVariants() {
        GeneratorOutput speech = new GeneratorOutput.SpeechTokenOutput(new int[]{1});
        GeneratorOutput codec = new GeneratorOutput.CodecFrameOutput(new int[][]{{1}});
        assertThat(length(speech)).isEqualTo(1);
        assertThat(length(codec)).isEqualTo(1);
    }

    private int length(GeneratorOutput out) {
        return switch (out) {
            case GeneratorOutput.SpeechTokenOutput s -> s.speechTokens().length;
            case GeneratorOutput.CodecFrameOutput c -> c.codecFrames().length;
        };
    }
}
