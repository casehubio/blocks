package io.casehub.blocks.social.jpa.quarkus;

import io.casehub.blocks.agentic.social.EngagementEvidence;
import io.casehub.blocks.agentic.social.StrategyProfile;
import io.casehub.blocks.agentic.social.StrategyStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class JpaStrategyStoreTest {

    @Inject
    StrategyStore store;

    @Test
    void storeAndLookup() {
        var now = Instant.now();
        var profile = new StrategyProfile("agent-st1", "tenant-st1",
                Map.of("verbosity", 0.5, "formality", 0.8), List.of("Be concise"), now, 5);
        store.store(profile);
        var found = store.lookup("agent-st1", "tenant-st1");
        assertThat(found).isPresent();
        assertThat(found.get().dimensions().get("verbosity")).isEqualTo(0.5);
        assertThat(found.get().guidelines()).contains("Be concise");
        assertThat(found.get().evidenceCount()).isEqualTo(5);
    }

    @Test
    void mergeOnDuplicateKey() {
        var now = Instant.now();
        store.store(new StrategyProfile("agent-st2", "tenant-st2",
                Map.of("verbosity", 0.3), List.of(), now, 0));
        store.store(new StrategyProfile("agent-st2", "tenant-st2",
                Map.of("verbosity", 0.7), List.of("Updated"), now, 10));
        var found = store.lookup("agent-st2", "tenant-st2");
        assertThat(found).isPresent();
        assertThat(found.get().dimensions().get("verbosity")).isEqualTo(0.7);
        assertThat(found.get().guidelines()).contains("Updated");
    }

    @Test
    void storeAndRetrieveEvidence() {
        var now = Instant.now();
        store.storeEvidence(new EngagementEvidence("agent-st3", "user1", "tenant-st3",
                "conv1", "Chat about weather", 5, 0.8, 42.0, 0.15,
                Map.of("verbosity", 0.6), now));
        store.storeEvidence(new EngagementEvidence("agent-st3", "user2", "tenant-st3",
                "conv2", "Chat about code", 10, 0.9, 100.0, 0.3,
                Map.of("verbosity", 0.9), now.plusSeconds(60)));

        assertThat(store.evidenceCount("agent-st3", "tenant-st3")).isEqualTo(2);

        var recent = store.recentEvidence("agent-st3", "tenant-st3", 10);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).recordedAt()).isAfter(recent.get(1).recordedAt());
        assertThat(recent.get(0).subjectId()).isEqualTo("user2");
    }

    @Test
    void subjectInsights() {
        var now = Instant.now();
        store.storeEvidence(new EngagementEvidence("agent-st4", "target-user", "tenant-st4",
                null, "Helpful conversation", 8, 0.75, 60.0, 0.2,
                Map.of(), now));
        var insights = store.subjectInsights("agent-st4", "target-user", "tenant-st4");
        assertThat(insights).hasSize(1);
        assertThat(insights.get(0)).contains("engagement=75%");
        assertThat(insights.get(0)).contains("turns=8");
    }

    @Test
    void eraseAgent() {
        var now = Instant.now();
        store.store(new StrategyProfile("agent-st5", "tenant-st5",
                Map.of(), List.of(), now, 0));
        store.storeEvidence(new EngagementEvidence("agent-st5", "user1", "tenant-st5",
                null, null, 3, 0.5, 30.0, 0.0, Map.of(), now));
        store.eraseAgent("agent-st5", "tenant-st5");
        assertThat(store.lookup("agent-st5", "tenant-st5")).isEmpty();
        assertThat(store.evidenceCount("agent-st5", "tenant-st5")).isEqualTo(0);
    }

    @Test
    void eraseSubject() {
        var now = Instant.now();
        store.storeEvidence(new EngagementEvidence("agent-st6", "subject-x", "tenant-st6",
                null, null, 3, 0.5, 30.0, 0.0, Map.of(), now));
        store.eraseSubject("subject-x", "tenant-st6");
        assertThat(store.recentEvidence("agent-st6", "tenant-st6", 10)).isEmpty();
    }
}
