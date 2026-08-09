package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.prompt.FewShotExample;
import io.casehub.blocks.prompt.PromptVariant;
import io.casehub.blocks.prompt.SystemPromptCustomiser;
import io.casehub.blocks.prompt.VariantSelector;
import io.casehub.blocks.prompt.runtime.InMemoryPromptVariantStore;
import io.casehub.engine.plan.TaskNode;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmDecompositionPromptCustomisationTest {

    @Mock AgentProvider agentProvider;

    private void mockLlmResponse(String response) {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(response)));
    }

    @Test
    void usesCustomisedSystemPromptWhenCustomiserProvided() {
        mockLlmResponse("[]");

        SystemPromptCustomiser customiser = (base, sigId, slot) ->
                base + "\n\nPrefer granular task decomposition.";
        var selector = new VariantSelector(0.0, 5);

        var decomposition = new LlmDecomposition<String>(agentProvider, Object::toString,
                1, customiser, selector, null);

        var goal = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "analyse", List.of());
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0, null);

        try {
            decomposition.decompose(goal, ctx).await().indefinitely();
        } catch (Exception ignored) {}

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertThat(captor.getValue().systemPrompt())
                .contains("Prefer granular task decomposition.");
    }

    @Test
    void appendsFewShotExamplesFromStoreToUserPrompt() {
        mockLlmResponse("[]");

        var store = new InMemoryPromptVariantStore();
        var examples = List.of(
                new FewShotExample("Goal: triage patient",
                        "[{\"agent\":\"dr\",\"task\":\"assess\"}]",
                        "SUCCESS", 0.9, null));
        var variant = new PromptVariant("llm-decomposition", "v1", examples, null, 0.8,
                Instant.now(), null, 0);
        store.store(variant);
        store.activate("llm-decomposition", "v1", "control");

        var selector = new VariantSelector(0.0, 5);
        var decomposition = new LlmDecomposition<String>(agentProvider, Object::toString,
                1, null, selector, store);

        var goal = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "analyse", List.of());
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0, null);

        try {
            decomposition.decompose(goal, ctx).await().indefinitely();
        } catch (Exception ignored) {}

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertThat(captor.getValue().userPrompt())
                .contains("Goal: triage patient")
                .contains("SUCCESS");
    }

    @Test
    void worksWithoutCustomiserOrStore() {
        mockLlmResponse("[]");

        var decomposition = new LlmDecomposition<String>(agentProvider);

        var goal = new TaskNode.CompoundTask<String>(java.util.UUID.randomUUID().toString(), "analyse", List.of());
        var ctx = new AgenticDecompositionContext<>("state", List.of(), 0, null);

        try {
            decomposition.decompose(goal, ctx).await().indefinitely();
        } catch (Exception ignored) {}

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertThat(captor.getValue().systemPrompt()).doesNotContain("examples");
    }
}
