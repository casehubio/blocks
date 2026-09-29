package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognitive.index.CognitiveProfileQuery;
import io.casehub.neocortex.cognitive.index.EntityKnowledge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CognitiveProfileParticipantTest {

    @Test
    void tickResolvesActiveSubjects() {
        var profile = mock(CognitiveProfile.class);
        var node = stubNode("penelope");
        var entity = stubEntityKnowledge(node);
        when(profile.resolve(any())).thenReturn(Optional.of(entity));

        var participant = new CognitiveProfileParticipant(
                profile, null, null, CognitionConfig.all());

        var context = new CognitionTickContext("hooded-claw", "tenant",
                null, (aid, tid) -> Set.of("penelope"));
        participant.tick(context);

        assertThat(participant.lastEntityKnowledge()).hasSize(1);
        assertThat(participant.lastEntityKnowledge().getFirst().node().name()).isEqualTo("penelope");

        var captor = ArgumentCaptor.forClass(CognitiveProfileQuery.class);
        verify(profile).resolve(captor.capture());
        assertThat(captor.getValue().entityName()).isEqualTo("penelope");
        assertThat(captor.getValue().asSeenBy()).isEqualTo(PrincipalId.agent("hooded-claw"));
    }

    @Test
    void tickSkipsWhenEntityKnowledgeDisabled() {
        var profile = mock(CognitiveProfile.class);
        var config = CognitionConfig.all().with("entityKnowledge", false);
        var participant = new CognitiveProfileParticipant(profile, null, null, config);

        var context = new CognitionTickContext("agent", "tenant",
                null, (aid, tid) -> Set.of("penelope"));
        participant.tick(context);

        assertThat(participant.lastEntityKnowledge()).isEmpty();
        verify(profile, never()).resolve(any());
    }

    @Test
    void tickHandlesEmptyResolveGracefully() {
        var profile = mock(CognitiveProfile.class);
        when(profile.resolve(any())).thenReturn(Optional.empty());

        var participant = new CognitiveProfileParticipant(
                profile, null, null, CognitionConfig.all());

        var context = new CognitionTickContext("agent", "tenant",
                null, (aid, tid) -> Set.of("unknown-entity"));
        participant.tick(context);

        assertThat(participant.lastEntityKnowledge()).isEmpty();
    }

    @Test
    void tickDeduplicatesAcrossSources() {
        var profile = mock(CognitiveProfile.class);
        var node = stubNode("penelope");
        var entity = stubEntityKnowledge(node);
        when(profile.resolve(any())).thenReturn(Optional.of(entity));

        var participant = new CognitiveProfileParticipant(
                profile, null, null, CognitionConfig.all());

        var context = new CognitionTickContext("agent", "tenant",
                null, (aid, tid) -> new java.util.LinkedHashSet<>(List.of("penelope")));
        participant.tick(context);

        verify(profile, times(1)).resolve(any());
    }

    @Test
    void tickCallsCompareWhenPerspectiveComparisonEnabled() {
        var profile = mock(CognitiveProfile.class);
        var node = stubNode("penelope");
        var entity = stubEntityKnowledge(node);
        when(profile.resolve(any())).thenReturn(Optional.of(entity));
        when(profile.compare(any(), any())).thenReturn(Map.of(
                PrincipalId.agent("agent-a"), entity));

        var config = CognitionConfig.all().with("perspectiveComparison", true);
        var participant = new CognitiveProfileParticipant(profile, null, null, config);

        var context = new CognitionTickContext("agent-a", "tenant",
                null, (aid, tid) -> Set.of("penelope"));
        participant.tick(context);

        verify(profile).compare(any(), any());
        assertThat(participant.lastComparisons()).isNotEmpty();
    }

    @Test
    void tickSkipsCompareWhenPerspectiveComparisonDisabled() {
        var profile = mock(CognitiveProfile.class);
        var node = stubNode("penelope");
        var entity = stubEntityKnowledge(node);
        when(profile.resolve(any())).thenReturn(Optional.of(entity));

        var participant = new CognitiveProfileParticipant(
                profile, null, null, CognitionConfig.all());

        var context = new CognitionTickContext("agent", "tenant",
                null, (aid, tid) -> Set.of("penelope"));
        participant.tick(context);

        verify(profile, never()).compare(any(), any());
        assertThat(participant.lastComparisons()).isEmpty();
    }

    @Test
    void tickResolvesMultipleSubjects() {
        var profile = mock(CognitiveProfile.class);
        var nodeP = stubNode("penelope");
        var nodeM = stubNode("muttley");
        when(profile.resolve(argThat(q -> q != null && "penelope".equals(q.entityName()))))
                .thenReturn(Optional.of(stubEntityKnowledge(nodeP)));
        when(profile.resolve(argThat(q -> q != null && "muttley".equals(q.entityName()))))
                .thenReturn(Optional.of(stubEntityKnowledge(nodeM)));

        var participant = new CognitiveProfileParticipant(
                profile, null, null, CognitionConfig.all());

        var context = new CognitionTickContext("agent", "tenant",
                null, (aid, tid) -> Set.of("penelope", "muttley"));
        participant.tick(context);

        assertThat(participant.lastEntityKnowledge()).hasSize(2);
        verify(profile, times(2)).resolve(any());
    }


    @Test
    void tickPopulatesSocialComparisonsWhenPerspectiveComparisonEnabled() {
        var profile = mock(CognitiveProfile.class);
        var node    = stubNode("penelope");
        var entity  = stubEntityKnowledge(node);
        when(profile.resolve(any())).thenReturn(Optional.of(entity));
        when(profile.compare(any(), any())).thenReturn(Map.of(
                PrincipalId.agent("agent-a"), entity,
                PrincipalId.agent("agent-b"), entity));

        var config      = CognitionConfig.all().with("perspectiveComparison", true);
        var participant = new CognitiveProfileParticipant(profile, null, null, config);

        var context = new CognitionTickContext("agent-a", "tenant",
                                               null, (aid, tid) -> Set.of("penelope"));
        participant.tick(context);

        assertThat(participant.lastSocialComparisons()).hasSize(1);
        assertThat(participant.lastSocialComparisons().values().iterator().next()
                              .agentCount()).isEqualTo(2);
    }

    @Test
    void tickSocialComparisonsEmptyWhenPerspectiveComparisonDisabled() {
        var profile = mock(CognitiveProfile.class);
        var node    = stubNode("penelope");
        var entity  = stubEntityKnowledge(node);
        when(profile.resolve(any())).thenReturn(Optional.of(entity));

        var participant = new CognitiveProfileParticipant(
                profile, null, null, CognitionConfig.all());

        var context = new CognitionTickContext("agent", "tenant",
                                               null, (aid, tid) -> Set.of("penelope"));
        participant.tick(context);

        assertThat(participant.lastSocialComparisons()).isEmpty();
    }

    private static MindMapNode stubNode(String name) {
        var node = mock(MindMapNode.class);
        when(node.name()).thenReturn(name);
        when(node.id()).thenReturn("node-" + name);
        return node;
    }

    private static EntityKnowledge stubEntityKnowledge(MindMapNode node) {
        return new EntityKnowledge(
                node, List.of(), Map.of(), null, Set.of(),
                "tenant", PrincipalId.agent("perceiver"));
    }
}
