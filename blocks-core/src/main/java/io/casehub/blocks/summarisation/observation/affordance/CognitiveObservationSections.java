package io.casehub.blocks.summarisation.observation.affordance;

import io.casehub.blocks.agentic.belief.Belief;
import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.emergence.NormStrength;
import io.casehub.blocks.agentic.social.emergence.SocialNorm;
import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.blocks.summarisation.observation.PartitionedDrain;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.neocortex.memory.Memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class CognitiveObservationSections {

    private CognitiveObservationSections() {}

    public static ObservationSection goalsSection(List<AgentGoal> goals) {
        var items = new ArrayList<String>();
        goals.stream()
             .sorted(Comparator.comparing(AgentGoal::priority)
                                .thenComparing(AgentGoal::name))
             .map(g -> "[" + g.priority().name() + "] " + g.description())
             .forEach(items::add);
        if (items.isEmpty()) {
            return ObservationSection.items("Your Goals", "No specific goals.", List.of());
        }
        return ObservationSection.items("Your Goals", null, items);
    }

    public static ObservationSection recentActivitySection(PartitionedDrain<String> drain) {
        String text = drain.currentPartition().renderedText();
        if (text == null || text.isBlank()) {
            return ObservationSection.items("Recent Activity", "The room is quiet.", List.of());
        }
        return ObservationSection.text("Recent Activity", text.strip());
    }

    public static ObservationSection pastExperienceSection(List<Memory> memories) {
        var items = memories.stream()
                            .map(Memory::text)
                            .filter(t -> t != null && !t.isBlank())
                            .toList();
        return ObservationSection.items("Past Experience", null, items);
    }

    public static ObservationSection insightsSection(List<Memory> reflections) {
        var items = reflections.stream()
                               .map(Memory::text)
                               .filter(t -> t != null && !t.isBlank())
                               .toList();
        return ObservationSection.items("Insights", null, items);
    }

    public static ObservationSection relationshipNotesSection(String characterName, List<Memory> memories) {
        var items = memories.stream()
                            .map(m -> "You recall: " + m.text())
                            .toList();
        return ObservationSection.items("About " + characterName, null, items);
    }

    public static ObservationSection motivationalStateSection(DriveProfile profile) {
        var items = new ArrayList<String>();
        for (var axis : DriveAxis.values()) {
            var intensity = profile.drives().get(axis);
            if (intensity != null && intensity.intensity() >= 0.05) {
                String name = axis.name().charAt(0) + axis.name().substring(1).toLowerCase();
                items.add(String.format("%s: %.1f — %s", name, intensity.intensity(), intensity.trigger()));
            }
        }
        if (items.isEmpty()) {
            return ObservationSection.items("Motivational State", "No active drives.", List.of());
        }
        return ObservationSection.items("Motivational State", null, items);
    }

    public static ObservationSection narrativeSection(io.casehub.blocks.agentic.social.narrative.NarrativeState state) {
        var items    = new ArrayList<String>();
        var dominant = state.dominantTheme();
        if (dominant != null) {
            items.add("Core identity: " + dominant.label()
                      + " (salience: " + String.format("%.1f", dominant.salience()) + ")");
        }
        for (var episode : state.episodes()) {
            if (episode.emotionalValence() > 0.3 || episode.emotionalValence() < -0.3) {
                items.add("Memory: " + episode.description());
            }
        }
        for (var theme : state.themes()) {
            if (!theme.equals(dominant) && theme.salience() >= 0.3) {
                items.add("Theme: " + theme.label());
            }
        }
        if (items.isEmpty()) {
            return ObservationSection.items("Self-Narrative", "No established identity yet.", List.of());
        }
        return ObservationSection.items("Self-Narrative", null, items);
    }

    public static ObservationSection beliefsSection(List<? extends Belief<?>> beliefs, Set<String> revisedKeys) {
        var items = new ArrayList<String>();
        beliefs.stream()
               .sorted(Comparator.comparingInt((Belief<?> b) -> b.entrenchment()).reversed()
                                 .thenComparing(Belief::key))
               .forEach(b -> {
                   String prefix = revisedKeys.contains(b.key()) ? "[REVISED] " : "";
                   items.add(prefix + b.key() + ": " + b.value());
               });
        if (items.isEmpty()) {
            return ObservationSection.items("Your Beliefs", "No established beliefs.", List.of());
        }
        return ObservationSection.items("Your Beliefs", null, items);
    }

    public static ObservationSection principlesSection(List<Principle> principles) {
        var items = principles.stream()
                              .map(p -> p.category() != null
                                        ? "[" + p.category() + "] " + p.text()
                                        : p.text())
                              .toList();
        if (items.isEmpty()) {
            return ObservationSection.items("Your Principles", "No guiding principles.", List.of());
        }
        return ObservationSection.items("Your Principles", null, items);
    }

    public static ObservationSection trustSection(List<TrustSummary> summaries) {
        var items = new ArrayList<String>();
        summaries.stream()
                 .sorted(Comparator.comparingInt((TrustSummary s) -> s.level().ordinal())
                                   .thenComparing(TrustSummary::subjectName))
                 .forEach(s -> {
                     String entry = s.subjectName() + ": " + s.level().name();
                     if (s.reason() != null) {
                         entry += " — " + s.reason();
                     }
                     items.add(entry);
                 });
        if (items.isEmpty()) {
            return ObservationSection.items("Your Trust", "No trust assessments.", List.of());
        }
        return ObservationSection.items("Your Trust", null, items);
    }

    private static int normStrengthOrder(NormStrength strength) {
        return switch (strength) {
            case ESTABLISHED -> 0;
            case EMERGING -> 1;
            case DECLINING -> 2;
        };
    }

    public static ObservationSection normsSection(List<SocialNorm> norms) {
        var items = norms.stream()
                         .sorted(Comparator.comparingInt((SocialNorm n) -> normStrengthOrder(n.strength()))
                                           .thenComparing(SocialNorm::description))
                         .map(n -> "[" + n.strength().name() + "] " + n.description())
                         .toList();
        if (items.isEmpty()) {
            return ObservationSection.items("Your Active Norms", "No active norms.", List.of());
        }
        return ObservationSection.items("Your Active Norms", null, items);
    }

}
