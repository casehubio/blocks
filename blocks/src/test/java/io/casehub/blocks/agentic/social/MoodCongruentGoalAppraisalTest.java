package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.mindmap.AppraisalContext;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import io.casehub.neocortex.mindmap.GoalAppraisal;
import io.casehub.neocortex.mindmap.MindMapNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MoodCongruentGoalAppraisalTest {

    private static final String AGENT = "agent-1";
    private static final String TENANT = "tenant-1";

    private GoalAppraisal delegate;
    private MoodOrchestrator moodOrchestrator;
    private MoodCongruentGoalAppraisal appraisal;

    @BeforeEach
    void setUp() {
        delegate = mock(GoalAppraisal.class);
        moodOrchestrator = mock(MoodOrchestrator.class);
        appraisal = new MoodCongruentGoalAppraisal(delegate, moodOrchestrator, MoodCongruenceConfig.defaults());
        when(delegate.appraise(any(), any())).thenReturn(List.of());
    }

    // --- no mood: pass-through ---

    @Test
    void noMood_delegatesWithOriginalContext() {
        when(moodOrchestrator.currentMood(AGENT, TENANT)).thenReturn(Optional.empty());
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        assertThat(captor.getValue().weights()).isEqualTo(AppraisalWeights.NEUTRAL);
    }

    // --- neutral mood: no modulation ---

    @Test
    void neutralMood_noModulation() {
        setMood(0.0, 0.0, 0.0);
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        var weights = captor.getValue().weights();
        assertThat(weights.fearOnsetThreshold()).isCloseTo(1.0, within(0.001));
        assertThat(weights.urgencyWeight()).isCloseTo(1.0, within(0.001));
    }

    // --- arousal modulation ---

    @Test
    void highArousal_lowersFearThreshold() {
        setMood(0.0, 0.6, 0.0);
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        assertThat(captor.getValue().weights().fearOnsetThreshold()).isLessThan(1.0);
    }

    @Test
    void lowArousal_raisesFearThreshold() {
        setMood(0.0, -0.6, 0.0);
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        assertThat(captor.getValue().weights().fearOnsetThreshold()).isGreaterThan(1.0);
    }

    @Test
    void highArousal_amplifiesUrgencyWeight() {
        setMood(0.0, 0.6, 0.0);
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        assertThat(captor.getValue().weights().urgencyWeight()).isGreaterThan(1.0);
    }

    // --- pleasure modulation ---

    @Test
    void highPleasure_raisesFearThreshold() {
        setMood(0.6, 0.0, 0.0);
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        assertThat(captor.getValue().weights().fearOnsetThreshold()).isGreaterThan(1.0);
    }

    @Test
    void lowPleasure_lowersFearThreshold() {
        setMood(-0.6, 0.0, 0.0);
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        assertThat(captor.getValue().weights().fearOnsetThreshold()).isLessThan(1.0);
    }

    // --- combined axes ---

    @Test
    void highArousalAndLowPleasure_compoundLowerThreshold() {
        setMood(-0.5, 0.7, 0.0);
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        var threshold = captor.getValue().weights().fearOnsetThreshold();
        assertThat(threshold).isLessThan(0.7);
    }

    @Test
    void highPleasureAndLowArousal_compoundRaiseThreshold() {
        setMood(0.5, -0.5, 0.0);
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        var threshold = captor.getValue().weights().fearOnsetThreshold();
        assertThat(threshold).isGreaterThan(1.3);
    }

    // --- clamping ---

    @Test
    void extremeMood_thresholdClampedToMinimum() {
        setMood(-1.0, 1.0, 0.0);
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        var threshold = captor.getValue().weights().fearOnsetThreshold();
        assertThat(threshold).isGreaterThanOrEqualTo(0.3);
    }

    @Test
    void extremePositiveMood_thresholdClampedToMaximum() {
        setMood(1.0, -1.0, 0.0);
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        var threshold = captor.getValue().weights().fearOnsetThreshold();
        assertThat(threshold).isLessThanOrEqualTo(2.5);
    }

    @Test
    void urgencyWeightNeverBelowMinimum() {
        setMood(0.0, -1.0, 0.0);
        var ctx = ctx(AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        assertThat(captor.getValue().weights().urgencyWeight()).isGreaterThanOrEqualTo(0.5);
    }

    // --- non-zero baseline ---

    @Test
    void displacementComputedRelativeToBaseline() {
        var anxiousBaseline = new PadProjection(-0.2, 0.3, 0.0);
        setMood(anxiousBaseline.pleasure(), anxiousBaseline.arousal() + 0.4, 0.0);
        var ctx = ctxWithBaseline(AppraisalWeights.NEUTRAL, anxiousBaseline);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        assertThat(captor.getValue().weights().fearOnsetThreshold()).isLessThan(1.0);
    }

    @Test
    void moodAtBaseline_noModulation() {
        var baseline = new PadProjection(0.3, -0.2, 0.1);
        setMood(baseline.pleasure(), baseline.arousal(), baseline.dominance());
        var ctx = ctxWithBaseline(AppraisalWeights.NEUTRAL, baseline);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        assertThat(captor.getValue().weights().fearOnsetThreshold()).isCloseTo(1.0, within(0.001));
    }

    // --- preserves non-modulated weight fields ---

    @Test
    void nonModulatedFieldsPreserved() {
        setMood(0.0, 0.5, 0.0);
        var original = new AppraisalWeights(1.5, 0.8, 1.2, 1.3, 0.7);
        var ctx = ctx(original);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        var weights = captor.getValue().weights();
        assertThat(weights.relationshipWeight()).isEqualTo(0.8);
        assertThat(weights.selfStandardsStrictness()).isEqualTo(1.3);
        assertThat(weights.otherStandardsStrictness()).isEqualTo(0.7);
    }

    // --- context fields preserved ---

    @Test
    void contextFieldsPreservedExceptWeights() {
        setMood(0.0, 0.5, 0.0);
        var ctx = new AppraisalContext(TENANT, AGENT, PadProjection.NEUTRAL,
                3, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"),
                Map.of("alice", 0.7), AppraisalWeights.NEUTRAL);

        appraisal.appraise(mockGoal(), ctx);

        var captor = ArgumentCaptor.forClass(AppraisalContext.class);
        verify(delegate).appraise(any(), captor.capture());
        var captured = captor.getValue();
        assertThat(captured.tenantId()).isEqualTo(TENANT);
        assertThat(captured.agentId()).isEqualTo(AGENT);
        assertThat(captured.surfacingCount()).isEqualTo(3);
        assertThat(captured.lastProgressAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(captured.lastSurfacedAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(captured.relationshipScores()).containsEntry("alice", 0.7);
    }

    // --- config ---

    @Test
    void configDefaults_valid() {
        var config = MoodCongruenceConfig.defaults();
        assertThat(config.arousalCongruenceFactor()).isGreaterThan(0);
        assertThat(config.pleasureCongruenceFactor()).isGreaterThan(0);
        assertThat(config.minThresholdMultiplier()).isGreaterThan(0);
        assertThat(config.maxThresholdMultiplier()).isGreaterThan(config.minThresholdMultiplier());
    }

    // --- delegate return value ---

    @Test
    void returnsWhatDelegateReturns() {
        when(moodOrchestrator.currentMood(AGENT, TENANT)).thenReturn(Optional.empty());
        var expected = List.of(emotion(EmotionType.FEAR, 0.5));
        when(delegate.appraise(any(), any())).thenReturn(expected);

        var result = appraisal.appraise(mockGoal(), ctx(AppraisalWeights.NEUTRAL));
        assertThat(result).isSameAs(expected);
    }

    // --- helpers ---

    private void setMood(double pleasure, double arousal, double dominance) {
        var mood = new MoodState(AGENT, TENANT, Instant.now(),
                pleasure, arousal, dominance, "test", null, Set.of(), Map.of());
        when(moodOrchestrator.currentMood(AGENT, TENANT)).thenReturn(Optional.of(mood));
    }

    private AppraisalContext ctx(AppraisalWeights weights) {
        return new AppraisalContext(TENANT, AGENT, PadProjection.NEUTRAL,
                0, null, null, Map.of(), weights);
    }

    private AppraisalContext ctxWithBaseline(AppraisalWeights weights, PadProjection baseline) {
        return new AppraisalContext(TENANT, AGENT, baseline,
                0, null, null, Map.of(), weights);
    }

    private static CognitiveEmotion emotion(EmotionType type, double intensity) {
        return new CognitiveEmotion(type, intensity, "subject", Instant.now(),
                EmotionSource.INTRINSIC, PadProjection.NEUTRAL);
    }

    private static MindMapNode mockGoal() {
        var node = mock(MindMapNode.class);
        when(node.name()).thenReturn("test-goal");
        when(node.id()).thenReturn("goal-1");
        return node;
    }
}
