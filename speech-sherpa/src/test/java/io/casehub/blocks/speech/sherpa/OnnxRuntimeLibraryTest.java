package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("onnxRuntimeAvailable")
class OnnxRuntimeLibraryTest {

    @Test
    void load_resolvesApiBase() {
        var lib = OnnxRuntimeLibrary.load();
        assertThat(lib).isNotNull();
    }

    @Test
    void createSession_andQueryOutputs() {
        var lib = OnnxRuntimeLibrary.load();
        try (var session = lib.createSession(testModelPath(), 1)) {
            assertThat(session.outputCount()).isEqualTo(1);
            assertThat(session.outputName(0)).isEqualTo("y");
            assertThat(session.inputCount()).isEqualTo(1);
            assertThat(session.inputName(0)).isEqualTo("x");
        }
    }

    @Test
    void run_identityModel() {
        var lib = OnnxRuntimeLibrary.load();
        try (var session = lib.createSession(testModelPath(), 1);
             Arena arena = Arena.ofConfined()) {

            float[] inputData = {42.0f};
            MemorySegment inputSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, inputData);
            long[] inputShape = {1};

            float[] output = session.runFloat(
                    new String[]{"x"}, new MemorySegment[]{inputSeg}, new long[][]{inputShape},
                    new String[]{"y"}, arena);

            assertThat(output).containsExactly(42.0f);
        }
    }

    @Test
    void run_multipleOutputNames() {
        var lib = OnnxRuntimeLibrary.load();
        try (var session = lib.createSession(testModelPath(), 1);
             Arena arena = Arena.ofConfined()) {

            float[] inputData = {7.5f};
            MemorySegment inputSeg = arena.allocateFrom(ValueLayout.JAVA_FLOAT, inputData);
            long[] inputShape = {1};

            float[] output = session.runFloat(
                    new String[]{"x"}, new MemorySegment[]{inputSeg}, new long[][]{inputShape},
                    new String[]{"y"}, arena);

            assertThat(output).hasSize(1);
            assertThat(output[0]).isEqualTo(7.5f);
        }
    }

    static Path testModelPath() {
        var url = OnnxRuntimeLibraryTest.class.getResource("/test-identity.onnx");
        if (url == null) {
            throw new IllegalStateException("test-identity.onnx not found on classpath");
        }
        return Path.of(url.getPath());
    }

    static boolean onnxRuntimeAvailable() {
        try {
            OnnxRuntimeLibrary.load();
            return true;
        } catch (UnsatisfiedLinkError | SherpaException e) {
            return false;
        }
    }
}
