package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class StrategyProfileTest {

    @Test void requiresNonNullAgentId() {
        assertThatThrownBy(() -> new StrategyProfile(
                null, "t", Map.of(), List.of(), Instant.now(), 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void requiresNonNullTenantId() {
        assertThatThrownBy(() -> new StrategyProfile(
                "a", null, Map.of(), List.of(), Instant.now(), 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void requiresNonNullDimensions() {
        assertThatThrownBy(() -> new StrategyProfile(
                "a", "t", null, List.of(), Instant.now(), 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void defensiveCopiesDimensions() {
        var dims = new HashMap<>(Map.of("verbosity", 0.5));
        var profile = new StrategyProfile("a", "t", dims, List.of(), Instant.now(), 0);
        dims.put("hacked", 1.0);
        assertThat(profile.dimensions()).doesNotContainKey("hacked");
    }

    @Test void defensiveCopiesGuidelines() {
        var list = new ArrayList<>(List.of("be concise"));
        var profile = new StrategyProfile("a", "t", Map.of(), list, Instant.now(), 0);
        list.add("hacked");
        assertThat(profile.guidelines()).hasSize(1);
    }

    @Test void toPromptSection_emptyGuidelines() {
        var profile = new StrategyProfile("a", "t", Map.of(), List.of(), Instant.now(), 0);
        assertThat(profile.toPromptSection()).isEmpty();
    }

    @Test void toPromptSection_formatsGuidelines() {
        var profile = new StrategyProfile("a", "t", Map.of(),
                List.of("Be concise", "Ask questions"), Instant.now(), 0);
        String section = profile.toPromptSection();
        assertThat(section).contains("## Interaction Strategy");
        assertThat(section).contains("- Be concise");
        assertThat(section).contains("- Ask questions");
    }
}
