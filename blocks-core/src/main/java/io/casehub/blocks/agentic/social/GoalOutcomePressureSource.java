package io.casehub.blocks.agentic.social;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.GoalOutcomeCounts;
import io.casehub.eidos.api.SignalValence;

import java.util.Comparator;
import java.util.List;

public class GoalOutcomePressureSource implements TraitPressureSource<GoalOutcomeCounts> {

    static final double SUCCESS_THRESHOLD = 0.7;
    static final double FAILURE_FLOOR = 0.3;

    @Override
    public Class<GoalOutcomeCounts> eventType() {
        return GoalOutcomeCounts.class;
    }

    @Override
    public List<TraitActivation> translate(final GoalOutcomeCounts event,
                                           final AgentDescriptor descriptor) {
        var profile = descriptor.disposition().dispositionProfile();
        if (profile == null || profile.isEmpty()) {
            return List.of();
        }
        int total = event.successCount() + event.failureCount();
        if (total == 0) {
            return List.of();
        }
        double rate = event.successRate();
        var sorted = profile.stream()
                            .sorted(Comparator.comparingDouble(DispositionValue::weight).reversed())
                            .toList();

        if (rate >= SUCCESS_THRESHOLD) {
            return List.of(new TraitActivation(sorted.get(0).term(), SignalValence.POSITIVE));
        }
        if (rate <= FAILURE_FLOOR) {
            var target = sorted.size() > 1 ? sorted.get(1) : sorted.get(0);
            return List.of(new TraitActivation(target.term(), SignalValence.NEGATIVE));
        }
        return List.of();
    }
}
