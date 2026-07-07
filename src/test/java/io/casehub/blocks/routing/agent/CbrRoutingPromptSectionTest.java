package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.eidos.api.MatchDegree;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TextualCbrCase;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbrRoutingPromptSectionTest {

    @Mock CbrCaseMemoryStore cbrStore;

    private final RoutingFeatureExtractor extractor = new TextOnlyFeatureExtractor();

    private CbrRoutingPromptSection section;

    private AgentRoutingContext context() {
        return new AgentRoutingContext(UUID.randomUUID(), "analysis", NullNode.instance, "t", List.of());
    }

    private AgentCandidate candidate(String id) {
        return new AgentCandidate(id, Set.of("analysis"), 0,
                AgentHealth.READY, null, new MatchDegree.None());
    }

    @BeforeEach
    void setUp() {
        section = new CbrRoutingPromptSection(cbrStore, extractor, 10, 0.5);
    }

    @Test
    void returnsNull_whenCbrStoreNull() {
        var noStore = new CbrRoutingPromptSection(null, extractor, 10, 0.5);
        assertThat(noStore.render(context(), List.of(candidate("a")))).isNull();
    }

    @Test
    void returnsNull_whenNoSimilarCases() {
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of());
        assertThat(section.render(context(), List.of(candidate("a")))).isNull();
    }

    @Test
    void rendersPlanCbrCase_withEligibleAgent() {
        var trace = new PlanTrace("binding-a", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var cbrCase = new PlanCbrCase("problem", "solution", "RESOLVED", 0.9,
                Map.of(), List.of(trace));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

        var result = section.render(context(), List.of(candidate("agent-a")));

        assertThat(result)
                .contains("Historical context")
                .contains("agent-a")
                .contains("SUCCESS");
    }

    @Test
    void filtersOutNonEligibleAgents() {
        var traceA = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var traceB = new PlanTrace("b", "analysis", "excluded", "FAILURE", 1, Map.of());
        var cbrCase = new PlanCbrCase("problem", "sol", "RESOLVED", 0.9,
                Map.of(), List.of(traceA, traceB));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

        var result = section.render(context(), List.of(candidate("agent-a")));

        assertThat(result).contains("agent-a").doesNotContain("excluded");
    }

    @Test
    void returnsNull_whenAllCasesReferenceNonEligibleAgents() {
        var trace = new PlanTrace("b", "analysis", "excluded", "SUCCESS", 1, Map.of());
        var cbrCase = new PlanCbrCase("problem", "sol", "RESOLVED", 0.9,
                Map.of(), List.of(trace));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

        assertThat(section.render(context(), List.of(candidate("other")))).isNull();
    }

    @Test
    void rendersTextualCbrCase_withoutAgentAttribution() {
        var textCase = new TextualCbrCase("similar problem", "some solution", "RESOLVED", 0.8);
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(textCase, 0.85)));

        var result = section.render(context(), List.of(candidate("a")));

        assertThat(result)
                .contains("similar problem")
                .contains("RESOLVED")
                .doesNotContain("Outcomes by agent");
    }

    @Test
    void mixedCases_planAndTextual() {
        var trace = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var planCase = new PlanCbrCase("plan problem", "sol", "RESOLVED", 0.9,
                Map.of(), List.of(trace));
        var textCase = new TextualCbrCase("text problem", "sol", "FAILURE", 0.7);
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(
                        new ScoredCbrCase<>(planCase, 0.9),
                        new ScoredCbrCase<>(textCase, 0.85)));

        var result = section.render(context(), List.of(candidate("agent-a")));

        assertThat(result)
                .contains("Outcomes by agent")
                .contains("agent-a")
                .contains("text problem");
    }

    @Test
    void cbrStoreThrows_returnsNull() {
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenThrow(new RuntimeException("store error"));
        assertThat(section.render(context(), List.of(candidate("a")))).isNull();
    }
}
