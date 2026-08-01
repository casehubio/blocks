package io.casehub.blocks.prompt.runtime;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.blocks.prompt.FewShotExample;
import io.casehub.blocks.prompt.PromptVariant;
import io.casehub.blocks.prompt.VariantSelector;
import io.casehub.eidos.api.MatchDegree;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OptimisedFewShotSectionTest {

    private AgentRoutingContext context() {
        return new AgentRoutingContext(
                UUID.randomUUID(), "triage", NullNode.instance, "tenant", List.of(), null, null);
    }

    private AgentCandidate candidate(String id) {
        return new AgentCandidate(id, Set.of("triage"), 0, AgentHealth.READY, null, new MatchDegree.None());
    }

    @Test
    void returnsNullWhenNoActiveVariant() {
        var store = new InMemoryPromptVariantStore();
        var selector = new VariantSelector(0.0, 5);
        var section = new OptimisedFewShotSection(store, selector, "llm-routing");
        assertThat(section.render(context(), List.of(candidate("a")))).isNull();
    }

    @Test
    void returnsNullWhenVariantHasNoExamples() {
        var store = new InMemoryPromptVariantStore();
        var variant = new PromptVariant("llm-routing", "v1", List.of(), null, 0.8,
                Instant.now(), null, 0);
        store.store(variant);
        store.activate("llm-routing", "v1", "control");
        var selector = new VariantSelector(0.0, 5);
        var section = new OptimisedFewShotSection(store, selector, "llm-routing");
        assertThat(section.render(context(), List.of(candidate("a")))).isNull();
    }

    @Test
    void rendersExamplesFromActiveVariant() {
        var store = new InMemoryPromptVariantStore();
        var examples = List.of(
                new FewShotExample("Case: patient triage", "Selected: dr-smith", "SUCCESS", 0.9, null),
                new FewShotExample("Case: lab review", "Selected: lab-tech", "SUCCESS", 0.85, null));
        var variant = new PromptVariant("llm-routing", "v1", examples, null, 0.8,
                Instant.now(), null, 0);
        store.store(variant);
        store.activate("llm-routing", "v1", "control");
        var selector = new VariantSelector(0.0, 5);
        var section = new OptimisedFewShotSection(store, selector, "llm-routing");
        var result = section.render(context(), List.of(candidate("a")));
        assertThat(result)
                .contains("Case: patient triage")
                .contains("Selected: dr-smith")
                .contains("SUCCESS")
                .contains("Case: lab review");
    }

    @Test
    void usesExperimentSlotWhenSelectorChoosesIt() {
        var store = new InMemoryPromptVariantStore();
        var controlExamples = List.of(
                new FewShotExample("control input", "control output", "SUCCESS", 0.8, null));
        var experimentExamples = List.of(
                new FewShotExample("experiment input", "experiment output", "SUCCESS", 0.9, null));
        var controlVariant = new PromptVariant("llm-routing", "v-ctrl", controlExamples, null, 0.8,
                Instant.now(), null, 0);
        var experimentVariant = new PromptVariant("llm-routing", "v-exp", experimentExamples, null, 0.9,
                Instant.now(), null, 0);
        store.store(controlVariant);
        store.store(experimentVariant);
        store.activate("llm-routing", "v-ctrl", "control");
        store.activate("llm-routing", "v-exp", "experiment");

        var selector = new VariantSelector(1.0, 5);
        var section = new OptimisedFewShotSection(store, selector, "llm-routing");
        var result = section.render(context(), List.of(candidate("a")));
        assertThat(result).contains("experiment input");
        assertThat(result).doesNotContain("control input");
    }
}
