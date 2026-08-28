package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Audio8ConfigTest {

    @Test
    void defaultsUsesZeroPointOneBVariant(@TempDir Path tempDir) {
        var config = Audio8Config.defaults(tempDir);
        assertThat(config.variant()).isEqualTo("0.1b");
        assertThat(config.numThreads()).isEqualTo(4);
        assertThat(config.provider()).isEqualTo("cpu");
    }

    @Test
    void defaultsSetsAutoRegressiveParams(@TempDir Path tempDir) {
        var config = Audio8Config.defaults(tempDir);
        assertThat(config.maxTokens()).isEqualTo(1024);
        assertThat(config.temperature()).isEqualTo(0.7f);
        assertThat(config.topP()).isEqualTo(0.9f);
        assertThat(config.topK()).isEqualTo(50);
    }

    @Test
    void defaultsAcceptsVariantOverride(@TempDir Path tempDir) {
        var config = Audio8Config.defaults(tempDir, "0.6b");
        assertThat(config.variant()).isEqualTo("0.6b");
    }

    @Test
    void rejectsNullModelDir() {
        assertThatThrownBy(() -> Audio8Config.defaults(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsZeroThreads(@TempDir Path tempDir) {
        assertThatThrownBy(() -> new Audio8Config(tempDir, "0.1b", 0, "cpu", 1024, 0.7f, 0.9f, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMaxTokens(@TempDir Path tempDir) {
        assertThatThrownBy(() -> new Audio8Config(tempDir, "0.1b", 4, "cpu", -1, 0.7f, 0.9f, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void audio8ModelDirReturnsExpectedPath() {
        Path dir = Provisioner.audio8ModelDir("0.1b");
        assertThat(dir.toString()).endsWith("audio8-tts/0.1b");
    }

    @Test
    void audio8ModelDirIncludesVariant() {
        assertThat(Provisioner.audio8ModelDir("0.6b").getFileName().toString()).isEqualTo("0.6b");
    }

    @Test
    void ensureAudio8ModelRejectsUnknownVariant() {
        assertThatThrownBy(() -> Provisioner.ensureAudio8Model("unknown"))
                .isInstanceOf(SherpaException.class)
                .hasMessageContaining("Unknown Audio8 variant");
    }
}
