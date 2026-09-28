package io.casehub.blocks.agentic.social.reflection;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.reflection.ReflectionEvent;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmReflectionSynthesizerTest {

    private static final MemoryDomain EXPERIENCE = new MemoryDomain("experience");

    private Memory mem(String memoryId, String caseId, String turnId, String eventType,
                       Instant createdAt, String description, Map<String, String> extraAttrs) {
        var attrs = new HashMap<>(Map.of(
            ExperienceAttributeKeys.EVENT_TYPE, eventType,
            ExperienceAttributeKeys.TIMESTAMP, createdAt.toString()));
        if (turnId != null) attrs.put(ExperienceAttributeKeys.TURN_ID, turnId);
        attrs.putAll(extraAttrs);
        return new Memory(memoryId, Subject.of("agent", "agent-1"), EXPERIENCE, "tenant-1",
            caseId, description, attrs, createdAt, null, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Instance<AgentProvider> mockProviderInstance(AgentProvider provider) {
        Instance<AgentProvider> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(provider != null);
        when(instance.isUnsatisfied()).thenReturn(provider == null);
        if (provider != null) when(instance.get()).thenReturn(provider);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private Instance<CaseMemoryStore> mockStoreInstance(CaseMemoryStore store) {
        Instance<CaseMemoryStore> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(store != null);
        if (store != null) when(instance.get()).thenReturn(store);
        return instance;
    }

    private AgentProvider mockProvider(String textResponse) {
        AgentProvider provider = mock(AgentProvider.class);
        var events = Multi.createFrom().items(
            (AgentEvent) new AgentEvent.TextDelta(textResponse),
            new AgentEvent.InvocationComplete(100, 50, 0, 0, 0, null, 1000, 900, null, 1, false)
        );
        when(provider.invoke(any(AgentSessionConfig.class))).thenReturn(events);
        return provider;
    }

    @Test
    void returnsEmptyWhenAgentProviderUnavailable() {
        var synth = new LlmReflectionSynthesizer(mockProviderInstance(null), mockStoreInstance(null));
        var result = synth.synthesize("agent-1", "tenant-1", List.of(), 1);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoSources() {
        var provider = mockProvider("{\"heuristics\": []}");
        var synth = new LlmReflectionSynthesizer(mockProviderInstance(provider), mockStoreInstance(null));
        var result = synth.synthesize("agent-1", "tenant-1", List.of(), 1);
        assertThat(result).isEmpty();
    }

    @Test
    void extractsHeuristicsFromValidResponse() {
        var now = Instant.now();
        var sources = List.of(
            mem("m1", "case-1", "t1", "observation", now, "User asked about billing", Map.of()),
            mem("m2", "case-1", "t2", "action", now.plusSeconds(1), "Looked up account", Map.of()),
            mem("m3", "case-1", "t3", "outcome", now.plusSeconds(2), "Resolved issue",
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "success",
                       ExperienceAttributeKeys.RESULT, "resolved"))
        );

        String json = """
            {"heuristics": [{
                "condition": "user asks about billing",
                "action": "look up account first before asking clarifying questions",
                "source_cases": ["case-1"],
                "source_turns": ["t1", "t2"]
            }]}""";

        var provider = mockProvider(json);
        var synth = new LlmReflectionSynthesizer(mockProviderInstance(provider), mockStoreInstance(null));
        var result = synth.synthesize("agent-1", "tenant-1", sources, 1);

        assertThat(result).hasSize(1);
        ReflectionEvent event = result.get(0);
        assertThat(event.insight()).isEqualTo(
            "When user asks about billing, look up account first before asking clarifying questions");
        assertThat(event.agentId()).isEqualTo("agent-1");
        assertThat(event.tenantId()).isEqualTo("tenant-1");
        assertThat(event.level()).isEqualTo(1);
        assertThat(event.sourceMemoryIds()).containsExactlyInAnyOrder("m1", "m2");
    }

    @Test
    void handlesMultipleHeuristics() {
        var now = Instant.now();
        var sources = List.of(
            mem("m1", "case-1", "t1", "observation", now, "Observed X", Map.of()),
            mem("m2", "case-1", "t2", "outcome", now.plusSeconds(1), "Failed",
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "failed",
                       ExperienceAttributeKeys.RESULT, "error"))
        );

        String json = """
            {"heuristics": [
                {"condition": "X occurs", "action": "avoid Y",
                 "source_cases": ["case-1"], "source_turns": ["t1"]},
                {"condition": "after failure", "action": "try Z",
                 "source_cases": ["case-1"], "source_turns": ["t2"]}
            ]}""";

        var provider = mockProvider(json);
        var synth = new LlmReflectionSynthesizer(mockProviderInstance(provider), mockStoreInstance(null));
        var result = synth.synthesize("agent-1", "tenant-1", sources, 1);

        assertThat(result).hasSize(2);
    }

    @Test
    void handlesMalformedJson() {
        var now = Instant.now();
        var sources = List.of(
            mem("m1", "case-1", "t1", "observation", now, "Observed X", Map.of())
        );

        var provider = mockProvider("not valid json at all");
        var synth = new LlmReflectionSynthesizer(mockProviderInstance(provider), mockStoreInstance(null));
        var result = synth.synthesize("agent-1", "tenant-1", sources, 1);

        assertThat(result).isEmpty();
    }

    @Test
    void skipsHeuristicsWithInvalidSourceTurns() {
        var now = Instant.now();
        var sources = List.of(
            mem("m1", "case-1", "t1", "observation", now, "Observed X", Map.of())
        );

        String json = """
            {"heuristics": [{
                "condition": "X occurs", "action": "avoid Y",
                "source_cases": ["case-1"], "source_turns": ["nonexistent-turn"]
            }]}""";

        var provider = mockProvider(json);
        var synth = new LlmReflectionSynthesizer(mockProviderInstance(provider), mockStoreInstance(null));
        var result = synth.synthesize("agent-1", "tenant-1", sources, 1);

        assertThat(result).isEmpty();
    }

    @Test
    void tagsDerivationMetadata() {
        var now = Instant.now();
        var sources = List.of(
            mem("m1", "case-1", "t1", "outcome", now, "Failed",
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "failed",
                       ExperienceAttributeKeys.RESULT, "error"))
        );

        String json = """
            {"heuristics": [{
                "condition": "error occurs", "action": "retry with backoff",
                "source_cases": ["case-1"], "source_turns": ["t1"]
            }]}""";

        var provider = mockProvider(json);
        var synth = new LlmReflectionSynthesizer(mockProviderInstance(provider), mockStoreInstance(null));
        var result = synth.synthesize("agent-1", "tenant-1", sources, 1);

        assertThat(result.get(0).metadata()).containsEntry("derivation", "failure");
    }
}
