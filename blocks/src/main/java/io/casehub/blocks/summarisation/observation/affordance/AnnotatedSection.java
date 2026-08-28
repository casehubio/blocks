package io.casehub.blocks.summarisation.observation.affordance;

import java.util.Map;
import java.util.Set;

public record AnnotatedSection(
        ObservationSection section,
        Set<String> requiredTags,
        Map<ResolutionTier, ObservationSection> resolutions,
        String interpretiveFrame
) implements ObservationSection {

    public AnnotatedSection {
        requiredTags = Set.copyOf(requiredTags);
        resolutions = Map.copyOf(resolutions);
    }

    @Override
    public String header() {
        return section.header();
    }

    public static AnnotatedSection requiring(ObservationSection section,
                                              Set<String> tags) {
        return new AnnotatedSection(section, tags, Map.of(), null);
    }

    public static AnnotatedSection withResolution(ObservationSection fullSection,
                                                   Set<String> tags,
                                                   ResolutionTier fallbackTier,
                                                   ObservationSection fallbackSection) {
        return new AnnotatedSection(fullSection, tags,
                Map.of(fallbackTier, fallbackSection), null);
    }
}
