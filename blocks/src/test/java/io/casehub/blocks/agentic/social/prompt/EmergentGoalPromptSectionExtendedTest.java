package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.goal.CognitiveGoalConfig;
import io.casehub.blocks.agentic.social.goal.CognitiveGoalOrchestrator;
import io.casehub.blocks.agentic.social.goal.CognitiveGoalState;
import io.casehub.blocks.agentic.social.goal.DriveGoalProposal;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.neocortex.cognitive.AlmaPadTable;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmergentGoalPromptSectionExtendedTest {

    private InMemoryMindMapStore mindMapStore;
    private String goalSubgraphId;
    private static final String TENANT = "t1";
    private static final String AGENT = "agent-1";

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        goalSubgraphId = mindMapStore.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
    }

    @Test
    void driveGoalsOnly_rendersAsBeforeWhenNoCognitiveOrchestrator() {
        var driveOrch = mock(GoalProposalOrchestrator.class);
        when(driveOrch.currentProposals(AGENT, TENANT)).thenReturn(Optional.of(List.of(
                new DriveGoalProposal(DriveAxis.CURIOSITY, "explore-topic",
                        "Explore quantum computing", "curiosity drive",
                        0.7, null, null))));

        var section = new EmergentGoalPromptSection(driveOrch, null, CognitiveGoalConfig.defaults());
        var result = section.contribute(promptContext());

        assertThat(result).isNotNull();
        assertThat(result).contains("Explore quantum computing");
        assertThat(result).contains("curiosity");
    }

    @Test
    void cognitiveGoalsOnly_rendersWithEmotionLabels() {
        var driveOrch = mock(GoalProposalOrchestrator.class);
        when(driveOrch.currentProposals(AGENT, TENANT)).thenReturn(Optional.empty());

        var cogOrch = mock(CognitiveGoalOrchestrator.class);
        var goalNode = createGoal("Buy birthday gift", "active", 0.85, 0.9);
        var emotions = List.of(
                new CognitiveEmotion(EmotionType.FEAR, 0.8, goalNode.id(), Instant.now(),
                        EmotionSource.INTRINSIC, AlmaPadTable.project(EmotionType.FEAR, 0.8)));
        when(cogOrch.currentState(AGENT, TENANT)).thenReturn(Optional.of(
                new CognitiveGoalState(
                        List.of(new CognitiveGoalState.GoalEmotion(goalNode, emotions)),
                        List.of())));

        var section = new EmergentGoalPromptSection(driveOrch, cogOrch, CognitiveGoalConfig.defaults());
        var result = section.contribute(promptContext());

        assertThat(result).isNotNull();
        assertThat(result).contains("Buy birthday gift");
        assertThat(result).contains("CONCERNED");
    }

    @Test
    void bothSources_mergedAndRanked() {
        var driveOrch = mock(GoalProposalOrchestrator.class);
        when(driveOrch.currentProposals(AGENT, TENANT)).thenReturn(Optional.of(List.of(
                new DriveGoalProposal(DriveAxis.CURIOSITY, "explore-topic",
                        "Explore quantum computing", "curiosity drive",
                        0.6, null, null))));

        var cogOrch = mock(CognitiveGoalOrchestrator.class);
        var goalNode = createGoal("Buy birthday gift", "active", 0.85, 0.9);
        var emotions = List.of(
                new CognitiveEmotion(EmotionType.FEAR, 0.7, goalNode.id(), Instant.now(),
                        EmotionSource.INTRINSIC, AlmaPadTable.project(EmotionType.FEAR, 0.7)));
        when(cogOrch.currentState(AGENT, TENANT)).thenReturn(Optional.of(
                new CognitiveGoalState(
                        List.of(new CognitiveGoalState.GoalEmotion(goalNode, emotions)),
                        List.of())));

        var section = new EmergentGoalPromptSection(driveOrch, cogOrch, CognitiveGoalConfig.defaults());
        var result = section.contribute(promptContext());

        assertThat(result).isNotNull();
        assertThat(result).contains("Buy birthday gift");
        assertThat(result).contains("Explore quantum computing");
    }

    @Test
    void satisfactionEmotion_rendersCompleted() {
        var driveOrch = mock(GoalProposalOrchestrator.class);
        when(driveOrch.currentProposals(AGENT, TENANT)).thenReturn(Optional.empty());

        var cogOrch = mock(CognitiveGoalOrchestrator.class);
        var goalNode = createGoal("Submit report", "completed", 0.7, 1.0);
        var emotions = List.of(
                new CognitiveEmotion(EmotionType.SATISFACTION, 0.7, goalNode.id(), Instant.now(),
                        EmotionSource.INTRINSIC, AlmaPadTable.project(EmotionType.SATISFACTION, 0.7)));
        when(cogOrch.currentState(AGENT, TENANT)).thenReturn(Optional.of(
                new CognitiveGoalState(
                        List.of(new CognitiveGoalState.GoalEmotion(goalNode, emotions)),
                        List.of())));

        var section = new EmergentGoalPromptSection(driveOrch, cogOrch, CognitiveGoalConfig.defaults());
        var result = section.contribute(promptContext());

        assertThat(result).isNotNull();
        assertThat(result).contains("COMPLETED");
    }

    @Test
    void pityEmotion_rendersConcernForOthers() {
        var driveOrch = mock(GoalProposalOrchestrator.class);
        when(driveOrch.currentProposals(AGENT, TENANT)).thenReturn(Optional.empty());

        var cogOrch = mock(CognitiveGoalOrchestrator.class);
        var goalNode = createGoal("Check on colleague", "active", 0.4, 0.5);
        var emotions = List.of(
                new CognitiveEmotion(EmotionType.PITY, 0.6, "colleague-node", Instant.now(),
                        EmotionSource.EMPATHIC, AlmaPadTable.project(EmotionType.PITY, 0.6)));
        when(cogOrch.currentState(AGENT, TENANT)).thenReturn(Optional.of(
                new CognitiveGoalState(
                        List.of(new CognitiveGoalState.GoalEmotion(goalNode, emotions)),
                        List.of())));

        var section = new EmergentGoalPromptSection(driveOrch, cogOrch, CognitiveGoalConfig.defaults());
        var result = section.contribute(promptContext());

        assertThat(result).isNotNull();
        assertThat(result).contains("CONCERNED FOR OTHERS");
    }

    @Test
    void noGoals_returnsNull() {
        var driveOrch = mock(GoalProposalOrchestrator.class);
        when(driveOrch.currentProposals(AGENT, TENANT)).thenReturn(Optional.empty());

        var cogOrch = mock(CognitiveGoalOrchestrator.class);
        when(cogOrch.currentState(AGENT, TENANT)).thenReturn(Optional.empty());

        var section = new EmergentGoalPromptSection(driveOrch, cogOrch, CognitiveGoalConfig.defaults());
        assertThat(section.contribute(promptContext())).isNull();
    }

    @Test
    void surfacingCount_renderedWhenGreaterThanOne() {
        var driveOrch = mock(GoalProposalOrchestrator.class);
        when(driveOrch.currentProposals(AGENT, TENANT)).thenReturn(Optional.empty());

        var cogOrch = mock(CognitiveGoalOrchestrator.class);
        var goalNode = createGoalWithSurfacing("Buy gift", "active", 0.85, 0.9, 5);
        var emotions = List.of(
                new CognitiveEmotion(EmotionType.FEAR, 0.8, goalNode.id(), Instant.now(),
                        EmotionSource.INTRINSIC, AlmaPadTable.project(EmotionType.FEAR, 0.8)));
        when(cogOrch.currentState(AGENT, TENANT)).thenReturn(Optional.of(
                new CognitiveGoalState(
                        List.of(new CognitiveGoalState.GoalEmotion(goalNode, emotions)),
                        List.of())));

        var section = new EmergentGoalPromptSection(driveOrch, cogOrch, CognitiveGoalConfig.defaults());
        var result = section.contribute(promptContext());

        assertThat(result).contains("reminded 5 times");
    }

    private PromptContext promptContext() {
        return new PromptContext(AGENT, TENANT, null);
    }

    private MindMapNode createGoal(String name, String status, double priority, double urgency) {
        String id = mindMapStore.addNode(
                NodeInput.of(name, goalSubgraphId)
                        .withProperties(Map.of("status", status,
                                "priority", String.valueOf(priority),
                                "urgency", String.valueOf(urgency))),
                TENANT);
        return mindMapStore.getNode(id, TENANT);
    }

    private MindMapNode createGoalWithSurfacing(String name, String status,
                                                 double priority, double urgency,
                                                 int surfacingCount) {
        String id = mindMapStore.addNode(
                NodeInput.of(name, goalSubgraphId)
                        .withProperties(Map.of("status", status,
                                "priority", String.valueOf(priority),
                                "urgency", String.valueOf(urgency),
                                "surfaced-count", String.valueOf(surfacingCount))),
                TENANT);
        return mindMapStore.getNode(id, TENANT);
    }
}
