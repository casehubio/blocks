package io.casehub.blocks.agentic.yaml.llm;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectivePromptSectionTest {

    @Test
    void wrappedSectionsHaveDistinctNames() {
        PromptSection mood = ctx -> "mood content";
        PromptSection drives = ctx -> "drive content";
        PromptSection narrative = ctx -> "narrative content";

        var wrapped = List.of(
                DirectivePromptSection.wrap(mood, "MoodPromptSection"),
                DirectivePromptSection.wrap(drives, "DrivePromptSection"),
                DirectivePromptSection.wrap(narrative, "NarrativePromptSection"));

        var ctx = new PromptContext("agent", "tenant", "subject");
        var map = new LinkedHashMap<String, String>();
        for (var section : wrapped) {
            var text = section.contribute(ctx);
            if (text != null && !text.isBlank()) {
                var dps = (DirectivePromptSection) section;
                map.put(dps.delegateName(), text);
            }
        }

        assertThat(map).hasSize(3);
        assertThat(map).containsKeys(
                "MoodPromptSection", "DrivePromptSection", "NarrativePromptSection");
    }

    @Test
    void mapKeyCollisionWithGetClassSimpleName() {
        PromptSection mood = ctx -> "mood content";
        PromptSection drives = ctx -> "drive content";

        var wrapped = List.of(
                DirectivePromptSection.wrap(mood, "MoodPromptSection"),
                DirectivePromptSection.wrap(drives, "DrivePromptSection"));

        var ctx = new PromptContext("agent", "tenant", "subject");
        var collisionMap = new LinkedHashMap<String, String>();
        for (var section : wrapped) {
            var text = section.contribute(ctx);
            if (text != null && !text.isBlank()) {
                collisionMap.put(section.getClass().getSimpleName(), text);
            }
        }

        // Demonstrates the bug: getClass().getSimpleName() returns
        // "DirectivePromptSection" for all, so map collapses to 1 entry
        assertThat(collisionMap).hasSize(1);
        assertThat(collisionMap).containsKey("DirectivePromptSection");
    }

    @Test
    void nullDelegateContentReturnsNull() {
        PromptSection empty = ctx -> null;
        var wrapped = DirectivePromptSection.wrap(empty, "EmptySection");

        var ctx = new PromptContext("agent", "tenant", "subject");
        assertThat(wrapped.contribute(ctx)).isNull();
    }

    @Test
    void blankDelegateContentReturnsNull() {
        PromptSection blank = ctx -> "   ";
        var wrapped = DirectivePromptSection.wrap(blank, "BlankSection");

        var ctx = new PromptContext("agent", "tenant", "subject");
        assertThat(wrapped.contribute(ctx)).isNull();
    }

    @Test
    void wrapWithoutNameUsesClassSimpleName() {
        var section = new NamedTestSection();
        var wrapped = (DirectivePromptSection) DirectivePromptSection.wrap(section);

        assertThat(wrapped.delegateName()).isEqualTo("NamedTestSection");
    }

    private static class NamedTestSection implements PromptSection {
        @Override
        public String contribute(PromptContext context) {
            return "test";
        }
    }
}
