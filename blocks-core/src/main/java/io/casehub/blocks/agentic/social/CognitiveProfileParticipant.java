package io.casehub.blocks.agentic.social;

import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognitive.index.CognitiveProfileQuery;
import io.casehub.neocortex.cognitive.index.EntityKnowledge;
import io.casehub.neocortex.cognitive.index.TemporalSource;
import io.casehub.platform.api.identity.PrincipalId;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CognitiveProfileParticipant implements CognitionTickParticipant {

    private final CognitiveProfile profile;
    private final @Nullable CognitiveAttentionMediator attentionMediator;
    private final @Nullable TemporalFocusOrchestrator temporalFocus;
    private final CognitionConfig config;

    private List<EntityKnowledge> lastEntityKnowledge = List.of();
    private Map<String, Map<PrincipalId, EntityKnowledge>> lastComparisons = Map.of();
    private Map<String, io.casehub.neocortex.cognitive.index.PerspectivalComparison> lastSocialComparisons = Map.of();


    public CognitiveProfileParticipant(CognitiveProfile profile,
                                       @Nullable CognitiveAttentionMediator attentionMediator,
                                       @Nullable TemporalFocusOrchestrator temporalFocus,
                                       CognitionConfig config) {
        this.profile = Objects.requireNonNull(profile);
        this.attentionMediator = attentionMediator;
        this.temporalFocus = temporalFocus;
        this.config = config;
    }

    @Override
    public void tick(CognitionTickContext context) {
        if (!config.entityKnowledgeEnabled()) {
            lastEntityKnowledge = List.of();
            lastComparisons = Map.of();
            lastSocialComparisons = Map.of();
            return;
        }

        var agentPrincipal = PrincipalId.agent(context.agentId());
        var seeds = collectSeeds(context);
        var resolved = new ArrayList<EntityKnowledge>();
        var comparisons = new LinkedHashMap<String, Map<PrincipalId, EntityKnowledge>>();
        var socialComparisons = new LinkedHashMap<String, io.casehub.neocortex.cognitive.index.PerspectivalComparison>();

        for (var query : seeds) {
            var withPerspective = query.withAsSeenBy(agentPrincipal);
            profile.resolve(withPerspective).ifPresent(ek -> {
                resolved.add(ek);
                if (config.perspectiveComparisonEnabled()) {
                    var subjects = context.resolver().relevantSubjects(
                            context.agentId(), context.tenantId());
                    if (!subjects.isEmpty()) {
                        var agents = new LinkedHashSet<PrincipalId>();
                        for (var s : subjects) agents.add(PrincipalId.agent(s));
                        var cmp = profile.compare(withPerspective, agents);
                        if (!cmp.isEmpty()) {
                            comparisons.put(ek.node().id(), cmp);
                            socialComparisons.put(ek.node().id(),
                                    io.casehub.neocortex.cognitive.index.SocialComparison.compare(cmp));
                        }
                    }
                }
            });
        }

        lastEntityKnowledge = List.copyOf(resolved);
        lastComparisons = Map.copyOf(comparisons);
        lastSocialComparisons = Map.copyOf(socialComparisons);
    }

    private List<CognitiveProfileQuery> collectSeeds(CognitionTickContext context) {
        var seen = new LinkedHashSet<String>();
        var seeds = new ArrayList<CognitiveProfileQuery>();
        var tenantId = context.tenantId();

        var subjects = context.resolver().relevantSubjects(context.agentId(), tenantId);
        for (var subjectId : subjects) {
            if (seen.add("name:" + subjectId)) {
                seeds.add(CognitiveProfileQuery.byName(subjectId, tenantId));
            }
        }

        if (attentionMediator != null) {
            attentionMediator.drainAttention(context.agentId()).ifPresent(briefing -> {
                for (var signal : briefing.signals()) {
                    var nodeId = signal.sourceNodeId();
                    if (nodeId != null && seen.add("id:" + nodeId)) {
                        seeds.add(CognitiveProfileQuery.byId(nodeId, tenantId));
                    }
                }
            });
        }

        if (temporalFocus != null) {
            for (var item : temporalFocus.lastFocus()) {
                if (item.entry().source() instanceof TemporalSource.FromMindMap fromMM) {
                    var nodeId = fromMM.node().id();
                    if (seen.add("id:" + nodeId)) {
                        seeds.add(CognitiveProfileQuery.byId(nodeId, tenantId));
                    }
                }
            }
        }

        return seeds;
    }

    public List<EntityKnowledge> lastEntityKnowledge() {
        return lastEntityKnowledge;
    }

    public Map<String, Map<PrincipalId, EntityKnowledge>> lastComparisons() {
        return lastComparisons;
    }

    public Map<String, io.casehub.neocortex.cognitive.index.PerspectivalComparison> lastSocialComparisons() {
        return lastSocialComparisons;
    }

}
