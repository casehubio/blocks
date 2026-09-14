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
import io.casehub.platform.agent.AgentProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@ApplicationScoped
public class SocialAvatarCognition implements AvatarCognition {

    private static final System.Logger LOG = System.getLogger(SocialAvatarCognition.class.getName());

    @Inject MoodOrchestrator mood;
    @Inject DriveOrchestrator drives;
    @Inject MentalModelOrchestrator mentalModel;
    @Inject UserModelOrchestrator userModel;
    @Inject StrategyLearningOrchestrator strategy;
    @Inject Instance<NarrativeOrchestrator> narrative;
    @Inject Instance<GoalProposalOrchestrator> goals;
    @Inject Instance<InnerLifeOrchestrator> innerLife;
    @Inject Instance<AgentRegistry> agentRegistry;
    @Inject Instance<AgentProvider> agentProviderInstance;

    private CognitionCore core;

    @PostConstruct
    void init() {
        core = new CognitionCore(mood, drives, userModel, mentalModel, strategy,
                narrative.isResolvable() ? narrative.get() : null,
                goals.isResolvable() ? goals.get() : null,
                null,
                agentProviderInstance.isResolvable() ? agentProviderInstance.get() : null);
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
        if (!innerLife.isResolvable() || !agentRegistry.isResolvable()) return null;
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
        if (innerLife.isResolvable() && agentRegistry.isResolvable()) {
            agentRegistry.get().findById(agentId, tenantId)
                    .ifPresent(desc -> record(() -> innerLife.get().observeResponse(desc)));
        }
    }

    List<PromptSection> buildSections(String agentId, String tenantId) {
        var sections = new ArrayList<PromptSection>();
        if (agentRegistry.isResolvable()) {
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
        if (!agentRegistry.isResolvable()) return null;
        return agentRegistry.get().findById(agentId, tenantId).orElse(null);
    }

    private void record(Runnable action) {
        try { action.run(); }
        catch (Exception e) { LOG.log(System.Logger.Level.WARNING, "Signal recording failed", e); }
    }
}
