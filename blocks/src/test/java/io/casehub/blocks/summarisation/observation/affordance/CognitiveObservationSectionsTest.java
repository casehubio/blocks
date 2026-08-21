package io.casehub.blocks.summarisation.observation.affordance;

import io.casehub.blocks.summarisation.observation.ObservationResult;
import io.casehub.blocks.summarisation.observation.PartitionedDrain;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.GoalPriority;
import io.casehub.eidos.api.Visibility;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveObservationSectionsTest {

    private static final MemoryDomain DOMAIN = new MemoryDomain("test");

    private static Memory memory(String text) {
        return new Memory("m-1", "agent-1", DOMAIN, "t-1", "c-1",
                text, Map.of(), Instant.now(), 0.5);
    }

    private static AgentGoal goal(String name, String description, GoalPriority priority) {
        return new AgentGoal(name, description, priority, Visibility.PUBLIC, List.of());
    }

    @Test
    void goalsSection_renders_sorted_by_priority_then_name() {
        var goals = List.of(
                goal("z-secondary", "Secondary goal Z", GoalPriority.SECONDARY),
                goal("a-primary", "Primary goal A", GoalPriority.PRIMARY),
                goal("b-primary", "Primary goal B", GoalPriority.PRIMARY));
        var section = CognitiveObservationSections.goalsSection(goals);
        assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
        var items = ((ObservationSection.ItemList) section).items();
        assertThat(items).hasSize(3);
        assertThat(items.get(0)).contains("PRIMARY").contains("Primary goal A");
        assertThat(items.get(1)).contains("PRIMARY").contains("Primary goal B");
        assertThat(items.get(2)).contains("SECONDARY").contains("Secondary goal Z");
    }

    @Test
    void goalsSection_empty_goals_shows_no_specific_goals() {
        var section = CognitiveObservationSections.goalsSection(List.of());
        assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
        assertThat(((ObservationSection.ItemList) section).emptyMessage()).isEqualTo("No specific goals.");
    }

    @Test
    void recentActivitySection_renders_drain_text() {
        var result = new ObservationResult("Penelope entered the room.", List.of(), 1, 5000L, null);
        var drain = new PartitionedDrain<String>(result, Map.of());
        var section = CognitiveObservationSections.recentActivitySection(drain);
        assertThat(section).isInstanceOf(ObservationSection.TextBlock.class);
        assertThat(((ObservationSection.TextBlock) section).content()).isEqualTo("Penelope entered the room.");
    }

    @Test
    void recentActivitySection_empty_drain_shows_quiet_room() {
        var drain = new PartitionedDrain<String>(ObservationResult.empty(0), Map.of());
        var section = CognitiveObservationSections.recentActivitySection(drain);
        assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
        assertThat(((ObservationSection.ItemList) section).emptyMessage()).isEqualTo("The room is quiet.");
    }

    @Test
    void pastExperienceSection_renders_memory_texts() {
        var memories = List.of(
                memory("Saw Dastardly near the kitchen"),
                memory("Heard a suspicious noise"));
        var section = CognitiveObservationSections.pastExperienceSection(memories);
        assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
        var items = ((ObservationSection.ItemList) section).items();
        assertThat(items).containsExactly("Saw Dastardly near the kitchen", "Heard a suspicious noise");
    }

    @Test
    void pastExperienceSection_filters_blank_texts() {
        var memories = List.of(memory("Valid text"), memory(""), memory("  "));
        var section = CognitiveObservationSections.pastExperienceSection(memories);
        var items = ((ObservationSection.ItemList) section).items();
        assertThat(items).containsExactly("Valid text");
    }

    @Test
    void insightsSection_renders_reflection_texts() {
        var reflections = List.of(memory("Dastardly is planning something"));
        var section = CognitiveObservationSections.insightsSection(reflections);
        assertThat(((ObservationSection.ItemList) section).header()).isEqualTo("Insights");
        assertThat(((ObservationSection.ItemList) section).items()).containsExactly("Dastardly is planning something");
    }

    @Test
    void relationshipNotesSection_renders_with_recall_prefix() {
        var memories = List.of(memory("Helped me escape the trap"));
        var section = CognitiveObservationSections.relationshipNotesSection("Penelope", memories);
        assertThat(((ObservationSection.ItemList) section).header()).isEqualTo("About Penelope");
        assertThat(((ObservationSection.ItemList) section).items()).containsExactly("You recall: Helped me escape the trap");
    }
}
