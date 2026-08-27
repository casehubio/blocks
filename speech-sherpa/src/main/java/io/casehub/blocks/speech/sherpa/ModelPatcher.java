package io.casehub.blocks.speech.sherpa;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

final class ModelPatcher {

    private static final System.Logger LOG = System.getLogger("casehub-speech");
    static final String DURATION_OUTPUT_NAME = "/Ceil_output_0";
    private static final int MODEL_GRAPH_FIELD = 7;
    private static final int GRAPH_OUTPUT_FIELD = 12;
    private static final int VALUE_INFO_NAME_FIELD = 1;
    private static final int VALUE_INFO_TYPE_FIELD = 2;
    private static final int TYPE_TENSOR_FIELD = 1;
    private static final int TENSOR_ELEM_TYPE_FIELD = 1;
    private static final int ONNX_FLOAT = 1;

    private ModelPatcher() {}

    static boolean patch(Path modelDir) {
            Path modelFile = findOnnxModel(modelDir);
            try {
                byte[]          bytes = Files.readAllBytes(modelFile);
                UnknownFieldSet model = UnknownFieldSet.parseFrom(bytes);

                UnknownFieldSet.Field graphField = model.getField(MODEL_GRAPH_FIELD);
                if (graphField == null || graphField.getLengthDelimitedList().isEmpty()) {
                    throw new SherpaException("ONNX model has no graph: " + modelFile);
                }

                byte[]          graphBytes = graphField.getLengthDelimitedList().getFirst().toByteArray();
                UnknownFieldSet graph      = UnknownFieldSet.parseFrom(graphBytes);

                if (hasOutput(graph, DURATION_OUTPUT_NAME)) {
                    LOG.log(System.Logger.Level.DEBUG, "Model already patched: {0}", modelFile);
                    return false;
                }

                if (!hasNodeOutput(graph, DURATION_OUTPUT_NAME)) {
                    LOG.log(System.Logger.Level.INFO,
                            "Model does not contain node output {0} — skipping patch: {1}",
                            DURATION_OUTPUT_NAME, modelFile);
                    return false;
                }

                String fileName = modelFile.getFileName().toString();
                Path   backup   = modelDir.resolve(fileName.replace(".onnx", ".unpatched.onnx"));
                if (!Files.exists(backup)) {
                    Files.copy(modelFile, backup);
                }

                UnknownFieldSet patchedGraph = addOutput(graph, DURATION_OUTPUT_NAME);
                UnknownFieldSet patchedModel = replaceField(model, MODEL_GRAPH_FIELD,
                                                            UnknownFieldSet.Field.newBuilder()
                                                                                 .addLengthDelimited(patchedGraph.toByteString())
                                                                                 .build());

                Path tempFile = Files.createTempFile(modelDir, ".patching-", ".onnx");
                try {
                    Files.write(tempFile, patchedModel.toByteArray());
                    Files.move(tempFile, modelFile, StandardCopyOption.ATOMIC_MOVE,
                               StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    Files.deleteIfExists(tempFile);
                    throw e;
                }

                LOG.log(System.Logger.Level.INFO, "Patched model with duration output: {0}", modelFile);
                return true;

            } catch (SherpaException e) {
                throw e;
            } catch (IOException e) {
                throw new SherpaException("Failed to patch model: " + modelFile, e);
            }
    }

    static void ensureUnpatchedBackup(Path modelDir) {
        Path   modelFile = findOnnxModel(modelDir);
        String fileName  = modelFile.getFileName().toString();
        Path   backup    = modelDir.resolve(fileName.replace(".onnx", ".unpatched.onnx"));
        if (Files.exists(backup)) {
            return;
        }
        try {
            byte[]                bytes      = Files.readAllBytes(modelFile);
            UnknownFieldSet       model      = UnknownFieldSet.parseFrom(bytes);
            UnknownFieldSet.Field graphField = model.getField(MODEL_GRAPH_FIELD);
            if (graphField == null || graphField.getLengthDelimitedList().isEmpty()) {
                return;
            }
            byte[]          graphBytes = graphField.getLengthDelimitedList().getFirst().toByteArray();
            UnknownFieldSet graph      = UnknownFieldSet.parseFrom(graphBytes);
            if (!hasOutput(graph, DURATION_OUTPUT_NAME)) {
                Files.copy(modelFile, backup);
                return;
            }
            UnknownFieldSet unpatchedGraph = removeOutput(graph, DURATION_OUTPUT_NAME);
            UnknownFieldSet unpatchedModel = replaceField(model, MODEL_GRAPH_FIELD,
                                                          UnknownFieldSet.Field.newBuilder()
                                                                               .addLengthDelimited(unpatchedGraph.toByteString())
                                                                               .build());
            Files.write(backup, unpatchedModel.toByteArray());
            LOG.log(System.Logger.Level.INFO, "Created unpatched backup: {0}", backup);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to create unpatched backup: " + e.getMessage());
        }
    }


    private static boolean hasOutput(UnknownFieldSet graph, String name) throws IOException {
        UnknownFieldSet.Field outputField = graph.getField(GRAPH_OUTPUT_FIELD);
        if (outputField == null) {
            return false;
        }
        for (ByteString outputBytes : outputField.getLengthDelimitedList()) {
            UnknownFieldSet output = UnknownFieldSet.parseFrom(outputBytes);
            UnknownFieldSet.Field nameField = output.getField(VALUE_INFO_NAME_FIELD);
            if (nameField != null && !nameField.getLengthDelimitedList().isEmpty()) {
                String outputName = nameField.getLengthDelimitedList().getFirst().toStringUtf8();
                if (name.equals(outputName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final int GRAPH_NODE_FIELD  = 1;
    private static final int NODE_OUTPUT_FIELD = 2;

    private static boolean hasNodeOutput(UnknownFieldSet graph, String outputName) throws IOException {
        UnknownFieldSet.Field nodeField = graph.getField(GRAPH_NODE_FIELD);
        if (nodeField == null) {
            return false;
        }
        for (ByteString nodeBytes : nodeField.getLengthDelimitedList()) {
            UnknownFieldSet       node    = UnknownFieldSet.parseFrom(nodeBytes);
            UnknownFieldSet.Field outputs = node.getField(NODE_OUTPUT_FIELD);
            if (outputs != null) {
                for (ByteString name : outputs.getLengthDelimitedList()) {
                    if (outputName.equals(name.toStringUtf8())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    private static UnknownFieldSet addOutput(UnknownFieldSet graph, String outputName) {
        UnknownFieldSet newOutput = buildValueInfoProto(outputName, ONNX_FLOAT);
        UnknownFieldSet.Field existingOutputField = graph.getField(GRAPH_OUTPUT_FIELD);

        UnknownFieldSet.Field.Builder outputFieldBuilder = UnknownFieldSet.Field.newBuilder();
        if (existingOutputField != null) {
            for (ByteString bs : existingOutputField.getLengthDelimitedList()) {
                outputFieldBuilder.addLengthDelimited(bs);
            }
        }
        outputFieldBuilder.addLengthDelimited(newOutput.toByteString());

        return replaceField(graph, GRAPH_OUTPUT_FIELD, outputFieldBuilder.build());
    }

    private static UnknownFieldSet removeOutput(UnknownFieldSet graph, String outputName) throws IOException {
        UnknownFieldSet.Field existingOutputField = graph.getField(GRAPH_OUTPUT_FIELD);
        if (existingOutputField == null) {
            return graph;
        }
        UnknownFieldSet.Field.Builder outputFieldBuilder = UnknownFieldSet.Field.newBuilder();
        for (ByteString outputBytes : existingOutputField.getLengthDelimitedList()) {
            UnknownFieldSet       output    = UnknownFieldSet.parseFrom(outputBytes);
            UnknownFieldSet.Field nameField = output.getField(VALUE_INFO_NAME_FIELD);
            if (nameField != null && !nameField.getLengthDelimitedList().isEmpty()) {
                String name = nameField.getLengthDelimitedList().getFirst().toStringUtf8();
                if (outputName.equals(name)) {
                    continue;
                }
            }
            outputFieldBuilder.addLengthDelimited(outputBytes);
        }
        return replaceField(graph, GRAPH_OUTPUT_FIELD, outputFieldBuilder.build());
    }


    private static UnknownFieldSet buildValueInfoProto(String name, int elemType) {
        UnknownFieldSet tensorType = UnknownFieldSet.newBuilder()
                .addField(TENSOR_ELEM_TYPE_FIELD,
                        UnknownFieldSet.Field.newBuilder().addVarint(elemType).build())
                .build();

        UnknownFieldSet type = UnknownFieldSet.newBuilder()
                .addField(TYPE_TENSOR_FIELD,
                        UnknownFieldSet.Field.newBuilder()
                                .addLengthDelimited(tensorType.toByteString()).build())
                .build();

        return UnknownFieldSet.newBuilder()
                .addField(VALUE_INFO_NAME_FIELD,
                        UnknownFieldSet.Field.newBuilder()
                                .addLengthDelimited(ByteString.copyFromUtf8(name)).build())
                .addField(VALUE_INFO_TYPE_FIELD,
                        UnknownFieldSet.Field.newBuilder()
                                .addLengthDelimited(type.toByteString()).build())
                .build();
    }

    private static UnknownFieldSet replaceField(UnknownFieldSet fieldSet, int fieldNumber,
                                                 UnknownFieldSet.Field replacement) {
        UnknownFieldSet.Builder builder = UnknownFieldSet.newBuilder();
        for (Map.Entry<Integer, UnknownFieldSet.Field> entry : fieldSet.asMap().entrySet()) {
            if (entry.getKey() != fieldNumber) {
                builder.addField(entry.getKey(), entry.getValue());
            }
        }
        builder.addField(fieldNumber, replacement);
        return builder.build();
    }

    private static Path findOnnxModel(Path modelDir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modelDir, "*.onnx")) {
            for (Path p : stream) {
                if (!p.getFileName().toString().endsWith(".onnx.json")) {
                    return p;
                }
            }
        } catch (IOException e) {
            throw new SherpaException("Failed to scan model directory: " + modelDir, e);
        }
        throw new SherpaException("No .onnx model found in " + modelDir);
    }
}
