package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.CognitionCore;
import io.casehub.blocks.agentic.social.InnerLifeOrchestrator;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.speech.AvatarCognition;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.blocks.speech.SpeechPromptAssembler;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentRegistry;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class SocialAvatarCognition implements AvatarCognition {

    private static final System.Logger LOG = System.getLogger(SocialAvatarCognition.class.getName());

    private final MoodOrchestrator mood;
    private final DriveOrchestrator drives;
    private final MentalModelOrchestrator mentalModel;
    private final UserModelOrchestrator userModel;
    private final StrategyLearningOrchestrator strategy;
    private final Optional<NarrativeOrchestrator> narrative;
    private final Optional<GoalProposalOrchestrator> goals;
    private final Optional<InnerLifeOrchestrator> innerLife;
    private final Optional<AgentRegistry> agentRegistry;
    private final CognitionCore core;

    public SocialAvatarCognition(MoodOrchestrator mood,
                                  DriveOrchestrator drives,
                                  MentalModelOrchestrator mentalModel,
                                  UserModelOrchestrator userModel,
                                  StrategyLearningOrchestrator strategy,
                                  Optional<NarrativeOrchestrator> narrative,
                                  Optional<GoalProposalOrchestrator> goals,
                                  Optional<InnerLifeOrchestrator> innerLife,
                                  Optional<AgentRegistry> agentRegistry) {
        this.mood = mood;
        this.drives = drives;
        this.mentalModel = mentalModel;
        this.userModel = userModel;
        this.strategy = strategy;
        this.narrative = narrative;
        this.goals = goals;
        this.innerLife = innerLife;
        this.agentRegistry = agentRegistry;
        this.core = new CognitionCore(mood, drives, userModel, mentalModel, strategy,
                narrative.orElse(null), goals.orElse(null), null);
    }

    @Override
    public SpeechPromptAssembler wrapAssembler(SpeechPromptAssembler base, String agentId, String tenantId,
                                                Supplier<String> subjectIdSupplier) {
        var sections = buildSections(agentId, tenantId);
        return new SocialPromptAssembler(base, sections, agentId, tenantId, subjectIdSupplier);
    }

    @Override
    public void initialize(String agentId, String tenantId) {
        var descriptor = resolveDescriptor(agentId, tenantId);
        core.tick(agentId, tenantId, descriptor, (aid, tid) -> Set.of());
    }

    @Override
    public void tick(String agentId, String tenantId, Set<String> activeSubjects) {
        var descriptor = resolveDescriptor(agentId, tenantId);
        core.tick(agentId, tenantId, descriptor, (aid, tid) -> activeSubjects);
    }

    @Override
    public @Nullable String evaluateProactive(String agentId, String tenantId,
                                               String channelContext) {
        if (innerLife.isEmpty() || agentRegistry.isEmpty()) return null;
        return agentRegistry.get().findById(agentId, tenantId)
                .map(desc -> {
                    var support = new ProactiveSpeechSupport(innerLife.get(), desc);
                    return support.evaluateProactive(channelContext);
                })
                .orElse(null);
    }

    @Override
    public void recordInteraction(String agentId, String tenantId,
                                   @Nullable String subjectId,
                                   String userMessage, String response) {
        core.recordInteraction(agentId, tenantId, subjectId, userMessage, response, null);
        if (innerLife.isPresent() && agentRegistry.isPresent()) {
            agentRegistry.get().findById(agentId, tenantId)
                    .ifPresent(desc -> record(() -> innerLife.get().observeResponse(desc)));
        }
    }

    List<PromptSection> buildSections(String agentId, String tenantId) {
        var sections = new ArrayList<PromptSection>();
        if (agentRegistry.isPresent()) {
            agentRegistry.get().findById(agentId, tenantId)
                    .ifPresent(desc -> {
                        var profile = desc.disposition() != null
                                ? desc.disposition().dispositionProfile() : null;
                        sections.add(new PersonalityPromptSection(profile));
                    });
        }
        sections.addAll(core.promptSections());
        return sections;
    }

    private @Nullable AgentDescriptor resolveDescriptor(String agentId, String tenantId) {
        if (agentRegistry.isEmpty()) return null;
        return agentRegistry.get().findById(agentId, tenantId).orElse(null);
    }

    private void record(Runnable action) {
        try { action.run(); }
        catch (Exception e) { LOG.log(System.Logger.Level.WARNING, "Signal recording failed", e); }
    }
}
