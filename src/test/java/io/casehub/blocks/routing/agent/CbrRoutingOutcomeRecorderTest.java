package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbrRoutingOutcomeRecorderTest {

    @Mock CbrCaseMemoryStore cbrStore;

    private final RoutingFeatureExtractor extractor = new TextOnlyFeatureExtractor();
    private CbrRoutingOutcomeRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new CbrRoutingOutcomeRecorder(cbrStore, extractor);
    }

    private AgentRoutingContext context() {
        return new AgentRoutingContext(UUID.randomUUID(), "analysis", NullNode.instance, "t", List.of());
    }

    @Test
    void recordsSuccess_asPlanCbrCase() {
        when(cbrStore.store(any(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn("stored-id");

        recorder.record(context(), "agent-a", "binding-x", "SUCCESS", null)
                .await().indefinitely();

        var caseCaptor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(caseCaptor.capture(), anyString(),
                eq("agent-routing"), any(MemoryDomain.class), eq("t"), anyString());

        var stored = (PlanCbrCase) caseCaptor.getValue();
        assertThat(stored.outcome()).isEqualTo("SUCCESS");
        assertThat(stored.planTrace()).hasSize(1);
        assertThat(stored.planTrace().getFirst().workerName()).isEqualTo("agent-a");
        assertThat(stored.planTrace().getFirst().bindingName()).isEqualTo("binding-x");
        assertThat(stored.planTrace().getFirst().capabilityName()).isEqualTo("analysis");
        assertThat(stored.planTrace().getFirst().stepOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    void recordsFailure() {
        when(cbrStore.store(any(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn("stored-id");

        recorder.record(context(), "agent-b", "binding-y", "FAILURE", null)
                .await().indefinitely();

        var caseCaptor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(caseCaptor.capture(), anyString(),
                eq("agent-routing"), any(MemoryDomain.class), anyString(), anyString());

        var stored = (PlanCbrCase) caseCaptor.getValue();
        assertThat(stored.outcome()).isEqualTo("FAILURE");
        assertThat(stored.planTrace().getFirst().stepOutcome()).isEqualTo("FAILURE");
    }

    @Test
    void nullCbrStore_completesSuccessfully() {
        var noStore = new CbrRoutingOutcomeRecorder(null, extractor);
        noStore.record(context(), "a", "b", "SUCCESS", null).await().indefinitely();
        // no exception, no store call
    }

    @Test
    void usesCapabilityNameAsFallbackProblem() {
        when(cbrStore.store(any(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn("stored-id");
        // NullNode context → extractProblem returns null → falls back to capabilityName
        recorder.record(context(), "a", "b", "SUCCESS", null).await().indefinitely();

        var caseCaptor = ArgumentCaptor.forClass(CbrCase.class);
        verify(cbrStore).store(caseCaptor.capture(), anyString(), anyString(),
                any(), anyString(), anyString());
        assertThat(((PlanCbrCase) caseCaptor.getValue()).problem()).isEqualTo("analysis");
    }
}
