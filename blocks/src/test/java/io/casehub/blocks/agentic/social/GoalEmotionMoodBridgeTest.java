package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.goal.CognitiveGoalOrchestrator;
import io.casehub.blocks.agentic.social.goal.CognitiveGoalState;
import io.casehub.blocks.agentic.social.goal.CognitiveGoalState.GoalEmotion;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.mindmap.MindMapNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoalEmotionMoodBridgeTest {

    private final CognitiveGoalOrchestrator goalOrchestrator = mock(CognitiveGoalOrchestrator.class);
    private final MoodOrchestrator moodOrchestrator = mock(MoodOrchestrator.class);
    private final GoalEmotionMoodBridge bridge = new GoalEmotionMoodBridge(goalOrchestrator, moodOrchestrator);

    @Test
    void tickWithEmotionsProducesWeightedAverageDirectShift() {
        var emotion1 = emotion(EmotionType.FEAR, 0.8, new PadProjection(-0.6, 0.8, -0.4));
        var emotion2 = emotion(EmotionType.HOPE, 0.4, new PadProjection(0.6, 0.4, 0.2));
        var goalEmotion = new GoalEmotion(mockGoal("goal-1"), List.of(emotion1, emotion2));
        var state = new CognitiveGoalState(List.of(goalEmotion), List.of());
        when(goalOrchestrator.currentState("agent", "tenant")).thenReturn(Optional.of(state));

        bridge.tick(new CognitionTickContext("agent", "tenant", null, (a, t) -> Set.of()));

        var captor = ArgumentCaptor.forClass(MoodSignal.class);
        verify(moodOrchestrator).record(captor.capture(), eq("agent"), eq("tenant"));
        var signal = (MoodSignal.DirectShift) captor.getValue();
        assertThat(signal.pleasureDelta()).isCloseTo(-0.2, within(0.01));
        assertThat(signal.arousalDelta()).isCloseTo(0.667, within(0.01));
        assertThat(signal.cause()).isEqualTo("goal-emotions");
    }

    @Test
    void tickWithNoGoalStateProducesNoSignal() {
        when(goalOrchestrator.currentState("agent", "tenant")).thenReturn(Optional.empty());

        bridge.tick(new CognitionTickContext("agent", "tenant", null, (a, t) -> Set.of()));

        verify(moodOrchestrator, never()).record(any(), any(), any());
    }

    @Test
    void tickWithNoEmotionsProducesNoSignal() {
        var goalEmotion = new GoalEmotion(mockGoal("goal-1"), List.of());
        var state = new CognitiveGoalState(List.of(goalEmotion), List.of());
        when(goalOrchestrator.currentState("agent", "tenant")).thenReturn(Optional.of(state));

        bridge.tick(new CognitionTickContext("agent", "tenant", null, (a, t) -> Set.of()));

        verify(moodOrchestrator, never()).record(any(), any(), any());
    }

    @Test
    void tickWithZeroIntensitySkips() {
        var emotion = emotion(EmotionType.FEAR, 0.0, new PadProjection(-0.5, 0.5, -0.3));
        var goalEmotion = new GoalEmotion(mockGoal("goal-1"), List.of(emotion));
        var state = new CognitiveGoalState(List.of(goalEmotion), List.of());
        when(goalOrchestrator.currentState("agent", "tenant")).thenReturn(Optional.of(state));

        bridge.tick(new CognitionTickContext("agent", "tenant", null, (a, t) -> Set.of()));

        verify(moodOrchestrator, never()).record(any(), any(), any());
    }

    @Test
    void multipleGoalsAggregateAcrossAll() {
        var emotion1 = emotion(EmotionType.FEAR, 0.6, new PadProjection(-0.8, 0.6, -0.2));
        var emotion2 = emotion(EmotionType.SATISFACTION, 0.6, new PadProjection(0.8, -0.2, 0.4));
        var goal1 = new GoalEmotion(mockGoal("goal-1"), List.of(emotion1));
        var goal2 = new GoalEmotion(mockGoal("goal-2"), List.of(emotion2));
        var state = new CognitiveGoalState(List.of(goal1, goal2), List.of());
        when(goalOrchestrator.currentState("agent", "tenant")).thenReturn(Optional.of(state));

        bridge.tick(new CognitionTickContext("agent", "tenant", null, (a, t) -> Set.of()));

        var captor = ArgumentCaptor.forClass(MoodSignal.class);
        verify(moodOrchestrator).record(captor.capture(), eq("agent"), eq("tenant"));
        var signal = (MoodSignal.DirectShift) captor.getValue();
        assertThat(signal.pleasureDelta()).isCloseTo(0.0, within(0.01));
    }

    @Test
    void padValuesClampedToValidRange() {
        var emotion = emotion(EmotionType.DISTRESS, 1.0, new PadProjection(-3.0, 3.0, -3.0));
        var goalEmotion = new GoalEmotion(mockGoal("goal-1"), List.of(emotion));
        var state = new CognitiveGoalState(List.of(goalEmotion), List.of());
        when(goalOrchestrator.currentState("agent", "tenant")).thenReturn(Optional.of(state));

        bridge.tick(new CognitionTickContext("agent", "tenant", null, (a, t) -> Set.of()));

        var captor = ArgumentCaptor.forClass(MoodSignal.class);
        verify(moodOrchestrator).record(captor.capture(), eq("agent"), eq("tenant"));
        var signal = (MoodSignal.DirectShift) captor.getValue();
        assertThat(signal.pleasureDelta()).isEqualTo(-2.0);
        assertThat(signal.arousalDelta()).isEqualTo(2.0);
    }

    private static CognitiveEmotion emotion(EmotionType type, double intensity, PadProjection pad) {
        return new CognitiveEmotion(type, intensity, "subject-1", Instant.now(),
                EmotionSource.INTRINSIC, pad);
    }

    private static MindMapNode mockGoal(String name) {
        var node = mock(MindMapNode.class);
        when(node.name()).thenReturn(name);
        when(node.id()).thenReturn(name);
        return node;
    }
}
