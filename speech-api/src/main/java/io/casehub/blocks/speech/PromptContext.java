package io.casehub.blocks.speech;

import org.jspecify.annotations.Nullable;

public record PromptContext(String agentId, String tenantId, @Nullable String subjectId) {}
