package io.casehub.blocks.speech.sherpa;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ModelPatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void patch_addsSecondOutput() throws Exception {
        Path modelDir = createTestModelDir(1);
        boolean result = ModelPatcher.patch(modelDir);
        assertThat(result).isTrue();
        assertThat(countOutputs(modelDir)).isEqualTo(2);
    }

    @Test
    void patch_idempotent() throws Exception {
        Path modelDir = createTestModelDir(1);
        ModelPatcher.patch(modelDir);
        boolean secondResult = ModelPatcher.patch(modelDir);
        assertThat(secondResult).isFalse();
        assertThat(countOutputs(modelDir)).isEqualTo(2);
    }

    @Test
    void patch_preservesOriginalOutput() throws Exception {
        Path modelDir = createTestModelDir(1);
        ModelPatcher.patch(modelDir);
        assertThat(getOutputName(modelDir, 0)).isEqualTo("output");
        assertThat(getOutputName(modelDir, 1)).isEqualTo("/Ceil_output_0");
    }

    @Test
    void patch_alreadyPatchedModel() throws Exception {
        Path modelDir = createTestModelDir(2);
        boolean result = ModelPatcher.patch(modelDir);
        assertThat(result).isFalse();
    }

    private Path createTestModelDir(int numOutputs) throws IOException {
        byte[] modelBytes = buildMinimalOnnxModel(numOutputs);
        Path modelFile = tempDir.resolve("model.onnx");
        Files.write(modelFile, modelBytes);
        return tempDir;
    }

    private int countOutputs(Path modelDir) throws IOException {
        byte[] bytes = Files.readAllBytes(modelDir.resolve("model.onnx"));
        UnknownFieldSet model = UnknownFieldSet.parseFrom(bytes);
        byte[] graphBytes = model.getField(7).getLengthDelimitedList().getFirst().toByteArray();
        UnknownFieldSet graph = UnknownFieldSet.parseFrom(graphBytes);
        UnknownFieldSet.Field outputField = graph.getField(12);
        return outputField == null ? 0 : outputField.getLengthDelimitedList().size();
    }

    private String getOutputName(Path modelDir, int index) throws IOException {
        byte[] bytes = Files.readAllBytes(modelDir.resolve("model.onnx"));
        UnknownFieldSet model = UnknownFieldSet.parseFrom(bytes);
        byte[] graphBytes = model.getField(7).getLengthDelimitedList().getFirst().toByteArray();
        UnknownFieldSet graph = UnknownFieldSet.parseFrom(graphBytes);
        ByteString outputBytes = graph.getField(12).getLengthDelimitedList().get(index);
        UnknownFieldSet output = UnknownFieldSet.parseFrom(outputBytes);
        return output.getField(1).getLengthDelimitedList().getFirst().toStringUtf8();
    }

    static byte[] buildMinimalOnnxModel(int numOutputs) throws IOException {
        // ValueInfoProto for "output" (field 1 = name)
        UnknownFieldSet outputValueInfo = UnknownFieldSet.newBuilder()
                .addField(1, UnknownFieldSet.Field.newBuilder()
                        .addLengthDelimited(ByteString.copyFromUtf8("output")).build())
                .build();

        // GraphProto with output(s) (field 12 = output)
        UnknownFieldSet.Field.Builder outputFieldBuilder = UnknownFieldSet.Field.newBuilder()
                .addLengthDelimited(outputValueInfo.toByteString());

        if (numOutputs >= 2) {
            UnknownFieldSet ceilOutput = UnknownFieldSet.newBuilder()
                    .addField(1, UnknownFieldSet.Field.newBuilder()
                            .addLengthDelimited(ByteString.copyFromUtf8("/Ceil_output_0")).build())
                    .build();
            outputFieldBuilder.addLengthDelimited(ceilOutput.toByteString());
        }

        UnknownFieldSet graph = UnknownFieldSet.newBuilder()
                .addField(1, UnknownFieldSet.Field.newBuilder()
                        .addLengthDelimited(ByteString.copyFromUtf8("test_graph")).build())
                .addField(12, outputFieldBuilder.build())
                .build();

        // ModelProto (field 1 = ir_version, field 7 = graph)
        UnknownFieldSet model = UnknownFieldSet.newBuilder()
                .addField(1, UnknownFieldSet.Field.newBuilder().addVarint(7).build())
                .addField(7, UnknownFieldSet.Field.newBuilder()
                        .addLengthDelimited(graph.toByteString()).build())
                .build();

        return model.toByteArray();
    }
}
