package io.casehub.blocks.agentic;

import java.util.stream.Collectors;

public final class AgentCardSupport {
    private AgentCardSupport() {}

    public static String buildCard(RoutingCandidate candidate, int index) {
        var sb = new StringBuilder();
        var name = candidateName(candidate, index);
        sb.append("- Agent: \"").append(name).append("\"");

        if (candidate.ref() instanceof AgentRef.WorkerAgent w) {
            var desc = w.worker().description();
            if (desc != null && !desc.isBlank()) {
                sb.append("\n  Description: ").append(desc);
            }
        }

        var descriptor = candidate.descriptor();
        if (descriptor != null) {
            if (descriptor.briefing() != null && !descriptor.briefing().isBlank()) {
                sb.append("\n  Briefing: ").append(descriptor.briefing());
            }
            if (descriptor.capabilities() != null && !descriptor.capabilities().isEmpty()) {
                var caps = descriptor.capabilities().stream()
                        .map(c -> {
                            var capStr = c.name();
                            if (c.epistemicDomains() != null && !c.epistemicDomains().isEmpty()) {
                                capStr += " (domains: " + String.join(", ", c.epistemicDomains().keySet()) + ")";
                            }
                            return capStr;
                        })
                        .collect(Collectors.joining(", "));
                sb.append("\n  Capabilities: ").append(caps);
            }
            if (descriptor.slot() != null && !descriptor.slot().isBlank()) {
                sb.append("\n  Slot: ").append(descriptor.slot());
            }
        }
        return sb.toString();
    }

    public static String candidateName(RoutingCandidate candidate, int index) {
        if (candidate.descriptor() != null && candidate.descriptor().name() != null) {
            return candidate.descriptor().name();
        }
        if (candidate.ref() instanceof AgentRef.WorkerAgent w) {
            return w.worker().name();
        }
        return candidate.ref().getClass().getSimpleName() + "-" + index;
    }
}
