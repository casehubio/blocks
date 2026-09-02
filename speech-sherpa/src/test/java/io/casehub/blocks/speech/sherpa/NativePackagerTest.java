package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativePackagerTest {

    @TempDir Path tempDir;

    @Test
    void rejectsNoArguments() {
        assertThatThrownBy(() -> NativePackager.packageNative(new String[]{}, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform ID");
    }

    @Test
    void rejectsUnknownPlatform() {
        assertThatThrownBy(() -> NativePackager.packageNative(new String[]{"unknown-arch"}, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-arch");
    }
}
