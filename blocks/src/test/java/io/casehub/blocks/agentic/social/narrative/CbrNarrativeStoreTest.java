package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CbrNarrativeStoreTest {

    @Mock CbrCaseMemoryStore cbrStore;
    CbrNarrativeStore store;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        store = new CbrNarrativeStore(cbrStore, NarrativeConfig.defaults());
    }

    @Test
    void storeConvertsStateToCbrCase() {
        var now = Instant.now();
        var episode = new IndividualEpisode("e1", now, null,
                List.of("growth"), "Learned something", 0.5, List.of("r1"));
        var state = new NarrativeState("agent1", "tenant1",
                NarrativeScope.INDIVIDUAL, List.of(episode), now, 3);

        store.store(state);

        var captor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(captor.capture(), eq("narrative"), eq("agent1"),
                any(MemoryDomain.class), eq("tenant1"), isNull(), any());
        var stored = captor.getValue();
        assertThat(stored.features().get(NarrativeStateSchema.SCOPE_ID))
                .isEqualTo(FeatureValue.string("agent1"));
        assertThat(stored.features().get(NarrativeStateSchema.SCOPE))
                .isEqualTo(FeatureValue.string("INDIVIDUAL"));
        assertThat(stored.producerAgentId()).isEqualTo("agent1");
    }

    @Test
    void loadReturnsNullWhenNoMatch() {
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of());
        var result = store.load("agent1", "tenant1");
        assertThat(result).isNull();
    }

    @Test
    void loadReconstructsStateRoundTrip() {
        var now = Instant.now();
        var episode = new IndividualEpisode("e1", now, null,
                List.of("growth"), "Learned something new", 0.6, List.of("r1"));
        var theme = new DerivedTheme("t1", now, null,
                List.of("learner"), "curious-explorer", 0.8,
                Map.of(DriveAxis.CURIOSITY, 0.5), List.of("e1"));
        var state = new NarrativeState("agent1", "tenant1",
                NarrativeScope.INDIVIDUAL, List.of(episode, theme), now, 5);

        var features = NarrativeStateSchema.toFeatures(state);
        CbrCase cbrCase = new FeatureVectorCbrCase(
                NarrativeStateSchema.toSummary(state), "-", null, null,
                features, null, "agent1");
        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(scored));

        var loaded = store.load("agent1", "tenant1");
        assertThat(loaded).isNotNull();
        assertThat(loaded.scopeId()).isEqualTo("agent1");
        assertThat(loaded.tenantId()).isEqualTo("tenant1");
        assertThat(loaded.scope()).isEqualTo(NarrativeScope.INDIVIDUAL);
        assertThat(loaded.reflectionCountAtSynthesis()).isEqualTo(5);
        assertThat(loaded.episodes()).hasSize(1);
        assertThat(loaded.episodes().getFirst().id()).isEqualTo("e1");
        assertThat(loaded.episodes().getFirst().description()).isEqualTo("Learned something new");
        assertThat(loaded.episodes().getFirst().emotionalValence()).isEqualTo(0.6);
        assertThat(loaded.themes()).hasSize(1);
        assertThat(loaded.themes().getFirst().label()).isEqualTo("curious-explorer");
        assertThat(loaded.themes().getFirst().salience()).isEqualTo(0.8);
        assertThat(loaded.themes().getFirst().axisModulationWeights())
                .containsEntry(DriveAxis.CURIOSITY, 0.5);
    }

    @Test
    void loadFiltersOnProducerAgentId() {
        var features = NarrativeStateSchema.toFeatures(
                new NarrativeState("other-agent", "tenant1",
                        NarrativeScope.INDIVIDUAL, List.of(),
                        Instant.now(), 0));
        CbrCase cbrCase = new FeatureVectorCbrCase(
                "summary", "-", null, null, features, null, "other-agent");
        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(scored));

        var result = store.load("agent1", "tenant1");
        assertThat(result).isNull();
    }

    @Test
    void storeGroupScopePreservesScope() {
        var now = Instant.now();
        var state = new NarrativeState("group1", "tenant1",
                NarrativeScope.GROUP, List.of(), now, 0);

        store.store(state);

        var captor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(captor.capture(), eq("narrative"), eq("group1"),
                any(MemoryDomain.class), eq("tenant1"), isNull(), any());
        var stored = captor.getValue();
        assertThat(stored.features().get(NarrativeStateSchema.SCOPE))
                .isEqualTo(FeatureValue.string("GROUP"));
    }
}
