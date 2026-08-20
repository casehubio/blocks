package io.casehub.blocks.agentic.social;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.BehavioralSignal;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.SignalValence;

import java.util.Comparator;
import java.util.List;

public class BehavioralSignalPressureSource implements TraitPressureSource<BehavioralSignal> {

    @Override
    public Class<BehavioralSignal> eventType() {
        return BehavioralSignal.class;
    }

    @Override
    public List<TraitActivation> translate(final BehavioralSignal event,
                                           final AgentDescriptor descriptor) {
        var profile = descriptor.disposition().dispositionProfile();
        if (profile == null || profile.isEmpty()) {
            return List.of();
        }
        var sorted = profile.stream()
                .sorted(Comparator.comparingDouble(DispositionValue::weight).reversed())
                .toList();

        return switch (event) {
            case SUCCESS, COMPLIANT -> List.of(
                    new TraitActivation(sorted.get(0).term(), SignalValence.POSITIVE));
            case DECLINE, VIOLATED -> {
                var target = sorted.size() > 1 ? sorted.get(1) : sorted.get(0);
                yield List.of(new TraitActivation(target.term(), SignalValence.NEGATIVE));
            }
        };
    }
}
