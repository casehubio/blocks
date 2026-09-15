package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.agentic.social.drive.DriveAxis;

import java.util.EnumMap;
import java.util.Map;

public final class NarrativeModulation {

    private NarrativeModulation() {}

    public static Map<DriveAxis, Double> compute(NarrativeState narrative) {
        var modulation = new EnumMap<DriveAxis, Double>(DriveAxis.class);
        for (var theme : narrative.themes()) {
            for (var entry : theme.axisModulationWeights().entrySet()) {
                modulation.merge(entry.getKey(),
                                 theme.salience() * entry.getValue(), Double::sum);
            }
        }
        modulation.replaceAll((axis, value) -> Math.clamp(value, -1.0, 1.0));
        return Map.copyOf(modulation);}
}
