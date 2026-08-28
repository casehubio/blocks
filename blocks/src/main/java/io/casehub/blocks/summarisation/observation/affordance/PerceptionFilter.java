package io.casehub.blocks.summarisation.observation.affordance;

import java.util.List;
import java.util.Set;

public class PerceptionFilter implements ObservationFilter {

    @Override
    public List<ObservationSection> filter(List<ObservationSection> sections,
                                            Set<String> observerTags) {
        return sections.stream()
                .map(s -> resolve(s, observerTags))
                .filter(s -> s != null)
                .toList();
    }

    private ObservationSection resolve(ObservationSection section, Set<String> observerTags) {
        if (!(section instanceof AnnotatedSection a)) {
            return section;
        }
        if (a.requiredTags().isEmpty() || observerTags.containsAll(a.requiredTags())) {
            return section;
        }
        if (!a.resolutions().isEmpty()) {
            for (var tier : ResolutionTier.values()) {
                if (tier != ResolutionTier.FULL && a.resolutions().containsKey(tier)) {
                    return a.resolutions().get(tier);
                }
            }
        }
        return null;
    }
}
