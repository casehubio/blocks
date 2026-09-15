package io.casehub.blocks.summarisation.observation.affordance;

import java.util.List;

@FunctionalInterface
public interface WorldObservationProvider {

    List<ObservationSection> worldSections();
}
