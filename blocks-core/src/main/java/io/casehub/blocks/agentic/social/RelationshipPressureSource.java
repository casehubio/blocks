package io.casehub.blocks.agentic.social;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.SignalValence;
import io.casehub.neocortex.memory.relationship.QualitySignal;
import io.casehub.neocortex.memory.relationship.RelationshipEvent;

import java.util.Comparator;
import java.util.List;

public class RelationshipPressureSource implements TraitPressureSource<RelationshipEvent> {

    @Override
    public Class<RelationshipEvent> eventType() {
        return RelationshipEvent.class;
    }

    @Override
    public List<TraitActivation> translate(final RelationshipEvent event,
                                           final AgentDescriptor descriptor) {
        var profile = descriptor.disposition().dispositionProfile();
        if (profile == null || profile.isEmpty()) {
            return List.of();
        }
        if (event.qualitySignal() == QualitySignal.NEUTRAL) {
            return List.of();
        }
        var sorted = profile.stream()
                .sorted(Comparator.comparingDouble(DispositionValue::weight).reversed())
                .toList();

        if (event.qualitySignal() == QualitySignal.POSITIVE) {
            return List.of(new TraitActivation(sorted.get(0).term(), SignalValence.POSITIVE));
        }
        var target = sorted.size() > 1 ? sorted.get(1) : sorted.get(0);
        return List.of(new TraitActivation(target.term(), SignalValence.NEGATIVE));
    }
}
