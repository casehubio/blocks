package io.casehub.blocks.summarisation.observation.affordance;

import java.util.List;
import java.util.Set;

public class ObservationPipeline {

    private final List<ObservationFilter> stages;

    public ObservationPipeline(ObservationFilter... stages) {
        this.stages = List.of(stages);
    }

    public List<ObservationSection> apply(List<ObservationSection> sections,
                                           Set<String> observerTags) {
        var current = sections;
        for (var stage : stages) {
            current = stage.filter(current, observerTags);
        }
        return unwrap(current);
    }

    private List<ObservationSection> unwrap(List<ObservationSection> sections) {
        return sections.stream()
                .map(s -> s instanceof AnnotatedSection a ? a.section() : s)
                .toList();
    }
}
