package io.casehub.blocks.agentic.social.drive;

import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MentalModelSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class AutonomyDrive implements DriveSource {

    private final MentalModelOrchestrator mentalModelOrchestrator;
    private final double confidenceFloor;
    private volatile @Nullable List<MentalModelSnapshot> lastSnapshots;


    public AutonomyDrive(MentalModelOrchestrator mentalModelOrchestrator,
                         double confidenceFloor) {
        this.mentalModelOrchestrator = mentalModelOrchestrator;
        this.confidenceFloor = confidenceFloor;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var snapshots = mentalModelOrchestrator.activeSnapshots(agentId, tenantId);
        this.lastSnapshots = snapshots;

        if (snapshots.isEmpty()) {
            return new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "no mental models");
        }

        double totalPressure  = 0.0;
        int    intentionCount = 0;
        for (var snapshot : snapshots) {
            for (var intention : snapshot.intentions()) {
                if (intention.confidence() >= confidenceFloor) {
                    totalPressure += intention.confidence();
                    intentionCount++;
                }
            }
        }

        if (intentionCount == 0) {
            return new DriveIntensity(DriveAxis.AUTONOMY, 0.0, "no high-confidence intentions");
        }

        double intensity = Math.clamp(totalPressure / (intentionCount + 4.0), 0.0, 1.0);
        String trigger = intentionCount + " high-confidence intentions across "
                         + snapshots.size() + " subjects";
        return new DriveIntensity(DriveAxis.AUTONOMY, intensity, trigger);}

    public @Nullable List<MentalModelSnapshot> lastSnapshots() {
        return lastSnapshots;
    }

}
