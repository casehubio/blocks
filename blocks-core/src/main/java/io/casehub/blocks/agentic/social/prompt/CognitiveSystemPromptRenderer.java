package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.CognitionConfig;
import io.casehub.eidos.api.AgentConstraint;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentPromptContext;
import io.casehub.eidos.api.ConstraintSeverity;
import io.casehub.eidos.api.SystemPromptRenderer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public class CognitiveSystemPromptRenderer implements SystemPromptRenderer {

    private final CognitionConfig config;

    public CognitiveSystemPromptRenderer(CognitionConfig config) {
        this.config = config;
    }

    @Override
    public RenderedPrompt render(AgentDescriptor descriptor, AgentPromptContext context) {
        var sb = new StringBuilder();

        sb.append("# ").append(descriptor.name()).append("\n\n");
        if (descriptor.briefing() != null && !descriptor.briefing().isBlank()) {
            sb.append(descriptor.briefing()).append("\n\n");
        }

        var hardConstraints = descriptor.constraints() != null
                ? descriptor.constraints().stream()
                    .filter(c -> c.severity() == ConstraintSeverity.HARD)
                    .toList()
                : List.<AgentConstraint>of();
        if (!hardConstraints.isEmpty()) {
            sb.append("## Prime Directives\n\n");
            for (var c : hardConstraints) {
                sb.append("- ").append(c.description()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Your Mind\n\n");
        sb.append(CognitivePreambleGenerator.generate(config)).append("\n");

        var content = sb.toString().stripTrailing();
        var hash = shortHash(content);
        return new RenderedPrompt(content, RenderFormat.MARKDOWN, hash, hash, false);
    }

    private static String shortHash(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes())).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
