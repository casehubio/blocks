package io.casehub.blocks.agentic.social;

import io.casehub.eidos.api.AgentDescriptor;
import org.jspecify.annotations.Nullable;

public record CognitionTickContext(
        String agentId,
        String tenantId,
        @Nullable AgentDescriptor descriptor,
        SubjectResolver resolver
) {}
