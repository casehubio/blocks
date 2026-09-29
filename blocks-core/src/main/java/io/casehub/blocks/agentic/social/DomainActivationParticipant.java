package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.cognitive.index.CorrelationStrength;
import io.casehub.neocortex.cognitive.index.DomainActivation;
import io.casehub.neocortex.cognitive.index.DomainActivationQuery;
import io.casehub.neocortex.cognitive.index.DomainCorrelation;
import io.casehub.neocortex.cognitive.index.DomainPair;
import io.casehub.neocortex.cognitive.index.DomainSignal;
import io.casehub.neocortex.cognitive.index.EventImpact;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.mood.MoodEvents;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.platform.api.identity.PrincipalId;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DomainActivationParticipant implements CognitionTickParticipant {

    private static final int MAX_PAIRS = 5;
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);

    private final DomainActivation domainActivation;
    private final MindMapStore mindMapStore;
    private final @Nullable ConsolidationMediator consolidationMediator;
    private final CognitionConfig config;

    private final Map<String, Instant> lastComputedAt = new ConcurrentHashMap<>();
    private volatile @Nullable DomainActivationSnapshot lastSnapshot;

    public DomainActivationParticipant(DomainActivation domainActivation,
                                       MindMapStore mindMapStore,
                                       @Nullable ConsolidationMediator consolidationMediator,
                                       CognitionConfig config) {
        this.domainActivation = Objects.requireNonNull(domainActivation);
        this.mindMapStore = Objects.requireNonNull(mindMapStore);
        this.consolidationMediator = consolidationMediator;
        this.config = config;
    }

    @Override
    public void tick(CognitionTickContext context) {
        if (!config.domainActivationEnabled()) {
            lastSnapshot = null;
            return;
        }

        if (consolidationMediator != null) {
            var ts = consolidationMediator.lastConsolidationTimestamp(context.tenantId());
            if (ts == null || !ts.isAfter(lastComputedAt.getOrDefault(context.agentId(), Instant.EPOCH))) {
                return;
            }
        }

        var cognitiveSubgraphs = mindMapStore.listSubgraphs(context.tenantId()).stream()
                .filter(sg -> SubgraphTypes.COGNITIVE.equals(sg.type()))
                .filter(sg -> !mindMapStore.nodesIn(sg.id(), context.tenantId()).isEmpty())
                .toList();

        if (cognitiveSubgraphs.size() < 2) {
            lastSnapshot = null;
            return;
        }

        lastSnapshot = computeCascade(context, cognitiveSubgraphs);
        lastComputedAt.put(context.agentId(), Instant.now());
    }

    public @Nullable DomainActivationSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    private DomainActivationSnapshot computeCascade(CognitionTickContext context,
                                                     List<MindMapSubgraph> subgraphs) {
        var principal = PrincipalId.agent(context.agentId());
        var tenantId = context.tenantId();
        var from = Instant.now().minus(DEFAULT_WINDOW);
        var to = Instant.now();

        var pairCorrelations = new LinkedHashMap<DomainPair, DomainCorrelation>();
        var domainSignals = new LinkedHashMap<String, DomainSignal>();
        var subgraphNames = subgraphs.stream()
                .collect(Collectors.toMap(MindMapSubgraph::id, MindMapSubgraph::name,
                        (a, b) -> a, LinkedHashMap::new));

        for (int i = 0; i < subgraphs.size(); i++) {
            for (int j = i + 1; j < subgraphs.size(); j++) {
                var query = DomainActivationQuery.between(principal, tenantId,
                        subgraphs.get(i).id(), subgraphs.get(j).id())
                        .withFrom(from).withTo(to);
                domainActivation.correlate(query).ifPresent(result -> {
                    pairCorrelations.putAll(result.correlations());
                    domainSignals.putAll(result.domains());
                });
            }
        }

        var strongPairs = pairCorrelations.entrySet().stream()
                .filter(e -> e.getValue().strength().ordinal() <= CorrelationStrength.MODERATE.ordinal())
                .sorted(Comparator.comparingDouble(e -> -e.getValue().dtwSimilarity()))
                .limit(MAX_PAIRS)
                .map(Map.Entry::getKey)
                .toList();

        var contextDomains = Set.of(MoodEvents.DOMAIN, ExperienceEvents.DOMAIN);
        var moodCorrelations = new LinkedHashMap<DomainPair, Map<String, DomainCorrelation>>();
        var experienceImpacts = new LinkedHashMap<DomainPair, Map<String, EventImpact>>();

        for (var pair : strongPairs) {
            var detailQuery = DomainActivationQuery.between(principal, tenantId,
                    pair.subgraphIdA(), pair.subgraphIdB())
                    .withContextDomains(contextDomains)
                    .withEventWindow(Duration.ofHours(2))
                    .withFrom(from).withTo(to);
            domainActivation.correlate(detailQuery).ifPresent(detail -> {
                var moodCorrs = detail.contextCorrelations().get(MoodEvents.DOMAIN);
                if (moodCorrs != null && !moodCorrs.isEmpty()) {
                    moodCorrelations.put(pair, moodCorrs);
                }
                var expImpacts = detail.eventImpacts().get(ExperienceEvents.DOMAIN);
                if (expImpacts != null && !expImpacts.isEmpty()) {
                    experienceImpacts.put(pair, expImpacts);
                }
            });
        }

        return new DomainActivationSnapshot(
                pairCorrelations, moodCorrelations, experienceImpacts,
                domainSignals, subgraphNames, Instant.now());
    }
}
