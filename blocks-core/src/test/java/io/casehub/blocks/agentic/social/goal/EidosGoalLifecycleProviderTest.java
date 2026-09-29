package io.casehub.blocks.agentic.social.goal;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.AgentMatch;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.GoalLifecycleState;
import io.casehub.eidos.api.GoalPriority;
import io.casehub.eidos.api.Visibility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EidosGoalLifecycleProviderTest {

    private AgentRegistry registryReturning(AgentDescriptor descriptor) {
        return new AgentRegistry() {
            @Override public void register(AgentDescriptor d) {}
            @Override public Optional<AgentDescriptor> findById(String id, String tid) {
                if (descriptor != null && descriptor.agentId().equals(id)
                    && descriptor.tenancyId().equals(tid)) {
                    return Optional.of(descriptor);
                }
                return Optional.empty();
            }
            @Override public List<AgentMatch> find(AgentQuery q) { return List.of(); }
        };
    }

    @Test
    void returnsLifecycleStatesFromRegistry() {
        var descriptor = AgentDescriptor.builder()
                .agentId("a1").name("Agent").slot("default").tenancyId("t1")
                .goals(List.of(
                        new AgentGoal("research", "Research", GoalPriority.PRIMARY,
                                Visibility.PUBLIC, List.of(), null,
                                GoalLifecycleState.DORMANT, null),
                        new AgentGoal("learn", "Learn", GoalPriority.SECONDARY,
                                Visibility.PUBLIC, List.of(), null,
                                GoalLifecycleState.ACTIVE, null)))
                .build();

        var provider = new EidosGoalLifecycleProvider(registryReturning(descriptor));
        var states = provider.getLifecycleStates("a1", "t1");

        assertThat(states).containsEntry("research", "dormant");
        assertThat(states).containsEntry("learn", "active");
    }

    @Test
    void returnsEmptyMapWhenAgentNotFound() {
        var provider = new EidosGoalLifecycleProvider(registryReturning(null));
        var states = provider.getLifecycleStates("missing", "t1");

        assertThat(states).isEmpty();
    }

    @Test
    void skipsGoalsWithNullLifecycleState() {
        var descriptor = AgentDescriptor.builder()
                .agentId("a1").name("Agent").slot("default").tenancyId("t1")
                .goals(List.of(
                        new AgentGoal("old-goal", "Old", GoalPriority.SECONDARY,
                                Visibility.PUBLIC, List.of(), null),
                        new AgentGoal("new-goal", "New", GoalPriority.PRIMARY,
                                Visibility.PUBLIC, List.of(), null,
                                GoalLifecycleState.ACTIVE, null)))
                .build();

        var provider = new EidosGoalLifecycleProvider(registryReturning(descriptor));
        var states = provider.getLifecycleStates("a1", "t1");

        assertThat(states).hasSize(1);
        assertThat(states).containsEntry("new-goal", "active");
    }

    @Test
    void returnsEmptyMapWhenRegistryNull() {
        var provider = new EidosGoalLifecycleProvider((AgentRegistry) null);
        var states = provider.getLifecycleStates("a1", "t1");

        assertThat(states).isEmpty();
    }
}
