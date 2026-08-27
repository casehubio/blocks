package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KokoroConfigTest {

    @TempDir Path tempDir;

    @Test
    void defaultsUsesVoiceZero() {
        var config = KokoroConfig.defaults(tempDir);
        assertThat(config.voiceId()).isZero();
        assertThat(config.lengthScale()).isEqualTo(1.0f);
        assertThat(config.numThreads()).isEqualTo(2);
        assertThat(config.provider()).isEqualTo("cpu");
    }

    @Test
    void defaultsWithVoiceId() {
        var config = KokoroConfig.defaults(tempDir, 5);
        assertThat(config.voiceId()).isEqualTo(5);
    }

    @Test
    void rejectsNullModelDir() {
        assertThatThrownBy(() -> KokoroConfig.defaults(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNonPositiveThreads() {
        assertThatThrownBy(() -> new KokoroConfig(tempDir, 0, 1.0f, 0, "cpu"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveLengthScale() {
        assertThatThrownBy(() -> new KokoroConfig(tempDir, 0, 0.0f, 2, "cpu"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
