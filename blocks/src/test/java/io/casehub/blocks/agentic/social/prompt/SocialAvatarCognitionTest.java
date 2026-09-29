package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.InnerLifeOrchestrator;
import io.casehub.blocks.agentic.social.InnerLifeTick;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.speech.AssembledPrompt;
import io.casehub.blocks.speech.SpeechPromptAssembler;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.GoalLifecycleState;
import io.casehub.eidos.api.GoalPriority;
import io.casehub.eidos.api.Visibility;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.mindmap.GoalAppraisal;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.SubgraphTypes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialAvatarCognitionTest {

    private SocialAvatarCognition cognition;
    private MoodOrchestrator mood;
    private DriveOrchestrator drives;
    private MentalModelOrchestrator mentalModel;
    private UserModelOrchestrator userModel;
    private StrategyLearningOrchestrator strategy;
    private InnerLifeOrchestrator innerLife;
    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        mood = mock(MoodOrchestrator.class);
        drives = mock(DriveOrchestrator.class);
        mentalModel = mock(MentalModelOrchestrator.class);
        userModel = mock(UserModelOrchestrator.class);
        strategy = mock(StrategyLearningOrchestrator.class);
        innerLife = mock(InnerLifeOrchestrator.class);
        registry = mock(AgentRegistry.class);

        cognition = new SocialAvatarCognition(
                mood, drives, mentalModel, userModel, strategy,
                Optional.empty(),
                Optional.empty(),
                Optional.of(innerLife),
                Optional.of(registry),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    @Test
    void wrapAssemblerReturnsSocialPromptAssembler() {
        SpeechPromptAssembler base = (msg, hist) -> new AssembledPrompt("base", msg);
        var wrapped = cognition.wrapAssembler(base, "a1", "t1", () -> null);
        assertThat(wrapped).isInstanceOf(SocialPromptAssembler.class);
        var result = wrapped.assemble("test", List.of());
        assertThat(result.systemPrompt()).startsWith("base");
    }

    @Test
    void tickCallsAgentScopedOrchestrators() {
        cognition.tick("a1", "t1", Set.of());
        verify(mood).tick("a1", "t1");
        verify(strategy).tick("a1", "t1");
    }

    @Test
    void tickCallsSubjectScopedOrchestratorsForEachSubject() {
        cognition.tick("a1", "t1", Set.of("user1", "user2"));
        verify(userModel).tick("a1", "user1", "t1");
        verify(userModel).tick("a1", "user2", "t1");
        verify(mentalModel).tick("a1", "user1", "t1");
        verify(mentalModel).tick("a1", "user2", "t1");
    }

    @Test
    void recordInteractionDispatchesSubjectScopedSignals() {
        cognition.recordInteraction("a1", "t1", "user1", "hello", "hi there");
        verify(userModel).record(any(), eq("a1"), eq("user1"), eq("t1"));
        verify(mentalModel).record(any(), eq("a1"), eq("user1"), eq("t1"));
        verify(strategy).record(any(), eq("a1"), eq("user1"), eq("t1"));
    }

    @Test
    void recordInteractionSkipsSubjectScopedWhenNoSubject() {
        cognition.recordInteraction("a1", "t1", null, "hello", "hi there");
        verify(userModel, never()).record(any(), any(), any(), any());
        verify(mentalModel, never()).record(any(), any(), any(), any());
        verify(strategy, never()).record(any(), any(), any(), any());
    }

    @Test
    void recordInteractionResetsProactiveCounter() {
        var descriptor = mock(AgentDescriptor.class);
        when(registry.findById("a1", "t1")).thenReturn(Optional.of(descriptor));
        cognition.recordInteraction("a1", "t1", "user1", "hello", "hi");
        verify(innerLife).observeResponse(descriptor);
    }

    @Test
    void evaluateProactiveReturnsContentOnInitiated() {
        var descriptor = mock(AgentDescriptor.class);
        when(registry.findById("a1", "t1")).thenReturn(Optional.of(descriptor));
        when(innerLife.tick(descriptor, "ctx"))
                .thenReturn(new InnerLifeTick.Initiated("Hi!", null, 0.9));
        var result = cognition.evaluateProactive("a1", "t1", "ctx");
        assertThat(result).isEqualTo("Hi!");
    }

    @Test
    void evaluateProactiveReturnsNullOnSilent() {
        var descriptor = mock(AgentDescriptor.class);
        when(registry.findById("a1", "t1")).thenReturn(Optional.of(descriptor));
        when(innerLife.tick(descriptor, "ctx"))
                .thenReturn(new InnerLifeTick.Silent(null));
        assertThat(cognition.evaluateProactive("a1", "t1", "ctx")).isNull();
    }

    @Test
    void recordInteractionIsolatesFailures() {
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(userModel).record(any(), any(), any(), any());
        cognition.recordInteraction("a1", "t1", "user1", "hello", "hi");
        verify(mentalModel).record(any(), eq("a1"), eq("user1"), eq("t1"));
    }

    private MindMapNode goalNodeWithDecaySignal(String nodeId, String name,
                                                 String decaySignal, String eidosGoalName) {
        var node = mock(MindMapNode.class);
        when(node.id()).thenReturn(nodeId);
        when(node.name()).thenReturn(name);
        when(node.property("decay-signal")).thenReturn(Optional.ofNullable(decaySignal));
        when(node.property("eidos-goal-name")).thenReturn(Optional.ofNullable(eidosGoalName));
        when(node.property("status")).thenReturn(Optional.of("decaying"));
        when(node.property("priority")).thenReturn(Optional.empty());
        when(node.property("surfacing-progress-gap")).thenReturn(Optional.empty());
        when(node.property("surfaced-count")).thenReturn(Optional.empty());
        when(node.property("last-progress-at")).thenReturn(Optional.empty());
        when(node.property("last-surfaced-at")).thenReturn(Optional.empty());
        return node;
    }

    private SocialAvatarCognition buildWithGoalNodes(List<MindMapNode> goalNodes) {
        var mindMapStore = mock(MindMapStore.class);
        var goalAppraisal = mock(GoalAppraisal.class);
        var memoryStore = mock(CaseMemoryStore.class);

        var goalSg = mock(MindMapSubgraph.class);
        when(goalSg.type()).thenReturn(SubgraphTypes.GOAL);
        when(goalSg.id()).thenReturn("sg-1");
        when(mindMapStore.listSubgraphs("t1")).thenReturn(List.of(goalSg));
        when(mindMapStore.nodesIn("sg-1", "t1")).thenReturn(goalNodes);

        return SocialAvatarCognition.builder()
                .mood(mood).drives(drives).mentalModel(mentalModel)
                .userModel(userModel).strategy(strategy)
                .agentRegistry(Optional.of(registry))
                .mindMapStore(Optional.of(mindMapStore))
                .goalAppraisal(Optional.of(goalAppraisal))
                .memoryStore(Optional.of(memoryStore))
                .build();
    }

    @Test
    void tick_consumesDormantRevisionAndTransitionsGoal() {
        var node = goalNodeWithDecaySignal("n1", "Research Goal", "dormant", "research");
        var cog = buildWithGoalNodes(List.of(node));

        cog.tick("a1", "t1", Set.of());

        verify(registry).updateGoalLifecycleState("a1", "t1", "research",
                GoalLifecycleState.DORMANT);
    }

    @Test
    void tick_consumesAbandonRevisionAndTransitionsGoal() {
        var node = goalNodeWithDecaySignal("n1", "Research Goal", "abandon", "research");
        var cog = buildWithGoalNodes(List.of(node));

        cog.tick("a1", "t1", Set.of());

        verify(registry).updateGoalLifecycleState("a1", "t1", "research",
                GoalLifecycleState.ABANDONED);
    }

    @Test
    void tick_skipsRevisionWithoutEidosGoalName() {
        var node = goalNodeWithDecaySignal("n1", "Standalone", "dormant", null);
        var cog = buildWithGoalNodes(List.of(node));

        cog.tick("a1", "t1", Set.of());

        verify(registry, never()).updateGoalLifecycleState(any(), any(), any(), any());
    }

    @Test
    void tick_isolatesRevisionErrors() {
        var node1 = goalNodeWithDecaySignal("n1", "G1", "dormant", "goal-a");
        var node2 = goalNodeWithDecaySignal("n2", "G2", "abandon", "goal-b");
        var cog = buildWithGoalNodes(List.of(node1, node2));

        org.mockito.Mockito.doThrow(new RuntimeException("registry down"))
                .when(registry).updateGoalLifecycleState("a1", "t1", "goal-a",
                        GoalLifecycleState.DORMANT);

        cog.tick("a1", "t1", Set.of());

        verify(registry).updateGoalLifecycleState("a1", "t1", "goal-b",
                GoalLifecycleState.ABANDONED);
    }
}
