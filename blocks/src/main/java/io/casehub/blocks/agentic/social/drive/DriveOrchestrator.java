package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeModulation;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import io.casehub.eidos.api.AgentDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class DriveOrchestrator {

    private final DriveSource curiosity;
    private final DriveSource competence;
    private final DriveSource affiliation;
    private final DriveSource autonomy;
    private final MoodOrchestrator moodOrchestrator;
    private final DriveComposer composer;
    private final DriveConfig config;
    private final Clock clock;
    private final @Nullable NarrativeOrchestrator narrativeOrchestrator;

    private final ConcurrentHashMap<String, DriveProfile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    @Inject
    public DriveOrchestrator(
            Instance<MemoryHygieneOrchestrator> hygieneInstance,
            StrategyLearningOrchestrator strategy,
            UserModelOrchestrator userModel,
            MentalModelOrchestrator mentalModel,
            MoodOrchestrator moodOrchestrator,
            DriveComposer composer,
            DriveConfig config,
            Instance<NarrativeOrchestrator> narrativeInstance) {
        this(
            hygieneInstance.isResolvable()
                ? new CuriosityDrive(hygieneInstance.get())
                : (agentId, tenantId) -> new DriveIntensity(
                        DriveAxis.CURIOSITY, 0.0, "no hygiene orchestrator"),
            new CompetenceDrive(strategy),
            new AffiliationDrive(userModel,
                    config.affiliationDecayThreshold(),
                    config.affiliationStaleDuration()),
            new AutonomyDrive(mentalModel, config.autonomyConfidenceFloor()),
            moodOrchestrator, composer, config, Clock.systemUTC(),
            narrativeInstance.isResolvable() ? narrativeInstance.get() : null
        );
    }

    public DriveOrchestrator(DriveSource curiosity, DriveSource competence,
                             DriveSource affiliation, DriveSource autonomy,
                             MoodOrchestrator moodOrchestrator, DriveComposer composer,
                             DriveConfig config) {
        this(curiosity, competence, affiliation, autonomy, moodOrchestrator,
                composer, config, Clock.systemUTC(), null);
    }

    DriveOrchestrator(DriveSource curiosity, DriveSource competence,
                      DriveSource affiliation, DriveSource autonomy,
                      MoodOrchestrator moodOrchestrator, DriveComposer composer,
                      DriveConfig config, Clock clock) {
        this(curiosity, competence, affiliation, autonomy, moodOrchestrator,
                composer, config, clock, null);
    }

    DriveOrchestrator(DriveSource curiosity, DriveSource competence,
                      DriveSource affiliation, DriveSource autonomy,
                      MoodOrchestrator moodOrchestrator, DriveComposer composer,
                      DriveConfig config, Clock clock,
                      @Nullable NarrativeOrchestrator narrativeOrchestrator) {
        this.curiosity = curiosity;
        this.competence = competence;
        this.affiliation = affiliation;
        this.autonomy = autonomy;
        this.moodOrchestrator = moodOrchestrator;
        this.composer = composer;
        this.config = config;
        this.clock = clock;
        this.narrativeOrchestrator = narrativeOrchestrator;
    }

    public DriveTick tick(String agentId, String tenantId, AgentDescriptor descriptor) {
        var key = agentId + ":" + tenantId;
        var lock = tickLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            var raw = new EnumMap<DriveAxis, DriveIntensity>(DriveAxis.class);
            raw.put(DriveAxis.CURIOSITY, curiosity.evaluate(agentId, tenantId));
            raw.put(DriveAxis.COMPETENCE, competence.evaluate(agentId, tenantId));
            raw.put(DriveAxis.AFFILIATION, affiliation.evaluate(agentId, tenantId));
            raw.put(DriveAxis.AUTONOMY, autonomy.evaluate(agentId, tenantId));

            var mood = moodOrchestrator.currentMood(agentId, tenantId).orElse(null);
            var disposition = descriptor.disposition();
            var now = Instant.now(clock);

            Map<DriveAxis, Double> narrativeMod = null;
            if (narrativeOrchestrator != null) {
                narrativeMod = narrativeOrchestrator.currentNarrative(agentId, tenantId)
                        .map(NarrativeModulation::compute)
                        .orElse(null);
            }

            var newProfile = composer.compose(raw, disposition, mood, narrativeMod, config,
                    agentId, tenantId, now);

            var previous = profiles.get(key);
            profiles.put(key, newProfile);

            if (previous == null) {
                return new DriveTick.Updated(newProfile, newProfile,
                        List.of(DriveAxis.values()));
            }

            var changed = new ArrayList<DriveAxis>();
            for (var axis : DriveAxis.values()) {
                var prevI = previous.drives().get(axis);
                var newI = newProfile.drives().get(axis);
                if (prevI == null || newI == null) {
                    changed.add(axis);
                } else if (Math.abs(prevI.intensity() - newI.intensity()) > config.changeThreshold()) {
                    changed.add(axis);
                }
            }

            if (changed.isEmpty()) {
                return new DriveTick.NoChange("all axes within threshold");
            }
            return new DriveTick.Updated(previous, newProfile, changed);
        } finally {
            lock.unlock();
        }
    }

    public Optional<DriveProfile> currentDrives(String agentId, String tenantId) {
        return Optional.ofNullable(profiles.get(agentId + ":" + tenantId));
    }

    public @Nullable CuriosityDrive curiosityDrive() {
        return curiosity instanceof CuriosityDrive cd ? cd : null;
    }

    public @Nullable CompetenceDrive competenceDrive() {
        return competence instanceof CompetenceDrive cd ? cd : null;
    }

    public @Nullable AffiliationDrive affiliationDrive() {
        return affiliation instanceof AffiliationDrive ad ? ad : null;
    }

    public @Nullable AutonomyDrive autonomyDrive() {
        return autonomy instanceof AutonomyDrive ad ? ad : null;
    }

}
