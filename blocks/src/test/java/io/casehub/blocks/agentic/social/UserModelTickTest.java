package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserModelTickTest {

    @Test
    void unchangedCarriesReason() {
        var tick = new UserModelTick.Unchanged("no signals");
        assertThat(tick.reason()).isEqualTo("no signals");
    }

    @Test
    void unchangedAllowsNullReason() {
        var tick = new UserModelTick.Unchanged(null);
        assertThat(tick.reason()).isNull();
    }

    @Test
    void updatedCarriesProfile() {
        var now = Instant.now();
        var profile = new UserProfile("a", "s", "t", "acquaintance", 0.3,
                10, 7, 2, 1, now, now, null, null, null, null, null, Map.of());
        var tick = new UserModelTick.Updated(profile);
        assertThat(tick.profile().relationshipStage()).isEqualTo("acquaintance");
    }

    @Test
    void synthesisedCarriesBothProfiles() {
        var now = Instant.now();
        var prev = new UserProfile("a", "s", "t", "stranger", 0.1,
                5, 3, 1, 1, now, now, null, null, null, null, null, Map.of());
        var curr = new UserProfile("a", "s", "t", "acquaintance", 0.3,
                10, 7, 2, 1, now, now, now,
                "formal", "tech", null, null, Map.of());
        var tick = new UserModelTick.Synthesised(curr, prev);
        assertThat(tick.profile().relationshipStage()).isEqualTo("acquaintance");
        assertThat(tick.previousProfile().relationshipStage()).isEqualTo("stranger");
    }

    @Test
    void synthesisedAllowsNullPreviousProfile() {
        var now = Instant.now();
        var curr = new UserProfile("a", "s", "t", "stranger", 0.1,
                1, 1, 0, 0, now, now, now,
                "casual", null, null, null, Map.of());
        var tick = new UserModelTick.Synthesised(curr, null);
        assertThat(tick.previousProfile()).isNull();
    }

    @Test
    void sealedExhaustiveness() {
        UserModelTick tick = new UserModelTick.Unchanged(null);
        var result = switch (tick) {
            case UserModelTick.Unchanged u -> "unchanged";
            case UserModelTick.Updated u -> "updated";
            case UserModelTick.Synthesised s -> "synthesised";
        };
        assertThat(result).isEqualTo("unchanged");
    }
}
