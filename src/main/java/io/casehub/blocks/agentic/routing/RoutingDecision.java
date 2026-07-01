package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface RoutingDecision
        permits RoutingDecision.Selected, RoutingDecision.Unresolvable,
                RoutingDecision.Escalate {

    record Selected(List<AgentRef> agents, @Nullable String reason) implements RoutingDecision {
        public Selected { agents = List.copyOf(agents); }
        public Selected(List<AgentRef> agents) { this(agents, null); }
    }

    record Unresolvable(String reason) implements RoutingDecision {}

    record Escalate(String reason) implements RoutingDecision {}
}
