package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.CognitiveAttentionRequired;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@ApplicationScoped
public class CognitiveAttentionMediator {

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<AttentionBriefing>>
        attentionQueues = new ConcurrentHashMap<>();

    void onAttentionRequired(@Observes CognitiveAttentionRequired event) {
        attentionQueues
            .computeIfAbsent(event.briefing().principalId(),
                k -> new ConcurrentLinkedQueue<>())
            .add(event.briefing());
    }

    public Optional<AttentionBriefing> drainAttention(String principalId) {
        var queue = attentionQueues.get(principalId);
        if (queue == null || queue.isEmpty()) return Optional.empty();
        List<AttentionBriefing> drained = new ArrayList<>();
        AttentionBriefing b;
        while ((b = queue.poll()) != null) drained.add(b);
        if (drained.isEmpty()) return Optional.empty();
        if (drained.size() == 1) return Optional.of(drained.getFirst());
        return Optional.of(merge(drained));
    }

    private static AttentionBriefing merge(List<AttentionBriefing> briefings) {
        var last = briefings.getLast();
        var deduped = new LinkedHashMap<String, AttentionSignal>();
        for (var briefing : briefings) {
            for (var signal : briefing.signals()) {
                var key = signal.sourceNodeId() + ":" + signal.category().name();
                deduped.merge(key, signal, (existing, incoming) ->
                    incoming.significance() >= existing.significance() ? incoming : existing);
            }
        }
        var signals = new ArrayList<>(deduped.values());
        signals.sort(Comparator.comparingDouble(AttentionSignal::significance).reversed());
        return new AttentionBriefing(
            last.principalId(), last.tenantId(), signals, last.urgencyP75(), last.generatedAt());
    }
}
