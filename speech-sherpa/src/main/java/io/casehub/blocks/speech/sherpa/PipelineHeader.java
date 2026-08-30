package io.casehub.blocks.speech.sherpa;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record PipelineHeader(
        String name,
        int sampleRate,
        Map<String, List<String>> stageModels,
        @Nullable ExecutionProviderConfig provider,
        Map<String, String> metadata
) {}
