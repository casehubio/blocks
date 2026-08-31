package io.casehub.blocks.summarisation.observation.affordance;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.blocks.summarisation.observation.ObservationResult;
import io.casehub.blocks.summarisation.observation.PartitionedDrain;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.GoalPriority;
import io.casehub.eidos.api.Visibility;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveObservationSectionsTest {

    private static final MemoryDomain DOMAIN = new MemoryDomain("test");

    private static Memory memory(String text) {
        return new Memory("m-1", "agent-1", DOMAIN, "t-1", "c-1",
                          text, Map.of(), Instant.now(),
                          io.casehub.neocortex.cognitive.Confidence.unknown(0.5),
                          null, null, null);
    }

    private static AgentGoal goal(String name, String description, GoalPriority priority) {
        return new AgentGoal(name, description, priority, Visibility.PUBLIC, List.of(), null);
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

    // --- DriveProfile observation section tests ---

    private static DriveProfile profile(Map<DriveAxis, DriveIntensity> drives) {
        double composite = drives.values().stream()
                .mapToDouble(DriveIntensity::intensity).average().orElse(0.0);
        DriveAxis dominant = drives.entrySet().stream()
                .max(Map.Entry.comparingByValue(
                        Comparator.comparingDouble(DriveIntensity::intensity)))
                .map(Map.Entry::getKey).orElse(DriveAxis.CURIOSITY);
        return new DriveProfile("agent-1", "tenant-1", drives,
                composite, dominant, Instant.now());
    }

    @Test
    void motivationalStateSection_renders_non_zero_axes() {
        var drives = Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.6, "5 low-retention memories across 3 groups"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.2, "1 declining of 3 dimensions"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.8, "2 of 3 relationships neglected"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.3, "2 high-confidence intentions across 1 subject"));
        var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
        assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
        var items = ((ObservationSection.ItemList) section).items();
        assertThat(items).hasSize(4);
        assertThat(items.get(0)).isEqualTo("Curiosity: 0.6 — 5 low-retention memories across 3 groups");
        assertThat(items.get(1)).isEqualTo("Competence: 0.2 — 1 declining of 3 dimensions");
        assertThat(items.get(2)).isEqualTo("Affiliation: 0.8 — 2 of 3 relationships neglected");
        assertThat(items.get(3)).isEqualTo("Autonomy: 0.3 — 2 high-confidence intentions across 1 subject");
        assertThat(((ObservationSection.ItemList) section).header()).isEqualTo("Motivational State");
    }

    @Test
    void motivationalStateSection_filters_low_intensity_axes() {
        var drives = Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.6, "gaps"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "none"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.03, "near zero"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "none"));
        var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
        var items = ((ObservationSection.ItemList) section).items();
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).startsWith("Curiosity: 0.6");
    }

    @Test
    void motivationalStateSection_all_below_threshold_shows_no_active_drives() {
        var drives = Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.0, "none"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "none"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.02, "trace"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "none"));
        var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
        assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
        var il = (ObservationSection.ItemList) section;
        assertThat(il.items()).isEmpty();
        assertThat(il.emptyMessage()).isEqualTo("No active drives.");
    }

    @Test
    void motivationalStateSection_single_axis_non_zero() {
        var drives = Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.0, "none"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "none"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.0, "none"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.9, "high pressure"));
        var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
        var items = ((ObservationSection.ItemList) section).items();
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).isEqualTo("Autonomy: 0.9 — high pressure");
    }

    @Test
    void motivationalStateSection_boundary_at_threshold() {
        var drives = Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.05, "just enough"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.04, "not enough"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.0, "none"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "none"));
        var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
        var items = ((ObservationSection.ItemList) section).items();
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).startsWith("Curiosity: 0.1");
    }

    @Test
    void motivationalStateSection_renders_via_affordance_renderer() {
        var drives = Map.of(
                DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.6, "gaps"),
                DriveAxis.COMPETENCE, new DriveIntensity(DriveAxis.COMPETENCE, 0.0, "none"),
                DriveAxis.AFFILIATION, new DriveIntensity(DriveAxis.AFFILIATION, 0.0, "none"),
                DriveAxis.AUTONOMY, new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "none"));
        var section = CognitiveObservationSections.motivationalStateSection(profile(drives));
        var rendered = new AffordanceRenderer().renderObservation(List.of(section));
        assertThat(rendered).contains("== Motivational State ==");
        assertThat(rendered).contains("- Curiosity: 0.6");
    }

    @Test
    void narrativeSection_rendersIdentity() {
        var now = java.time.Instant.now();
        var theme = new io.casehub.blocks.agentic.social.narrative.DerivedTheme(
                "t1", now, null, java.util.List.of(), "crisis-helper", 0.9,
                java.util.Map.of(), java.util.List.of());
        var episode = new io.casehub.blocks.agentic.social.narrative.IndividualEpisode(
                "e1", now, null, java.util.List.of(), "Helped team through crisis",
                0.8, java.util.List.of());
        var state = new io.casehub.blocks.agentic.social.narrative.NarrativeState(
                "a", "t", io.casehub.blocks.agentic.social.narrative.NarrativeScope.INDIVIDUAL,
                java.util.List.of(theme, episode), now, 5);

        var section = CognitiveObservationSections.narrativeSection(state);

        assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
        var items = ((ObservationSection.ItemList) section).items();
        assertThat(items).anyMatch(i -> i.contains("crisis-helper"));
        assertThat(items).anyMatch(i -> i.contains("Helped team through crisis"));
    }

    @Test
    void narrativeSection_emptyState() {
        var state = new io.casehub.blocks.agentic.social.narrative.NarrativeState(
                "a", "t", io.casehub.blocks.agentic.social.narrative.NarrativeScope.INDIVIDUAL,
                java.util.List.of(), java.time.Instant.now(), 0);

        var section = CognitiveObservationSections.narrativeSection(state);

        assertThat(section).isInstanceOf(ObservationSection.ItemList.class);
        var itemList = (ObservationSection.ItemList) section;
        assertThat(itemList.emptyMessage()).isEqualTo("No established identity yet.");
    }

    @Test
    void narrativeSection_filtersLowValenceEpisodes() {
        var now = java.time.Instant.now();
        var lowValence = new io.casehub.blocks.agentic.social.narrative.IndividualEpisode(
                "e1", now, null, java.util.List.of(), "Routine interaction",
                0.1, java.util.List.of());
        var highValence = new io.casehub.blocks.agentic.social.narrative.IndividualEpisode(
                "e2", now, null, java.util.List.of(), "Significant event",
                0.7, java.util.List.of());
        var state = new io.casehub.blocks.agentic.social.narrative.NarrativeState(
                "a", "t", io.casehub.blocks.agentic.social.narrative.NarrativeScope.INDIVIDUAL,
                java.util.List.of(lowValence, highValence), now, 5);

        var section = CognitiveObservationSections.narrativeSection(state);
        var items   = ((ObservationSection.ItemList) section).items();
        assertThat(items).noneMatch(i -> i.contains("Routine interaction"));
        assertThat(items).anyMatch(i -> i.contains("Significant event"));
    }
}
