package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NativeJarExtractorTest {

    @TempDir Path tempDir;

    @Test
    void extractsResourceFromClasspath() throws Exception {
        Path target = tempDir.resolve("extracted").resolve("libdummy.so");

        boolean extracted = NativeJarExtractor.extractResource(
                "META-INF/native/sherpa-onnx/1.13.6/test-platform/libdummy.so",
                target);

        assertThat(extracted).isTrue();
        assertThat(target).exists();
        assertThat(Files.readString(target)).contains("dummy");
    }

    @Test
    void returnsFalseWhenResourceNotFound() {
        Path target = tempDir.resolve("extracted").resolve("libfoo.so");

        boolean extracted = NativeJarExtractor.extractResource(
                "META-INF/native/sherpa-onnx/1.13.6/nonexistent/libfoo.so",
                target);

        assertThat(extracted).isFalse();
        assertThat(target).doesNotExist();
    }

    @Test
    void skipsExtractionWhenTargetDirExists() throws Exception {
        Path targetDir = tempDir.resolve("already-exists");
        Files.createDirectories(targetDir);

        boolean extracted = NativeJarExtractor.extractIfAvailable(targetDir);

        assertThat(extracted).isFalse();
    }
}
