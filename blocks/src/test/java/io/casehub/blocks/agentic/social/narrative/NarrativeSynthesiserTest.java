package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NarrativeSynthesiserTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-08-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private ReflectionQueryStore queryStore;
    private NarrativeStore narrativeStore;
    private TestAgentProvider agentProvider;
    private NarrativeSynthesiser synthesiser;

    static class TestAgentProvider implements AgentProvider {
        private final String response;
        int invocationCount = 0;
        String lastUserPrompt;
        String lastSystemPrompt;
        boolean shouldThrow = false;

        TestAgentProvider(String response) {
            this.response = response;
        }

        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            invocationCount++;
            lastUserPrompt = config.userPrompt();
            lastSystemPrompt = config.systemPrompt();
            if (shouldThrow) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }
            return Multi.createFrom().items(
                    new AgentEvent.TextDelta(response),
                    new AgentEvent.InvocationComplete(
                            100, 50, 0, 0, 0, 0.001, 500L, 400L, "test", 1, false));
        }

        @Override
        public AgentSession openSession(AgentSessionInit init) {
            throw new UnsupportedOperationException();
        }
    }

    private static final String VALID_RESPONSE = """
            {
              "newEpisodes": [
                {
                  "description": "Helped team through production outage",
                  "emotionalValence": 0.7,
                  "thematicTags": ["crisis", "teamwork"],
                  "fromReflections": [0]
                }
              ],
              "themes": [
                {
                  "label": "crisis-helper",
                  "salience": 0.8,
                  "thematicTags": ["crisis"],
                  "axisWeights": {
                    "AFFILIATION": 0.5,
                    "COMPETENCE": 0.3
                  }
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        queryStore = mock(ReflectionQueryStore.class);
        narrativeStore = mock(NarrativeStore.class);
        agentProvider = new TestAgentProvider(VALID_RESPONSE);
        synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
                queryStore, NarrativeConfig.defaults(), CLOCK);
    }

    private ReflectionEntry reflection(String insight) {
        return new ReflectionEntry("agent-1", "tenant-1", insight,
                EARLIER, List.of("case-1"));
    }

    private NarrativeState existingState(Instant synthesisedAt,
                                          List<NarrativeFragment> fragments) {
        return new NarrativeState("agent-1", "tenant-1",
                NarrativeScope.INDIVIDUAL, fragments, synthesisedAt, 5);
    }

    private void setupReflections(int count) {
        var reflections = new ArrayList<ReflectionEntry>();
        for (int i = 0; i < count; i++) {
            reflections.add(reflection("unique-insight-" + i + "-" + System.nanoTime()));
        }
        when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(count);
        when(queryStore.findSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(reflections);
    }

    // --- Gate tests ---

    @Test
    void synthesise_noNewReflections_skipped() {
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(0);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
        assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
                .isEqualTo("no new reflections");
    }

    @Test
    void synthesise_insufficientReflections_skipped() {
        var recentSynthesis = FIXED_NOW.minus(Duration.ofMinutes(30));
        var recent          = existingState(recentSynthesis, List.of());
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(recent);
        when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), eq(recentSynthesis)))
                .thenReturn(2);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
        assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
                .startsWith("insufficient reflections");
        assertThat(agentProvider.invocationCount).isZero();
    }

    @Test
    void synthesise_lowNovelty_skipped() {
        var recentSynthesis = FIXED_NOW.minus(Duration.ofMinutes(30));
        var existing = existingState(recentSynthesis, List.of(
                new IndividualEpisode("e1", EARLIER, null, List.of(),
                                      "insight-0 insight-1", 0.5, List.of())));
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
        when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), eq(recentSynthesis)))
                .thenReturn(5);
        when(queryStore.findSince(eq("agent-1"), eq("tenant-1"), eq(recentSynthesis)))
                .thenReturn(List.of(
                        reflection("insight-0"),
                        reflection("insight-1"),
                        reflection("insight-0"),
                        reflection("insight-1"),
                        reflection("insight-0")));

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
        assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
                .isEqualTo("low novelty");
        assertThat(agentProvider.invocationCount).isZero();
    }

    @Test
    void synthesise_quietPeriodBypass() {
        var oldSynthesis = FIXED_NOW.minus(Duration.ofMinutes(150));
        var existing = existingState(oldSynthesis, List.of());
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
        when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(1);
        when(queryStore.findSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(List.of(reflection("quiet period insight")));

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Synthesised.class);
        assertThat(agentProvider.invocationCount).isEqualTo(1);
    }

    @Test
    void synthesise_quietPeriodBypass_requiresAtLeastOneReflection() {
        var oldSynthesis = FIXED_NOW.minus(Duration.ofMinutes(150));
        var existing = existingState(oldSynthesis, List.of());
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
        when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(0);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
    }

    // --- Core synthesis tests ---

    @Test
    void synthesise_firstSynthesis_noExistingState() {
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        setupReflections(5);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Synthesised.class);
        var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
        assertThat(synthesised.state().episodes()).hasSize(1);
        assertThat(synthesised.state().themes()).hasSize(1);
        assertThat(synthesised.newReflectionsConsumed()).isEqualTo(5);
        verify(narrativeStore).store(any());
    }

    @Test
    void synthesise_incrementalMerge_preservesExistingEpisodes() {
        var existingEpisode = new IndividualEpisode("existing-1", EARLIER, null,
                List.of("old"), "old experience", 0.3, List.of());
        var existing = existingState(EARLIER, List.of(existingEpisode));
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
        setupReflections(5);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
        assertThat(synthesised.state().episodes()).hasSize(2);
        assertThat(synthesised.state().episodes().stream()
                .map(IndividualEpisode::id).toList())
                .contains("existing-1");
    }

    @Test
    void synthesise_themesFullyReDerived() {
        var oldTheme = new DerivedTheme("old-t", EARLIER, null, List.of(),
                "old-theme", 0.5, Map.of(), List.of());
        var existing = existingState(EARLIER, List.of(oldTheme));
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
        setupReflections(5);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
        assertThat(synthesised.state().themes()).hasSize(1);
        assertThat(synthesised.state().themes().getFirst().label())
                .isEqualTo("crisis-helper");
    }

    // --- Pruning tests ---

    @Test
    void synthesise_episodePruning_dropsOldest() {
        var config = new NarrativeConfig(NarrativeSynthesisGate.defaults(),
                2, 10, 0.1, 20, "narrative", "narrative");
        synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
                queryStore, config, CLOCK);

        var e1 = new IndividualEpisode("old", EARLIER.minusSeconds(100), null,
                List.of(), "oldest", 0.1, List.of());
        var e2 = new IndividualEpisode("mid", EARLIER, null,
                List.of(), "middle", 0.2, List.of());
        var existing = existingState(EARLIER, List.of(e1, e2));
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
        setupReflections(5);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
        assertThat(synthesised.state().episodes()).hasSize(2);
        assertThat(synthesised.state().episodes().stream()
                .map(IndividualEpisode::id).toList())
                .doesNotContain("old");
    }

    @Test
    void synthesise_themePruning_dropsLowestSalience() {
        var config = new NarrativeConfig(NarrativeSynthesisGate.defaults(),
                50, 1, 0.1, 20, "narrative", "narrative");
        var twoThemeResponse = """
                {
                  "newEpisodes": [],
                  "themes": [
                    {"label": "low", "salience": 0.2, "thematicTags": [], "axisWeights": {}},
                    {"label": "high", "salience": 0.9, "thematicTags": [], "axisWeights": {}}
                  ]
                }
                """;
        agentProvider = new TestAgentProvider(twoThemeResponse);
        synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
                queryStore, config, CLOCK);
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        setupReflections(5);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
        assertThat(synthesised.state().themes()).hasSize(1);
        assertThat(synthesised.state().themes().getFirst().label()).isEqualTo("high");
    }

    @Test
    void synthesise_themeSalienceFloor() {
        var config = new NarrativeConfig(NarrativeSynthesisGate.defaults(),
                50, 10, 0.5, 20, "narrative", "narrative");
        var lowSalienceResponse = """
                {
                  "newEpisodes": [],
                  "themes": [
                    {"label": "weak", "salience": 0.3, "thematicTags": [], "axisWeights": {}},
                    {"label": "strong", "salience": 0.8, "thematicTags": [], "axisWeights": {}}
                  ]
                }
                """;
        agentProvider = new TestAgentProvider(lowSalienceResponse);
        synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
                queryStore, config, CLOCK);
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        setupReflections(5);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
        assertThat(synthesised.state().themes()).hasSize(1);
        assertThat(synthesised.state().themes().getFirst().label()).isEqualTo("strong");
    }

    // --- Error handling tests ---

    @Test
    void synthesise_llmFailure_skipped() {
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        setupReflections(5);
        agentProvider.shouldThrow = true;

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
        assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
                .isEqualTo("llm failure");
    }

    @Test
    void synthesise_parseFailure_skipped() {
        agentProvider = new TestAgentProvider("not json at all");
        synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
                queryStore, NarrativeConfig.defaults(), CLOCK);
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        setupReflections(5);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
        assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
                .isEqualTo("parse failure");
    }

    @Test
    void synthesise_partiallyInvalidResponse_writesValidItems() {
        var mixedResponse = """
                {
                  "newEpisodes": [
                    {"description": "valid episode", "emotionalValence": 0.5, "thematicTags": [], "fromReflections": [0]},
                    {"emotionalValence": 999}
                  ],
                  "themes": [
                    {"label": "valid", "salience": 0.7, "thematicTags": [], "axisWeights": {}}
                  ]
                }
                """;
        agentProvider = new TestAgentProvider(mixedResponse);
        synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
                queryStore, NarrativeConfig.defaults(), CLOCK);
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        setupReflections(5);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
        assertThat(synthesised.state().episodes()).hasSize(1);
        assertThat(synthesised.state().themes()).hasSize(1);
    }

    @Test
    void synthesise_emptyResult_skipped() {
        var emptyResponse = """
                {
                  "newEpisodes": [],
                  "themes": []
                }
                """;
        agentProvider = new TestAgentProvider(emptyResponse);
        synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
                queryStore, NarrativeConfig.defaults(), CLOCK);
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        setupReflections(5);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
        assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
                .isEqualTo("empty synthesis result");
        verify(narrativeStore, never()).store(any());
    }

    @Test
    void synthesise_emptyThemesWithEpisodes_skipped() {
        var noThemesResponse = """
                {
                  "newEpisodes": [
                    {"description": "some episode", "emotionalValence": 0.3, "thematicTags": [], "fromReflections": [0]}
                  ],
                  "themes": []
                }
                """;
        agentProvider = new TestAgentProvider(noThemesResponse);
        synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
                queryStore, NarrativeConfig.defaults(), CLOCK);
        var existingEpisode = new IndividualEpisode("e1", EARLIER, null,
                List.of(), "existing", 0.5, List.of());
        when(narrativeStore.load("agent-1", "tenant-1"))
                .thenReturn(existingState(EARLIER, List.of(existingEpisode)));
        setupReflections(5);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(tick).isInstanceOf(NarrativeSynthesisTick.Skipped.class);
        assertThat(((NarrativeSynthesisTick.Skipped) tick).reason())
                .isEqualTo("no themes produced");
        verify(narrativeStore, never()).store(any());
    }

    // --- Data mapping tests ---

    @Test
    void synthesise_reflectionIndexMapping() {
        var r0 = new ReflectionEntry("agent-1", "tenant-1", "insight-0",
                EARLIER, List.of("case-a", "case-b"));
        var r1 = new ReflectionEntry("agent-1", "tenant-1", "insight-1",
                EARLIER, List.of("case-c"));
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(5);
        when(queryStore.findSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(List.of(r0, r1, reflection("x"), reflection("y"), reflection("z")));

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
        var episode = synthesised.state().episodes().getFirst();
        assertThat(episode.sourceReflectionIds())
                .containsExactly("case-a", "case-b");
    }

    @Test
    void synthesise_themeLabelsIncludedInPrompt() {
        var theme = new DerivedTheme("t1", EARLIER, null, List.of(),
                "existing-theme", 0.6, Map.of(), List.of());
        var existing = existingState(EARLIER, List.of(theme));
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
        setupReflections(5);

        synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        assertThat(agentProvider.lastUserPrompt).contains("existing-theme");
    }

    @Test
    void synthesise_maxReflectionsCapped() {
        var config = new NarrativeConfig(NarrativeSynthesisGate.defaults(),
                50, 10, 0.1, 3, "narrative", "narrative");
        synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
                queryStore, config, CLOCK);
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        var reflections = new ArrayList<ReflectionEntry>();
        for (int i = 0; i < 10; i++) {
            reflections.add(reflection("unique-insight-" + i + "-cap"));
        }
        when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(10);
        when(queryStore.findSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(reflections);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
        assertThat(synthesised.newReflectionsConsumed()).isEqualTo(3);
    }

    @Test
    void synthesise_cappedReflections_watermarkPreserved() {
        var config = new NarrativeConfig(NarrativeSynthesisGate.defaults(),
                50, 10, 0.1, 3, "narrative", "narrative");
        synthesiser = new NarrativeSynthesiser(agentProvider, narrativeStore,
                queryStore, config, CLOCK);
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        var thirdReflectionTime = Instant.parse("2026-08-24T10:30:00Z");
        var reflections = new ArrayList<ReflectionEntry>();
        for (int i = 0; i < 10; i++) {
            reflections.add(new ReflectionEntry("agent-1", "tenant-1",
                    "unique-insight-" + i + "-wm",
                    EARLIER.plusSeconds(i * 60),
                    List.of("case-" + i)));
        }
        when(queryStore.countSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(10);
        when(queryStore.findSince(eq("agent-1"), eq("tenant-1"), any()))
                .thenReturn(reflections);

        synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        var captor = org.mockito.ArgumentCaptor.forClass(NarrativeState.class);
        verify(narrativeStore).store(captor.capture());
        var written = captor.getValue();
        assertThat(written.synthesisedAt())
                .isEqualTo(EARLIER.plusSeconds(2 * 60))
                .isNotEqualTo(FIXED_NOW);
    }

    @Test
    void synthesise_writesCorrectNarrativeState() {
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(null);
        setupReflections(5);

        synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        var captor = org.mockito.ArgumentCaptor.forClass(NarrativeState.class);
        verify(narrativeStore).store(captor.capture());
        var written = captor.getValue();
        assertThat(written.scopeId()).isEqualTo("agent-1");
        assertThat(written.tenantId()).isEqualTo("tenant-1");
        assertThat(written.scope()).isEqualTo(NarrativeScope.INDIVIDUAL);
        assertThat(written.synthesisedAt()).isEqualTo(FIXED_NOW);
        assertThat(written.reflectionCountAtSynthesis()).isEqualTo(5);
    }

    // --- Concurrency test ---

    @Test
    void synthesise_perAgentLocking() throws InterruptedException {
        when(narrativeStore.load(anyString(), anyString())).thenReturn(null);
        setupReflections(5);

        var errors = new java.util.concurrent.atomic.AtomicInteger(0);
        var latch = new java.util.concurrent.CountDownLatch(4);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 4; i++) {
                executor.submit(() -> {
                    try {
                        synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }
        assertThat(errors.get()).isZero();
    }

    // --- GroupEpisode tag matching test ---

    @Test
    void synthesise_groupEpisodesIncludedInTagMatching() {
        var groupEpisode = new GroupEpisode("g1", EARLIER, null,
                List.of("crisis"), "group crisis event", 0.6,
                Set.of("agent-1", "agent-2"), Map.of(), 0.8);
        var existing = existingState(EARLIER, List.of(groupEpisode));
        when(narrativeStore.load("agent-1", "tenant-1")).thenReturn(existing);
        setupReflections(5);

        var tick = synthesiser.synthesiseIfNeeded("agent-1", "tenant-1");

        var synthesised = (NarrativeSynthesisTick.Synthesised) tick;
        var crisisTheme = synthesised.state().themes().stream()
                .filter(t -> t.label().equals("crisis-helper"))
                .findFirst().orElseThrow();
        assertThat(crisisTheme.supportingFragmentIds()).contains("g1");
    }
}
