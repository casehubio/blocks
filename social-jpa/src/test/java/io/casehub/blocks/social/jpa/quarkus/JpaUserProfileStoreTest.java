package io.casehub.blocks.social.jpa.quarkus;

import io.casehub.blocks.agentic.social.UserProfile;
import io.casehub.blocks.agentic.social.UserProfileStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class JpaUserProfileStoreTest {

    @Inject
    UserProfileStore store;

    @Test
    void storeAndLookup() {
        var now = Instant.now();
        var profile = new UserProfile("agent-up1", "subject-up1", "tenant-up1",
                "newcomer", 0.5, 10, 7, 2, 1,
                now, now, null, null, null, null, null, Map.of());
        store.store(profile);
        var found = store.lookup("agent-up1", "subject-up1", "tenant-up1");
        assertThat(found).isPresent();
        assertThat(found.get().familiarityScore()).isEqualTo(0.5);
    }

    @Test
    void lookupMissing() {
        assertThat(store.lookup("none", "none", "none")).isEmpty();
    }

    @Test
    void findByAgent() {
        var now = Instant.now();
        store.store(new UserProfile("agent-up2", "s1", "tenant-up2", "newcomer", 0.3, 5, 3, 1, 1,
                now, now, null, null, null, null, null, Map.of()));
        store.store(new UserProfile("agent-up2", "s2", "tenant-up2", "familiar", 0.7, 20, 15, 3, 2,
                now, now, null, null, null, null, null, Map.of()));
        var results = store.findByAgent("agent-up2", "tenant-up2");
        assertThat(results).hasSize(2);
    }

    @Test
    void mergeOnDuplicateKey() {
        var now = Instant.now();
        store.store(new UserProfile("agent-up3", "s1", "tenant-up3", "newcomer", 0.3, 5, 3, 1, 1,
                now, now, null, null, null, null, null, Map.of()));
        store.store(new UserProfile("agent-up3", "s1", "tenant-up3", "familiar", 0.8, 15, 10, 3, 2,
                now, now, null, "direct", null, null, null, Map.of("k", "v")));
        var found = store.lookup("agent-up3", "s1", "tenant-up3");
        assertThat(found).isPresent();
        assertThat(found.get().familiarityScore()).isEqualTo(0.8);
        assertThat(found.get().communicationStyle()).isEqualTo("direct");
        assertThat(found.get().metadata()).containsEntry("k", "v");
    }

    @Test
    void eraseSubject() {
        var now = Instant.now();
        store.store(new UserProfile("agent-up4", "s1", "tenant-up4", "newcomer", 0.3, 5, 3, 1, 1,
                now, now, null, null, null, null, null, Map.of()));
        store.eraseSubject("s1", "tenant-up4");
        assertThat(store.lookup("agent-up4", "s1", "tenant-up4")).isEmpty();
    }
}
