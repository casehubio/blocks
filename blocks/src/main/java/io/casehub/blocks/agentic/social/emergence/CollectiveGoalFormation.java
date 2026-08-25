package io.casehub.blocks.agentic.social.emergence;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveProfile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class CollectiveGoalFormation {

    private final DriveOrchestrator driveOrchestrator;
    private final List<String> agentIds;
    private final CollectiveGoalConfig config;
    private final Clock clock;

    private final ConcurrentHashMap<String, List<CollectiveGoalProposal>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> cooldowns = new ConcurrentHashMap<>();

    public CollectiveGoalFormation(DriveOrchestrator driveOrchestrator,
                                   List<String> agentIds,
                                   CollectiveGoalConfig config) {
        this(driveOrchestrator, agentIds, config, Clock.systemUTC());
    }

    CollectiveGoalFormation(DriveOrchestrator driveOrchestrator,
                            List<String> agentIds,
                            CollectiveGoalConfig config,
                            Clock clock) {
        this.driveOrchestrator = Objects.requireNonNull(driveOrchestrator);
        this.agentIds = List.copyOf(agentIds);
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
    }

    public CollectiveGoalTick tick(String tenantId) {
        var lock = tickLocks.computeIfAbsent(tenantId, k -> new ReentrantLock());
        lock.lock();
        try {
            return doTick(tenantId);
        } finally {
            lock.unlock();
        }
    }

    public Optional<List<CollectiveGoalProposal>> currentProposals(String tenantId) {
        var proposals = cache.get(tenantId);
        if (proposals == null || proposals.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(proposals));
    }

    public static io.casehub.blocks.agentic.intention.JointIntention toJointIntention(
            CollectiveGoalProposal proposal, Instant formedAt) {
        String groupKey = proposal.proposedParticipants().stream().sorted().collect(Collectors.joining("|"));
        String intentionId = "collective-" + proposal.primaryAxis().name().toLowerCase()
                             + "-" + groupKey.hashCode()
                             + "-" + proposal.alignment().computedAt().toEpochMilli();
        return io.casehub.blocks.agentic.intention.JointIntention.form(
                intentionId, proposal.goalDescription(),
                proposal.proposedParticipants(), formedAt);
    }


    private CollectiveGoalTick doTick(String tenantId) {
        var profiles = new HashMap<String, DriveProfile>();
        for (var agentId : agentIds) {
            driveOrchestrator.currentDrives(agentId, tenantId).ifPresent(p -> profiles.put(agentId, p));
        }

        if (profiles.size() < config.minAlignedAgents()) {
            return new CollectiveGoalTick.NoChange("insufficient profiles");
        }

        var qualifyingAlignments = computeQualifyingAlignments(profiles);
        if (qualifyingAlignments.isEmpty()) {
            return new CollectiveGoalTick.NoChange("no alignment above threshold");
        }

        var groups = formGroups(qualifyingAlignments, profiles.keySet());
        var proposals = new ArrayList<CollectiveGoalProposal>();

        for (var group : groups) {
            if (group.size() < config.minAlignedAgents()) continue;

            var groupKey = cooldownKey(tenantId, group);
            if (isCooldownActive(groupKey)) continue;

            var groupAlignment = computeGroupAlignment(group, profiles);
            if (groupAlignment.compositeAlignment() < config.alignmentThreshold()) continue;

            var primaryAxis = groupAlignment.dominantSharedAxis() != null
                    ? groupAlignment.dominantSharedAxis()
                    : DriveAxis.CURIOSITY;

            proposals.add(new CollectiveGoalProposal(
                    groupAlignment,
                    goalDescription(primaryAxis),
                    Set.copyOf(group),
                    primaryAxis));

            cooldowns.put(groupKey, Instant.now(clock));
        }

        if (proposals.isEmpty()) {
            cache.put(tenantId, List.of());
            return new CollectiveGoalTick.NoChange("no qualifying groups");
        }

        cache.put(tenantId, List.copyOf(proposals));
        return new CollectiveGoalTick.Proposed(List.copyOf(proposals), List.copyOf(qualifyingAlignments));
    }

    private List<DriveAlignment> computeQualifyingAlignments(Map<String, DriveProfile> profiles) {
        var agents = new ArrayList<>(profiles.keySet());
        var alignments = new ArrayList<DriveAlignment>();

        for (int i = 0; i < agents.size(); i++) {
            for (int j = i + 1; j < agents.size(); j++) {
                var a = profiles.get(agents.get(i));
                var b = profiles.get(agents.get(j));
                var alignment = computePairwiseAlignment(a, b);
                if (alignment.compositeAlignment() >= config.alignmentThreshold()) {
                    alignments.add(alignment);
                }
            }
        }
        return alignments;
    }

    static DriveAlignment computePairwiseAlignment(DriveProfile a, DriveProfile b) {
        var alignmentPerAxis = new EnumMap<DriveAxis, Double>(DriveAxis.class);
        double sum = 0.0;
        double bestScore = -1.0;
        DriveAxis dominant = null;

        for (var axis : DriveAxis.values()) {
            double intensityA = intensityFor(a, axis);
            double intensityB = intensityFor(b, axis);
            double axisAlignment = 1.0 - Math.abs(intensityA - intensityB);
            alignmentPerAxis.put(axis, axisAlignment);
            sum += axisAlignment;

            double sharedScore = axisAlignment * Math.min(intensityA, intensityB);
            if (sharedScore > bestScore) {
                bestScore = sharedScore;
                dominant = axis;
            }
        }

        double composite = sum / DriveAxis.values().length;
        return new DriveAlignment(
                Set.of(a.agentId(), b.agentId()),
                alignmentPerAxis, composite, dominant,
                a.evaluatedAt().isAfter(b.evaluatedAt()) ? a.evaluatedAt() : b.evaluatedAt());
    }

    private List<Set<String>> formGroups(List<DriveAlignment> alignments, Set<String> allAgents) {
        var parent = new HashMap<String, String>();
        for (var agent : allAgents) {
            parent.put(agent, agent);
        }

        for (var alignment : alignments) {
            var ids = new ArrayList<>(alignment.agentIds());
            for (int i = 1; i < ids.size(); i++) {
                union(parent, ids.get(0), ids.get(i));
            }
        }

        var groups = new HashMap<String, Set<String>>();
        for (var agent : allAgents) {
            var root = find(parent, agent);
            groups.computeIfAbsent(root, k -> new LinkedHashSet<>()).add(agent);
        }

        return groups.values().stream()
                .filter(g -> g.size() >= config.minAlignedAgents())
                .toList();
    }

    private DriveAlignment computeGroupAlignment(Set<String> group, Map<String, DriveProfile> profiles) {
        var agents = new ArrayList<>(group);
        var axisAlignmentSums = new EnumMap<DriveAxis, Double>(DriveAxis.class);
        for (var axis : DriveAxis.values()) {
            axisAlignmentSums.put(axis, 0.0);
        }

        int pairCount = 0;
        var axisIntensitySums = new EnumMap<DriveAxis, Double>(DriveAxis.class);
        for (var axis : DriveAxis.values()) {
            axisIntensitySums.put(axis, 0.0);
        }

        for (int i = 0; i < agents.size(); i++) {
            var pi = profiles.get(agents.get(i));
            for (var axis : DriveAxis.values()) {
                axisIntensitySums.merge(axis, intensityFor(pi, axis), Double::sum);
            }
            for (int j = i + 1; j < agents.size(); j++) {
                var pj = profiles.get(agents.get(j));
                for (var axis : DriveAxis.values()) {
                    double ai = intensityFor(pi, axis);
                    double aj = intensityFor(pj, axis);
                    axisAlignmentSums.merge(axis, 1.0 - Math.abs(ai - aj), Double::sum);
                }
                pairCount++;
            }
        }

        var alignmentPerAxis = new EnumMap<DriveAxis, Double>(DriveAxis.class);
        double compositeSum = 0.0;
        double bestScore = -1.0;
        DriveAxis dominant = null;

        for (var axis : DriveAxis.values()) {
            double axisAlignment = axisAlignmentSums.get(axis) / pairCount;
            alignmentPerAxis.put(axis, axisAlignment);
            compositeSum += axisAlignment;

            double avgIntensity = axisIntensitySums.get(axis) / agents.size();
            double score = axisAlignment * avgIntensity;
            if (score > bestScore) {
                bestScore = score;
                dominant = axis;
            }
        }

        double composite = compositeSum / DriveAxis.values().length;
        var latestEval = agents.stream()
                .map(profiles::get)
                .map(DriveProfile::evaluatedAt)
                .max(Instant::compareTo)
                .orElse(Instant.now(clock));

        return new DriveAlignment(Set.copyOf(group), alignmentPerAxis, composite, dominant, latestEval);
    }

    private boolean isCooldownActive(String groupKey) {
        var lastProposal = cooldowns.get(groupKey);
        if (lastProposal == null) return false;
        return Duration.between(lastProposal, Instant.now(clock)).compareTo(config.cooldown()) < 0;
    }

    private static String cooldownKey(String tenantId, Set<String> group) {
        var sorted = group.stream().sorted().collect(Collectors.joining("|"));
        return tenantId + ":" + sorted;
    }

    private static double intensityFor(DriveProfile profile, DriveAxis axis) {
        var intensity = profile.drives().get(axis);
        return intensity != null ? intensity.intensity() : 0.0;
    }

    private static String goalDescription(DriveAxis axis) {
        return switch (axis) {
            case CURIOSITY -> "collective-exploration";
            case COMPETENCE -> "collective-skill-improvement";
            case AFFILIATION -> "collective-relationship-building";
            case AUTONOMY -> "collective-autonomy-initiative";
        };
    }

    private static String find(Map<String, String> parent, String x) {
        while (!parent.get(x).equals(x)) {
            parent.put(x, parent.get(parent.get(x)));
            x = parent.get(x);
        }
        return x;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        var rootA = find(parent, a);
        var rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootB, rootA);
        }
    }
}
