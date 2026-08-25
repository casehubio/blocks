package io.casehub.blocks.agentic.social.emergence;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SocialNormDetectorTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock CbrCaseMemoryStore cbrStore;
    SocialNormDetector detector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        detector = new SocialNormDetector(cbrStore, NormDetectionConfig.defaults(), CLOCK);
    }

    @Test
    void tick_noObservations_returnsNoChange() {
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of());
        var tick = detector.tick("tenant-1");
        assertThat(tick).isInstanceOf(NormDetectionTick.NoChange.class);
    }

    @Test
    void tick_belowMinObservations_noNormDetected() {
        var observations = buildObservations("verify-first", 5, true,
                Set.of("agent1", "agent2"));
        stubObservations(observations);

        var tick = detector.tick("tenant-1");
        assertThat(tick).isInstanceOf(NormDetectionTick.NoChange.class);
    }

    @Test
    void tick_belowMinAgents_noNormDetected() {
        var observations = buildObservations("verify-first", 15, true,
                Set.of("agent1"));
        stubObservations(observations);

        var tick = detector.tick("tenant-1");
        assertThat(tick).isInstanceOf(NormDetectionTick.NoChange.class);
    }

    @Test
    void tick_establishedNorm_highAdherence() {
        var observations = buildObservations("verify-first", 15, true,
                Set.of("agent1", "agent2"));
        stubObservations(observations);

        var tick = detector.tick("tenant-1");
        assertThat(tick).isInstanceOf(NormDetectionTick.Updated.class);
        var updated = (NormDetectionTick.Updated) tick;
        assertThat(updated.current().norms()).hasSize(1);
        var norm = updated.current().norms().getFirst();
        assertThat(norm.behavioralPattern()).isEqualTo("verify-first");
        assertThat(norm.strength()).isEqualTo(NormStrength.ESTABLISHED);
        assertThat(norm.adherenceRate()).isEqualTo(1.0);
        assertThat(norm.observationCount()).isEqualTo(15);
    }

    @Test
    void tick_emergingNorm_lowAdherence() {
        var followed = buildObservations("verify-first", 6, true,
                Set.of("agent1", "agent2"));
        var violated = buildObservations("verify-first", 6, false,
                Set.of("agent1", "agent2"));
        var all = new ArrayList<>(followed);
        all.addAll(violated);
        stubObservations(all);

        var tick = detector.tick("tenant-1");
        assertThat(tick).isInstanceOf(NormDetectionTick.Updated.class);
        var norm = ((NormDetectionTick.Updated) tick).current().norms().getFirst();
        assertThat(norm.strength()).isEqualTo(NormStrength.EMERGING);
        assertThat(norm.adherenceRate()).isEqualTo(0.5);
    }

    @Test
    void tick_decliningNorm_afterEstablished() {
        var highAdherence = buildObservations("verify-first", 15, true,
                Set.of("agent1", "agent2"));
        stubObservations(highAdherence);
        detector.tick("tenant-1");

        var followed = buildObservations("verify-first", 3, true,
                Set.of("agent1", "agent2"));
        var violated = buildObservations("verify-first", 10, false,
                Set.of("agent1", "agent2"));
        var lowAdherence = new ArrayList<>(followed);
        lowAdherence.addAll(violated);
        stubObservations(lowAdherence);

        var tick = detector.tick("tenant-1");
        assertThat(tick).isInstanceOf(NormDetectionTick.Updated.class);
        var norm = ((NormDetectionTick.Updated) tick).current().norms().getFirst();
        assertThat(norm.strength()).isEqualTo(NormStrength.DECLINING);
    }

    @Test
    void tick_multiplePatterns_detectsSeparateNorms() {
        var verifyObs = buildObservations("verify-first", 12, true,
                Set.of("agent1", "agent2"));
        var escalateObs = buildObservations("escalate-to-human", 11, true,
                Set.of("agent2", "agent3"));
        var all = new ArrayList<>(verifyObs);
        all.addAll(escalateObs);
        stubObservations(all);

        var tick = detector.tick("tenant-1");
        assertThat(tick).isInstanceOf(NormDetectionTick.Updated.class);
        var norms = ((NormDetectionTick.Updated) tick).current().norms();
        assertThat(norms).hasSize(2);
    }

    @Test
    void tick_noChange_whenSameStateOnSecondTick() {
        var observations = buildObservations("verify-first", 15, true,
                Set.of("agent1", "agent2"));
        stubObservations(observations);
        detector.tick("tenant-1");

        var tick2 = detector.tick("tenant-1");
        assertThat(tick2).isInstanceOf(NormDetectionTick.NoChange.class);
    }

    @Test
    void tick_newNormIds_tracked() {
        var observations = buildObservations("verify-first", 12, true,
                Set.of("agent1", "agent2"));
        stubObservations(observations);

        var tick = detector.tick("tenant-1");
        assertThat(tick).isInstanceOf(NormDetectionTick.Updated.class);
        var updated = (NormDetectionTick.Updated) tick;
        assertThat(updated.newNormIds()).contains("verify-first");
    }

    @Test
    void currentNorms_beforeTick_empty() {
        assertThat(detector.currentNorms("tenant-1")).isEmpty();
    }

    @Test
    void currentNorms_afterTick_returnsCachedState() {
        var observations = buildObservations("verify-first", 12, true,
                Set.of("agent1", "agent2"));
        stubObservations(observations);
        detector.tick("tenant-1");

        var norms = detector.currentNorms("tenant-1");
        assertThat(norms).isPresent();
        assertThat(norms.get().norms()).hasSize(1);
    }

    @Test
    void tick_perTenantIsolation() {
        var obs1 = buildObservations("verify-first", 12, true,
                Set.of("agent1", "agent2"));
        stubObservations(obs1);
        detector.tick("tenant-1");

        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of());
        detector.tick("tenant-2");

        assertThat(detector.currentNorms("tenant-1").get().norms()).hasSize(1);
        assertThat(detector.currentNorms("tenant-2").get().norms()).isEmpty();
    }

    @Test
    void establishedFilterOnDetectedNorms() {
        var established = buildObservations("verify-first", 12, true,
                Set.of("agent1", "agent2"));
        var mixed = buildObservations("sometimes-check", 6, true,
                Set.of("agent1", "agent2"));
        mixed.addAll(buildObservations("sometimes-check", 6, false,
                Set.of("agent1", "agent2")));
        var all = new ArrayList<>(established);
        all.addAll(mixed);
        stubObservations(all);

        detector.tick("tenant-1");
        var norms = detector.currentNorms("tenant-1").get();
        assertThat(norms.established()).hasSize(1);
        assertThat(norms.established().getFirst().behavioralPattern()).isEqualTo("verify-first");
    }

    // --- helpers ---

    private List<NormObservation> buildObservations(String pattern, int count,
                                                     boolean followed, Set<String> agents) {
        var list = new ArrayList<NormObservation>();
        for (int i = 0; i < count; i++) {
            list.add(new NormObservation(
                    "obs-" + pattern + "-" + i, "tenant-1", pattern,
                    agents, "conv-" + i,
                    NOW.minusSeconds(count - i), followed));
        }
        return list;
    }

    private void stubObservations(List<NormObservation> observations) {
        var scored = observations.stream()
                .map(obs -> {
                    var cbrCase = NormObservationSchema.toCbrCase(obs);
                    return new ScoredCbrCase<>(cbrCase, obs.observationId(), 1.0);
                })
                .toList();
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(scored);
    }
}
