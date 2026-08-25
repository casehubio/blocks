package io.casehub.blocks.agentic.social.emergence;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class SocialNormDetector {

    private final CbrCaseMemoryStore cbrStore;
    private final MemoryDomain domain;
    private final String caseType;
    private final NormDetectionConfig config;
    private final Clock clock;

    private final ConcurrentHashMap<String, DetectedNorms> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    @Inject
    public SocialNormDetector(CbrCaseMemoryStore cbrStore, NormDetectionConfig config) {
        this(cbrStore, config, Clock.systemUTC());
    }

    SocialNormDetector(CbrCaseMemoryStore cbrStore, NormDetectionConfig config, Clock clock) {
        this.cbrStore = cbrStore;
        this.domain = new MemoryDomain(config.memoryDomain());
        this.caseType = config.caseType();
        this.config = config;
        this.clock = clock;
    }

    public NormDetectionTick tick(String tenantId) {
        var lock = tickLocks.computeIfAbsent(tenantId, k -> new ReentrantLock());
        lock.lock();
        try {
            var observations = loadObservations(tenantId);
            var previous = cache.get(tenantId);
            var previousNormsByPattern = indexByPattern(previous);

            var grouped = groupByPattern(observations);
            var norms = new ArrayList<SocialNorm>();

            for (var entry : grouped.entrySet()) {
                var pattern = entry.getKey();
                var obs = entry.getValue();

                if (obs.size() < config.minObservationsForNorm()) continue;

                var agents = collectAgents(obs);
                if (agents.size() < config.minAgentsForNorm()) continue;

                long followed = obs.stream().filter(NormObservation::patternFollowed).count();
                double adherenceRate = (double) followed / obs.size();

                var firstObserved = obs.stream()
                        .map(NormObservation::observedAt)
                        .min(Instant::compareTo)
                        .orElse(Instant.now(clock));
                var lastObserved = obs.stream()
                        .map(NormObservation::observedAt)
                        .max(Instant::compareTo)
                        .orElse(Instant.now(clock));

                var strength = classifyStrength(adherenceRate, pattern, previousNormsByPattern);

                norms.add(new SocialNorm(
                        pattern, pattern, pattern,
                        adherenceRate, obs.size(), agents,
                        firstObserved, lastObserved, strength));
            }

            var now = Instant.now(clock);
            var detected = new DetectedNorms(norms, observations.size(), now);
            cache.put(tenantId, detected);

            if (previous == null) {
                if (norms.isEmpty()) {
                    return new NormDetectionTick.NoChange("no norms detected");
                }
                return new NormDetectionTick.Updated(
                        new DetectedNorms(List.of(), 0, now),
                        detected,
                        norms.stream().map(SocialNorm::normId).toList(),
                        List.of());
            }

            var previousIds = previous.norms().stream()
                    .map(SocialNorm::normId)
                    .toList();
            var currentIds = norms.stream()
                    .map(SocialNorm::normId)
                    .toList();

            var newNormIds = currentIds.stream()
                    .filter(id -> !previousIds.contains(id))
                    .toList();
            var declinedNormIds = norms.stream()
                    .filter(n -> n.strength() == NormStrength.DECLINING)
                    .map(SocialNorm::normId)
                    .filter(id -> previousNormsByPattern.containsKey(id)
                            && previousNormsByPattern.get(id).strength() != NormStrength.DECLINING)
                    .toList();

            if (newNormIds.isEmpty() && declinedNormIds.isEmpty()
                    && norms.size() == previous.norms().size()) {
                return new NormDetectionTick.NoChange("no norm changes");
            }

            return new NormDetectionTick.Updated(previous, detected, newNormIds, declinedNormIds);
        } finally {
            lock.unlock();
        }
    }

    public Optional<DetectedNorms> currentNorms(String tenantId) {
        return Optional.ofNullable(cache.get(tenantId));
    }

    private List<NormObservation> loadObservations(String tenantId) {
        var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                        Map.of(), 1000)
                .withMinSimilarity(0.0);
        var results = cbrStore.retrieveSimilar(query, CbrCase.class);
        var observations = new ArrayList<NormObservation>();
        for (var scored : results) {
            observations.add(NormObservationSchema.fromCase(scored, tenantId));
        }
        return observations;
    }

    private static Map<String, List<NormObservation>> groupByPattern(List<NormObservation> observations) {
        var grouped = new HashMap<String, List<NormObservation>>();
        for (var obs : observations) {
            grouped.computeIfAbsent(obs.behavioralPattern(), k -> new ArrayList<>()).add(obs);
        }
        return grouped;
    }

    private static Set<String> collectAgents(List<NormObservation> observations) {
        var agents = new HashSet<String>();
        for (var obs : observations) {
            agents.addAll(obs.involvedAgents());
        }
        return agents;
    }

    private NormStrength classifyStrength(double adherenceRate, String pattern,
                                          Map<String, SocialNorm> previousNorms) {
        var previous = previousNorms.get(pattern);
        if (previous != null && previous.strength() == NormStrength.ESTABLISHED
                && adherenceRate < config.decliningThreshold()) {
            return NormStrength.DECLINING;
        }
        if (adherenceRate >= config.establishedThreshold()) {
            return NormStrength.ESTABLISHED;
        }
        if (previous != null && previous.strength() == NormStrength.ESTABLISHED
                && adherenceRate < config.establishedThreshold()) {
            return NormStrength.DECLINING;
        }
        return NormStrength.EMERGING;
    }

    private static Map<String, SocialNorm> indexByPattern(DetectedNorms norms) {
        if (norms == null) return Map.of();
        var map = new HashMap<String, SocialNorm>();
        for (var norm : norms.norms()) {
            map.put(norm.behavioralPattern(), norm);
        }
        return map;
    }
}
