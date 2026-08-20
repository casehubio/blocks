package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.memory.EraseRequest;
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

class CbrMentalModelStoreTest {

    @Mock CbrCaseMemoryStore cbrStore;
    CbrMentalModelStore store;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        store = new CbrMentalModelStore(cbrStore, MentalModelConfig.defaults());
    }

    @Test
    void storeConvertsSnapshotToCbrCase() {
        var now = Instant.now();
        var snapshot = new MentalModelSnapshot("agent1", "user1", "tenant1",
                List.of(new AttributedState("risk", "high", 0.8, 2, now, BdiDimension.BELIEF)),
                List.of(), List.of(), now, null, now);

        store.store(snapshot);

        var captor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(captor.capture(), eq("mental-model"), eq("agent1"),
                any(MemoryDomain.class), eq("tenant1"), isNull(), any());
        var stored = captor.getValue();
        assertThat(stored.features().get(MentalModelSchema.SUBJECT_ID))
                .isEqualTo(FeatureValue.string("user1"));
        assertThat(stored.producerAgentId()).isEqualTo("agent1");
    }

    @Test
    void lookupReturnsEmptyWhenNoMatch() {
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of());
        var result = store.lookup("agent1", "user1", "tenant1");
        assertThat(result).isEmpty();
    }

    @Test
    void lookupReconstructsSnapshotRoundTrip() {
        var now = Instant.now();
        var belief = new AttributedState("risk", "high risk", 0.8, 2, now, BdiDimension.BELIEF);
        var desire = new AttributedState("speed", "wants quick fix", 0.6, 0, now, BdiDimension.DESIRE);
        var snapshot = new MentalModelSnapshot("agent1", "user1", "tenant1",
                List.of(belief), List.of(desire), List.of(), now, now, now);

        var features = MentalModelSchema.toFeatures(snapshot);
        CbrCase cbrCase = new FeatureVectorCbrCase(
                MentalModelSchema.toSummary(snapshot), "-", null, null,
                features, null, "agent1");
        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(scored));

        var result = store.lookup("agent1", "user1", "tenant1");
        assertThat(result).isPresent();
        var loaded = result.get();
        assertThat(loaded.agentId()).isEqualTo("agent1");
        assertThat(loaded.subjectId()).isEqualTo("user1");
        assertThat(loaded.beliefs()).hasSize(1);
        assertThat(loaded.beliefs().getFirst().key()).isEqualTo("risk");
        assertThat(loaded.beliefs().getFirst().confidence()).isEqualTo(0.8);
        assertThat(loaded.beliefs().getFirst().entrenchment()).isEqualTo(2);
        assertThat(loaded.beliefs().getFirst().dimension()).isEqualTo(BdiDimension.BELIEF);
        assertThat(loaded.desires()).hasSize(1);
        assertThat(loaded.desires().getFirst().key()).isEqualTo("speed");
    }

    @Test
    void eraseSubjectCallsEraseOnStore() {
        var features = Map.<String, FeatureValue>of(
                MentalModelSchema.SUBJECT_ID, FeatureValue.string("user1"));
        CbrCase cbrCase = new FeatureVectorCbrCase(
                "Mental model", "-", null, null, features, null, "agent1");
        var scored = new ScoredCbrCase<>(cbrCase, "case-1", 1.0);

        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(scored));

        store.eraseSubject("user1", "tenant1");
        verify(cbrStore).erase(any(EraseRequest.class));
    }

    @Test
    void findByAgentReturnsEmptyWhenNoMatch() {
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of());
        var result = store.findByAgent("agent1", "tenant1");
        assertThat(result).isEmpty();
    }

    @Test
    void schemaSerializationRoundTrip() {
        var now = Instant.now();
        var states = List.of(
                new AttributedState("key1", "description with \"quotes\"", 0.75, 3, now, BdiDimension.BELIEF),
                new AttributedState("key2", "another desc", 0.5, 0, now, BdiDimension.DESIRE));
        var json = MentalModelSchema.serializeStates(states);
        var deserialized = MentalModelSchema.deserializeStates(json);
        assertThat(deserialized).hasSize(2);
        assertThat(deserialized.get(0).key()).isEqualTo("key1");
        assertThat(deserialized.get(0).description()).isEqualTo("description with \"quotes\"");
        assertThat(deserialized.get(0).confidence()).isEqualTo(0.75);
        assertThat(deserialized.get(0).entrenchment()).isEqualTo(3);
        assertThat(deserialized.get(0).dimension()).isEqualTo(BdiDimension.BELIEF);
        assertThat(deserialized.get(1).key()).isEqualTo("key2");
        assertThat(deserialized.get(1).dimension()).isEqualTo(BdiDimension.DESIRE);
    }

    @Test
    void schemaDeserializeEmptyArray() {
        assertThat(MentalModelSchema.deserializeStates("[]")).isEmpty();
        assertThat(MentalModelSchema.deserializeStates("")).isEmpty();
        assertThat(MentalModelSchema.deserializeStates(null)).isEmpty();
    }
}
