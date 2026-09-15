package io.casehub.blocks.summarisation.observation;

public record ObservationTier(String name, int ordinal) {
    public static final ObservationTier VERBATIM = new ObservationTier("verbatim", 0);
    public static final ObservationTier GROUPED = new ObservationTier("grouped", 1);
    public static final ObservationTier SUMMARISED = new ObservationTier("summarised", 2);
}
