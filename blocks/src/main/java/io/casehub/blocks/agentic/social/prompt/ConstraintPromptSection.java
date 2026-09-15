package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.eidos.api.AgentConstraint;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class ConstraintPromptSection implements PromptSection {

    private final List<AgentConstraint> constraints;

    public ConstraintPromptSection(List<AgentConstraint> constraints) {
        this.constraints = List.copyOf(constraints);
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        if (constraints.isEmpty()) return null;
        var sb = new StringBuilder("Your constraints:");
        for (var c : constraints) {
            sb.append("\n- [").append(c.severity().name()).append("] ")
              .append(c.description());
        }
        return sb.toString();
    }
}
