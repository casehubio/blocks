package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.AttributedState;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MentalModelSnapshot;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class MentalModelPromptSection implements PromptSection {

    private final MentalModelOrchestrator mentalModel;

    public MentalModelPromptSection(MentalModelOrchestrator mentalModel) {
        this.mentalModel = mentalModel;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        var snapshots = mentalModel.activeSnapshots(context.agentId(), context.tenantId());
        if (snapshots.isEmpty()) {
            return null;
        }
        var target = context.subjectId() != null
                ? snapshots.stream().filter(s -> s.subjectId().equals(context.subjectId())).findFirst().orElse(null)
                : snapshots.get(0);
        if (target == null) {
            return null;
        }
        return render(target);
    }

    private static @Nullable String render(MentalModelSnapshot snapshot) {
        var sb = new StringBuilder();
        appendDimension(sb, "What you believe about the user", snapshot.beliefs());
        appendDimension(sb, "What you think they want", snapshot.desires());
        appendDimension(sb, "Their likely intentions", snapshot.intentions());
        if (sb.isEmpty()) {
            return null;
        }
        return "Theory of Mind (" + snapshot.subjectId() + "):" + sb;
    }

    private static void appendDimension(StringBuilder sb, String label, List<AttributedState> states) {
        var relevant = states.stream().filter(s -> s.confidence() >= 0.3).toList();
        if (!relevant.isEmpty()) {
            sb.append("\n").append(label).append(":");
            for (var state : relevant) {
                sb.append("\n  - ").append(state.description())
                  .append(" (confidence: ").append(String.format("%.1f", state.confidence())).append(")");
            }
        }
    }
}
