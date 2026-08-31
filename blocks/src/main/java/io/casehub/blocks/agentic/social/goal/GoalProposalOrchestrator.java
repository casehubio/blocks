package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveProfile;
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

    private final ConcurrentHashMap<String, GoalProposalState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    @Inject
    public GoalProposalOrchestrator(
            DriveOrchestrator driveOrchestrator,
            Instance<DriveGoalMapper> mapperInstances,
            Instance<DriveGoalFormationStrategy> strategyInstance,
            Instance<GoalSignalStore> goalSignalStore,
            GoalProposalConfig config) {
        this(driveOrchestrator, mapperInstances.stream().toList(),
             strategyInstance.isResolvable() ? strategyInstance.get() : null,
             goalSignalStore, config, Clock.systemUTC());
    }

    GoalProposalOrchestrator(
            DriveOrchestrator driveOrchestrator,
            List<DriveGoalMapper> mappers,
            Instance<GoalSignalStore> goalSignalStore,
            GoalProposalConfig config,
            Clock clock) {
        this(driveOrchestrator, mappers, null, goalSignalStore, config, clock);
    }

    GoalProposalOrchestrator(
            DriveOrchestrator driveOrchestrator,
            List<DriveGoalMapper> mappers,
            @Nullable DriveGoalFormationStrategy formationStrategy,
            Instance<GoalSignalStore> goalSignalStore,
            GoalProposalConfig config,
            Clock clock) {
        this.driveOrchestrator = driveOrchestrator;
        this.mappers           = List.copyOf(mappers);
        this.formationStrategy = formationStrategy;
        this.goalSignalStore   = goalSignalStore;
        this.config            = config;
        this.clock             = clock;
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

        return new GoalProposalTick.Proposed(proposals, abandonments);
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

    static final class GoalProposalState {
        Instant lastProposalTimestamp;
        List<DriveGoalProposal> cachedProposals = List.of();
        List<String> cachedAbandonments = List.of();
        Map<String, Instant> driveGoalBelowThresholdSince = new HashMap<>();
        Set<String> failureSuppressedGoalNames = new HashSet<>();
    }
}
