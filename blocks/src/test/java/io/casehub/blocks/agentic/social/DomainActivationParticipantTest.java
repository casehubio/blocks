package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.cognitive.index.AffectTrajectory;
import io.casehub.neocortex.cognitive.index.CorrelationStrength;
import io.casehub.neocortex.cognitive.index.DomainActivation;
import io.casehub.neocortex.cognitive.index.DomainActivationResult;
import io.casehub.neocortex.cognitive.index.DomainCorrelation;
import io.casehub.neocortex.cognitive.index.DomainPair;
import io.casehub.neocortex.cognitive.index.DomainSignal;
import io.casehub.neocortex.cognitive.index.TrendDirection;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DomainActivationParticipantTest {

    private final DomainActivation domainActivation = mock(DomainActivation.class);
    private final MindMapStore mindMapStore = mock(MindMapStore.class);
    private final ConsolidationMediator mediator = mock(ConsolidationMediator.class);
    private final CognitionConfig enabledConfig = CognitionConfig.all().with("domainActivation", true);

    private CognitionTickContext context(String agentId, String tenantId) {
        return new CognitionTickContext(agentId, tenantId, null, mock(SubjectResolver.class));
    }

    @Test
    void tick_skipsWhenFeatureDisabled() {
        var participant = new DomainActivationParticipant(
                domainActivation, mindMapStore, mediator, CognitionConfig.all());
        participant.tick(context("agent-1", "t1"));
        assertThat(participant.lastSnapshot()).isNull();
        verifyNoInteractions(domainActivation);
    }

    @Test
    void tick_skipsWhenNoConsolidationData() {
        when(mediator.lastConsolidationTimestamp("t1")).thenReturn(null);
        var participant = new DomainActivationParticipant(
                domainActivation, mindMapStore, mediator, enabledConfig);
        participant.tick(context("agent-1", "t1"));
        assertThat(participant.lastSnapshot()).isNull();
    }

    @Test
    void tick_skipsWhenConsolidationNotNewer() {
        var ts = Instant.now().minus(Duration.ofHours(1));
        when(mediator.lastConsolidationTimestamp("t1")).thenReturn(ts);
        setupTwoSubgraphs("t1");
        when(domainActivation.correlate(any())).thenReturn(Optional.empty());

        var participant = new DomainActivationParticipant(
                domainActivation, mindMapStore, mediator, enabledConfig);
        participant.tick(context("agent-1", "t1"));

        reset(domainActivation);
        participant.tick(context("agent-1", "t1"));
        verifyNoInteractions(domainActivation);
    }

    @Test
    void tick_skipsWhenFewerThanTwoCognitiveSubgraphs() {
        when(mediator.lastConsolidationTimestamp("t1")).thenReturn(Instant.now());
        when(mindMapStore.listSubgraphs("t1")).thenReturn(List.of(
                new MindMapSubgraph("sg1", "Work", SubgraphTypes.COGNITIVE, null, "t1", Instant.now())
        ));
        when(mindMapStore.nodesIn("sg1", "t1")).thenReturn(List.of(mock(MindMapNode.class)));

        var participant = new DomainActivationParticipant(
                domainActivation, mindMapStore, mediator, enabledConfig);
        participant.tick(context("agent-1", "t1"));
        assertThat(participant.lastSnapshot()).isNull();
    }

    @Test
    void tick_computesWhenConsolidationIsNewer() {
        when(mediator.lastConsolidationTimestamp("t1")).thenReturn(Instant.now());
        setupTwoSubgraphs("t1");

        var correlation = new DomainCorrelation(0.75, List.of(), 10, CorrelationStrength.STRONG, 0.01, 0, 0);
        var pair = new DomainPair("sg1", "sg2");
        var signal1 = new DomainSignal("sg1",
                new AffectTrajectory(0.1, 0.2, 0.1, 0.05, TrendDirection.IMPROVING, 0.1, 5),
                3, 10, 7);
        var signal2 = new DomainSignal("sg2",
                new AffectTrajectory(-0.1, 0.3, -0.1, 0.0, TrendDirection.WORSENING, 0.1, 5),
                2, 8, 7);
        var scanResult = new DomainActivationResult(
                Map.of("sg1", signal1, "sg2", signal2),
                Map.of(pair, correlation),
                Map.of(), Map.of(),
                PrincipalId.agent("agent-1"), "t1", null, null);
        when(domainActivation.correlate(any())).thenReturn(Optional.of(scanResult));

        var participant = new DomainActivationParticipant(
                domainActivation, mindMapStore, mediator, enabledConfig);
        participant.tick(context("agent-1", "t1"));

        assertThat(participant.lastSnapshot()).isNotNull();
        assertThat(participant.lastSnapshot().hasRenderableCorrelations()).isTrue();
        assertThat(participant.lastSnapshot().pairwiseCorrelations()).hasSize(1);
    }

    @Test
    void tick_nullMediatorAlwaysComputes() {
        setupTwoSubgraphs("t1");
        when(domainActivation.correlate(any())).thenReturn(Optional.empty());

        var participant = new DomainActivationParticipant(
                domainActivation, mindMapStore, null, enabledConfig);
        participant.tick(context("agent-1", "t1"));
        verify(domainActivation, atLeastOnce()).correlate(any());
    }

    @Test
    void tick_filtersNonCognitiveSubgraphs() {
        when(mediator.lastConsolidationTimestamp("t1")).thenReturn(Instant.now());
        when(mindMapStore.listSubgraphs("t1")).thenReturn(List.of(
                new MindMapSubgraph("sg1", "Work", SubgraphTypes.COGNITIVE, null, "t1", Instant.now()),
                new MindMapSubgraph("goal-sg", "Goals", SubgraphTypes.GOAL, null, "t1", Instant.now())
        ));
        when(mindMapStore.nodesIn("sg1", "t1")).thenReturn(List.of(mock(MindMapNode.class)));

        var participant = new DomainActivationParticipant(
                domainActivation, mindMapStore, mediator, enabledConfig);
        participant.tick(context("agent-1", "t1"));
        assertThat(participant.lastSnapshot()).isNull();
        verifyNoInteractions(domainActivation);
    }

    private void setupTwoSubgraphs(String tenantId) {
        when(mindMapStore.listSubgraphs(tenantId)).thenReturn(List.of(
                new MindMapSubgraph("sg1", "Work", SubgraphTypes.COGNITIVE, null, tenantId, Instant.now()),
                new MindMapSubgraph("sg2", "Family", SubgraphTypes.COGNITIVE, null, tenantId, Instant.now()),
                new MindMapSubgraph("goal-sg", "Goals", SubgraphTypes.GOAL, null, tenantId, Instant.now())
        ));
        when(mindMapStore.nodesIn("sg1", tenantId)).thenReturn(List.of(mock(MindMapNode.class)));
        when(mindMapStore.nodesIn("sg2", tenantId)).thenReturn(List.of(mock(MindMapNode.class)));
    }
}
