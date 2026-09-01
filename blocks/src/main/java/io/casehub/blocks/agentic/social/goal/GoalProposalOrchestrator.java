package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.GoalOutcomeCounts;
import io.casehub.eidos.api.GoalSignalStore;
import org.jspecify.annotations.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class GoalProposalOrchestrator {

    private final DriveOrchestrator driveOrchestrator;
    private final List<DriveGoalMapper> mappers;
    private final @Nullable DriveGoalFormationStrategy formationStrategy;
    private final Instance<GoalSignalStore> goalSignalStore;
    private final GoalProposalConfig config;
    private final Clock clock;
    private final @Nullable NarrativeOrchestrator narrativeOrchestrator;
    private final @Nullable GoalEscalationPolicy escalationPolicy;
    private final @Nullable CrossAxisGoalEnricher crossAxisEnricher;
    private final GoalEscalationConfig escalationConfig;

    private final ConcurrentHashMap<String, GoalProposalState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    @Inject
    public GoalProposalOrchestrator(
            DriveOrchestrator driveOrchestrator,
            Instance<DriveGoalMapper> mapperInstances,
            Instance<DriveGoalFormationStrategy> strategyInstance,
            Instance<GoalSignalStore> goalSignalStore,
            Instance<NarrativeOrchestrator> narrativeInstance,
            Instance<GoalEscalationPolicy> escalationInstance,
            Instance<CrossAxisGoalEnricher> enricherInstance,
            GoalProposalConfig config,
            GoalEscalationConfig escalationConfig) {
        this(driveOrchestrator, mapperInstances.stream().toList(),
             strategyInstance.isResolvable() ? strategyInstance.get() : null,
             goalSignalStore,
             narrativeInstance.isResolvable() ? narrativeInstance.get() : null,
             escalationInstance.isResolvable() ? escalationInstance.get() : null,
             enricherInstance.isResolvable() ? enricherInstance.get() : null,
             config, escalationConfig, Clock.systemUTC());
    }

    GoalProposalOrchestrator(
            DriveOrchestrator driveOrchestrator,
            List<DriveGoalMapper> mappers,
            Instance<GoalSignalStore> goalSignalStore,
            GoalProposalConfig config,
            Clock clock) {
        this(driveOrchestrator, mappers, null, goalSignalStore,
             null, null, null, config, GoalEscalationConfig.defaults(), clock);
    }

    GoalProposalOrchestrator(
            DriveOrchestrator driveOrchestrator,
            List<DriveGoalMapper> mappers,
            DriveGoalFormationStrategy formationStrategy,
            Instance<GoalSignalStore> goalSignalStore,
            GoalProposalConfig config,
            Clock clock) {
        this(driveOrchestrator, mappers, formationStrategy, goalSignalStore,
             null, null, null, config, GoalEscalationConfig.defaults(), clock);
    }

    GoalProposalOrchestrator(
            DriveOrchestrator driveOrchestrator,
            List<DriveGoalMapper> mappers,
            @Nullable DriveGoalFormationStrategy formationStrategy,
            Instance<GoalSignalStore> goalSignalStore,
            @Nullable NarrativeOrchestrator narrativeOrchestrator,
            @Nullable GoalEscalationPolicy escalationPolicy,
            @Nullable CrossAxisGoalEnricher crossAxisEnricher,
            GoalProposalConfig config,
            GoalEscalationConfig escalationConfig,
            Clock clock) {
        this.driveOrchestrator      = driveOrchestrator;
        this.mappers                = List.copyOf(mappers);
        this.formationStrategy      = formationStrategy;
        this.goalSignalStore        = goalSignalStore;
        this.narrativeOrchestrator  = narrativeOrchestrator;
        this.escalationPolicy       = escalationPolicy;
        this.crossAxisEnricher      = crossAxisEnricher;
        this.config                 = config;
        this.escalationConfig       = escalationConfig;
        this.clock                  = clock;
    }

    public GoalProposalTick tick(String agentId, String tenantId, AgentDescriptor descriptor) {
        String key = agentId + "|" + tenantId;
        ReentrantLock lock = tickLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            return doTick(agentId, tenantId, descriptor, key);
        } finally {
            lock.unlock();
        }
    }

    public Optional<List<DriveGoalProposal>> currentProposals(String agentId, String tenantId) {
        GoalProposalState state = states.get(agentId + "|" + tenantId);
        if (state == null || state.cachedProposals.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(state.cachedProposals));
    }

    private GoalProposalTick doTick(String agentId, String tenantId,
                                     AgentDescriptor descriptor, String key) {
        Optional<DriveProfile> profileOpt = driveOrchestrator.currentDrives(agentId, tenantId);
        if (profileOpt.isEmpty()) {
            return new GoalProposalTick.NoChange("no drive profile");
        }

        GoalProposalState state = states.computeIfAbsent(key, k -> new GoalProposalState());

        if (isCooldownActive(state)) {
            return new GoalProposalTick.NoChange("cooldown active");
        }

        DriveProfile profile = profileOpt.get();

        List<String> abandonments = evaluateRelevance(descriptor, profile, state, agentId, tenantId);

        int existingDriveGoals = countDriveGoals(descriptor);
        int remainingCapacity = config.maxDriveGoals() - existingDriveGoals + abandonments.size();

        List<DriveGoalProposal> proposals = new ArrayList<>();
        if (remainingCapacity > 0) {
            proposals = evaluateMappers(agentId, tenantId, profile, state,
                    descriptor, remainingCapacity);

            // Phase 2: cross-axis composition
            NarrativeState narrative = narrativeOrchestrator != null
                    ? narrativeOrchestrator.currentNarrative(agentId, tenantId).orElse(null)
                    : null;
            if (narrative != null) {
                proposals.addAll(evaluateCrossAxis(agentId, tenantId, profile, narrative));
            }

            proposals.sort(Comparator.comparingDouble(DriveGoalProposal::driveIntensity).reversed()
                    .thenComparing(p -> p.axis().ordinal()));
            if (proposals.size() > remainingCapacity) {
                proposals = new ArrayList<>(proposals.subList(0, remainingCapacity));
            }
        }

        if (proposals.isEmpty() && abandonments.isEmpty()) {
            return new GoalProposalTick.NoChange("no proposals or abandonments");
        }

        state.lastProposalTimestamp = Instant.now(clock);
        state.cachedProposals = List.copyOf(proposals);
        state.cachedAbandonments = List.copyOf(abandonments);

        return new GoalProposalTick.Changes(proposals, abandonments, List.of(), List.of());
    }

    private boolean isCooldownActive(GoalProposalState state) {
        if (state.lastProposalTimestamp == null) {
            return false;
        }
        return Duration.between(state.lastProposalTimestamp, Instant.now(clock))
                .compareTo(config.cooldown()) < 0;
    }

    private int countDriveGoals(AgentDescriptor descriptor) {
        int count = 0;
        for (AgentGoal goal : descriptor.goals()) {
            if (goal.attributes() != null && "drive".equals(goal.attributes().get("source"))) {
                count++;
            }
        }
        return count;
    }

    private List<DriveGoalProposal> evaluateMappers(String agentId, String tenantId,
                                                    DriveProfile profile,
                                                    GoalProposalState state,
                                                    AgentDescriptor descriptor,
                                                    int remainingCapacity) {
        List<DriveGoalProposal> proposals = new ArrayList<>();
        for (Map.Entry<DriveAxis, DriveIntensity> entry : profile.drives().entrySet()) {
            DriveIntensity intensity = entry.getValue();
            if (intensity.intensity() < config.proposalThreshold()) {
                continue;
            }

            DriveGoalProposal proposal = null;

            if (formationStrategy != null) {
                try {
                    var context = new DriveGoalFormationContext(
                            agentId, tenantId, intensity.axis(), intensity.intensity(),
                            intensity.trigger(), descriptor.goals(), remainingCapacity);
                    proposal = formationStrategy.propose(context);
                } catch (Exception e) {
                    proposal = null;
                }
            }

            if (proposal == null) {
                for (DriveGoalMapper mapper : mappers) {
                    proposal = mapper.evaluate(agentId, tenantId, intensity);
                    if (proposal != null) {break;}
                }
            }

            if (proposal != null && !state.failureSuppressedGoalNames.contains(proposal.goalName())) {
                proposals.add(proposal);
            }
        }
        return proposals;
    }

    private List<String> evaluateRelevance(AgentDescriptor descriptor, DriveProfile profile,
                                           GoalProposalState state,
                                           String agentId, String tenantId) {
        List<String> abandonments = new ArrayList<>();
        Map<String, GoalOutcomeCounts> counts = loadOutcomeCounts(agentId, tenantId);

        for (AgentGoal goal : descriptor.goals()) {
            if (goal.attributes() == null || !"drive".equals(goal.attributes().get("source"))) {
                continue;
            }

            if (isFailureAbandoned(goal, counts, state)) {
                abandonments.add(goal.name());
                continue;
            }

            if (isDriveStale(goal, profile, state)) {
                abandonments.add(goal.name());
            }
        }

        return abandonments;
    }

    private boolean isFailureAbandoned(AgentGoal goal, Map<String, GoalOutcomeCounts> counts,
                                        GoalProposalState state) {
        GoalOutcomeCounts goalCounts = counts.get(goal.name());
        if (goalCounts != null && goalCounts.failureCount() >= config.failureAbandonmentThreshold()) {
            state.failureSuppressedGoalNames.add(goal.name());
            return true;
        }
        return false;
    }

    private boolean isDriveStale(AgentGoal goal, DriveProfile profile, GoalProposalState state) {
        String axisName = goal.attributes().get("driveAxis");
        if (axisName == null) {
            return false;
        }

        DriveAxis axis;
        try {
            axis = DriveAxis.valueOf(axisName);
        } catch (IllegalArgumentException e) {
            return false;
        }

        DriveIntensity intensity = profile.drives().get(axis);
        double currentIntensity = intensity != null ? intensity.intensity() : 0.0;

        if (currentIntensity < config.relevanceThreshold()) {
            Instant belowSince = state.driveGoalBelowThresholdSince
                    .computeIfAbsent(goal.name(), k -> Instant.now(clock));
            return Duration.between(belowSince, Instant.now(clock))
                    .compareTo(config.staleAfter()) >= 0;
        } else {
            state.driveGoalBelowThresholdSince.remove(goal.name());
            return false;
        }
    }

    private Map<String, GoalOutcomeCounts> loadOutcomeCounts(String agentId, String tenantId) {
        if (!goalSignalStore.isResolvable()) {
            return Map.of();
        }
        try {
            return goalSignalStore.get().outcomeCounts(agentId, tenantId);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<DriveGoalProposal> evaluateCrossAxis(String agentId, String tenantId,
                                                       DriveProfile profile,
                                                       NarrativeState narrative) {
        List<DriveGoalProposal> compounds = new ArrayList<>();
        for (var theme : narrative.themes()) {
            var positiveAxes = theme.axisModulationWeights().entrySet().stream()
                    .filter(e -> e.getValue() > escalationConfig.crossAxisMinWeight())
                    .sorted(Map.Entry.<DriveAxis, Double>comparingByValue().reversed())
                    .toList();

            if (positiveAxes.size() < escalationConfig.minCrossAxisCount()) continue;

            DriveAxis dominant = positiveAxes.get(0).getKey();
            DriveAxis secondary = positiveAxes.get(1).getKey();

            var dominantIntensity = profile.drives().get(dominant);
            if (dominantIntensity == null) continue;

            String goalName = "compound-" + dominant.name().toLowerCase()
                    + "-" + secondary.name().toLowerCase()
                    + "-" + theme.label().replaceAll("[^a-zA-Z0-9-]", "").toLowerCase();

            var weightsStr = positiveAxes.stream()
                    .map(e -> e.getKey().name() + ":" + String.format("%.2f", e.getValue()))
                    .collect(java.util.stream.Collectors.joining(","));

            var attrs = Map.of("crossAxisWeights", weightsStr);

            var proposal = new DriveGoalProposal(dominant, goalName,
                    "Compound goal: " + theme.label() + " across " + dominant + " and " + secondary,
                    "cross-axis theme '" + theme.label() + "' with weights: " + weightsStr,
                    dominantIntensity.intensity(), null, attrs);

            if (crossAxisEnricher != null) {
                var enriched = crossAxisEnricher.enrich(proposal, narrative, theme);
                if (enriched != null) proposal = enriched;
            }

            compounds.add(proposal);
        }
        return compounds;
    }

    static final class GoalProposalState {
        Instant lastProposalTimestamp;
        List<DriveGoalProposal> cachedProposals = List.of();
        List<String> cachedAbandonments = List.of();
        Map<String, Instant> driveGoalBelowThresholdSince = new HashMap<>();
        Set<String> failureSuppressedGoalNames = new HashSet<>();
    }
}
