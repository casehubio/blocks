package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.eidos.api.AgentDescriptor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class DriveOrchestrator {

    private final CuriosityDrive curiosity;
    private final CompetenceDrive competence;
    private final AffiliationDrive affiliation;
    private final AutonomyDrive autonomy;
    private final MoodOrchestrator moodOrchestrator;
    private final DriveComposer composer;
    private final DriveConfig config;
    private final Clock clock;

    private final ConcurrentHashMap<String, DriveProfile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    public DriveOrchestrator(CuriosityDrive curiosity, CompetenceDrive competence,
                             AffiliationDrive affiliation, AutonomyDrive autonomy,
                             MoodOrchestrator moodOrchestrator, DriveComposer composer,
                             DriveConfig config) {
        this(curiosity, competence, affiliation, autonomy, moodOrchestrator,
                composer, config, Clock.systemUTC());
    }

    DriveOrchestrator(CuriosityDrive curiosity, CompetenceDrive competence,
                      AffiliationDrive affiliation, AutonomyDrive autonomy,
                      MoodOrchestrator moodOrchestrator, DriveComposer composer,
                      DriveConfig config, Clock clock) {
        this.curiosity = curiosity;
        this.competence = competence;
        this.affiliation = affiliation;
        this.autonomy = autonomy;
        this.moodOrchestrator = moodOrchestrator;
        this.composer = composer;
        this.config = config;
        this.clock = clock;
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

            var newProfile = composer.compose(raw, disposition, mood, config,
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
}
