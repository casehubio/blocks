package io.casehub.blocks.agentic.social.goal;

import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.neocortex.mindmap.GoalLifecycleProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class EidosGoalLifecycleProvider implements GoalLifecycleProvider {

    private final AgentRegistry agentRegistry;

    @Inject
    public EidosGoalLifecycleProvider(Instance<AgentRegistry> agentRegistry) {
        this.agentRegistry = agentRegistry.isResolvable() ? agentRegistry.get() : null;
    }

    EidosGoalLifecycleProvider(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    @Override
    public Map<String, String> getLifecycleStates(String agentId, String tenantId) {
        if (agentRegistry == null) return Map.of();

        return agentRegistry.findById(agentId, tenantId)
                .map(descriptor -> descriptor.goals().stream()
                        .filter(g -> g.lifecycleState() != null)
                        .collect(Collectors.toMap(
                                AgentGoal::name,
                                g -> g.lifecycleState().name().toLowerCase())))
                .orElse(Map.of());
    }
}
