package io.casehub.blocks.agentic.social;

import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.agentic.social.prompt.DrivePromptSection;
import io.casehub.blocks.agentic.social.prompt.GoalPromptSection;
import io.casehub.blocks.agentic.social.prompt.MentalModelPromptSection;
import io.casehub.blocks.agentic.social.prompt.MoodPromptSection;
import io.casehub.blocks.agentic.social.prompt.NarrativePromptSection;
import io.casehub.blocks.agentic.social.prompt.StrategyPromptSection;
import io.casehub.blocks.agentic.social.prompt.UserModelPromptSection;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.memory.engagement.EngagementEvent;
import io.casehub.neocortex.memory.relationship.QualitySignal;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CognitionCore {

    private static final System.Logger LOG =
            System.getLogger(CognitionCore.class.getName());

    private final MoodOrchestrator mood;
    private final DriveOrchestrator drives;
    private final @Nullable UserModelOrchestrator userModel;
    private final @Nullable MentalModelOrchestrator mentalModel;
    private final @Nullable StrategyLearningOrchestrator strategy;
    private final @Nullable NarrativeOrchestrator narrative;
    private final @Nullable GoalProposalOrchestrator goals;
    private final @Nullable MemoryHygieneOrchestrator memoryHygiene;

    public CognitionCore(MoodOrchestrator mood,
                          DriveOrchestrator drives,
                          @Nullable UserModelOrchestrator userModel,
                          @Nullable MentalModelOrchestrator mentalModel,
                          @Nullable StrategyLearningOrchestrator strategy,
                          @Nullable NarrativeOrchestrator narrative,
                          @Nullable GoalProposalOrchestrator goals,
                          @Nullable MemoryHygieneOrchestrator memoryHygiene) {
        this.mood = mood;
        this.drives = drives;
        this.userModel = userModel;
        this.mentalModel = mentalModel;
        this.strategy = strategy;
        this.narrative = narrative;
        this.goals = goals;
        this.memoryHygiene = memoryHygiene;
    }

    public void tick(String agentId, String tenantId,
                     @Nullable AgentDescriptor descriptor,
                     Set<String> activeSubjects) {
        if (mood.currentMood(agentId, tenantId).isEmpty()) {
            mood.record(new MoodSignal.InteractionAppraisal(0, 0, 0, "init"),
                    agentId, tenantId);
        }
        mood.tick(agentId, tenantId);

        if (memoryHygiene != null) {
            safeRun(() -> memoryHygiene.tick(agentId, tenantId));
        }

        if (narrative != null) {
            safeRun(() -> narrative.tick(agentId, tenantId));
        }

        if (descriptor != null) {
            safeRun(() -> drives.tick(agentId, tenantId, descriptor));
        }

        if (strategy != null) {
            safeRun(() -> strategy.tick(agentId, tenantId));
        }

        for (String subjectId : activeSubjects) {
            if (userModel != null) {
                safeRun(() -> userModel.tick(agentId, subjectId, tenantId));
            }
            if (mentalModel != null) {
                safeRun(() -> mentalModel.tick(agentId, subjectId, tenantId));
            }
        }

        if (goals != null && descriptor != null) {
            safeRun(() -> goals.tick(agentId, tenantId, descriptor));
        }
    }

    public void recordInteraction(String agentId, String tenantId,
                                   @Nullable String subjectId,
                                   String userMessage, String response) {
        if (subjectId != null) {
            if (userModel != null) {
                safeRun(() -> userModel.record(
                        new InteractionSignal.CustomSignal(
                                userMessage, QualitySignal.NEUTRAL),
                        agentId, subjectId, tenantId));
            }
            if (mentalModel != null) {
                safeRun(() -> mentalModel.record(
                        new MentalStateSignal.VerbalCue(
                                userMessage, CueType.BELIEF_STATEMENT),
                        agentId, subjectId, tenantId));
            }
            if (strategy != null) {
                safeRun(() -> strategy.record(
                        new EngagementSignal.TurnOutcome(
                                new EngagementEvent(agentId, subjectId,
                                        tenantId, null,
                                        UUID.randomUUID().toString(),
                                        Instant.now(),
                                        userMessage.isBlank()
                                                ? "[interaction]"
                                                : userMessage,
                                        null, Map.of(), true, null,
                                        (int) response.length(),
                                        null, null, null),
                                Map.of(), response),
                        agentId, subjectId, tenantId));
            }
        }
    }

    public List<PromptSection> promptSections() {
        var sections = new ArrayList<PromptSection>();
        sections.add(new MoodPromptSection(mood));
        sections.add(new DrivePromptSection(drives));
        if (narrative != null)
            sections.add(new NarrativePromptSection(narrative));
        if (userModel != null)
            sections.add(new UserModelPromptSection(userModel));
        if (mentalModel != null)
            sections.add(new MentalModelPromptSection(mentalModel));
        if (strategy != null)
            sections.add(new StrategyPromptSection(strategy));
        if (goals != null)
            sections.add(new GoalPromptSection(goals));
        return sections;
    }

    public MoodOrchestrator mood() { return mood; }
    public DriveOrchestrator drives() { return drives; }
    public @Nullable UserModelOrchestrator userModel() { return userModel; }
    public @Nullable MentalModelOrchestrator mentalModel() { return mentalModel; }
    public @Nullable StrategyLearningOrchestrator strategy() { return strategy; }
    public @Nullable NarrativeOrchestrator narrative() { return narrative; }
    public @Nullable GoalProposalOrchestrator goals() { return goals; }
    public @Nullable MemoryHygieneOrchestrator memoryHygiene() { return memoryHygiene; }

    private void safeRun(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Orchestrator operation failed", e);
        }
    }
}
