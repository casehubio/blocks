package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SherpaConfigTest {

    @Test
    void defaultsUseReasonableValues() {
        SherpaConfig config = SherpaConfig.defaults(Path.of("/models"));

        assertThat(config.modelDir()).isEqualTo(Path.of("/models"));
        int expectedThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        assertThat(config.numThreads()).isEqualTo(expectedThreads);
        assertThat(config.provider()).isEqualTo("cpu");
    }

    @Test
    void rejectsNullModelDir() {
        assertThatThrownBy(() -> SherpaConfig.defaults(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsZeroThreads() {
        assertThatThrownBy(() -> new SherpaConfig(Path.of("/models"), 0, "cpu"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numThreads");
    }

    @Test
    void rejectsNegativeThreads() {
        assertThatThrownBy(() -> new SherpaConfig(Path.of("/models"), -1, "cpu"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullProvider() {
        assertThatThrownBy(() -> new SherpaConfig(Path.of("/models"), 2, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void acceptsCoreMlProvider() {
        SherpaConfig config = new SherpaConfig(Path.of("/models"), 4, "coreml");

        assertThat(config.provider()).isEqualTo("coreml");
        assertThat(config.numThreads()).isEqualTo(4);
    }

    @Test
    void defaultsNoArg_usesDefaultModelDir() {
        SherpaConfig config = SherpaConfig.defaults();
        assertThat(config.modelDir().toString()).endsWith("sherpa-onnx-whisper-tiny");
        int expectedThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        assertThat(config.numThreads()).isEqualTo(expectedThreads);
        assertThat(config.provider()).isEqualTo("cpu");
    }
}
