package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.CueType;
import io.casehub.blocks.agentic.social.EngagementSignal;
import io.casehub.blocks.agentic.social.InnerLifeOrchestrator;
import io.casehub.blocks.agentic.social.InteractionSignal;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MentalStateSignal;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.speech.AvatarCognition;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.blocks.speech.SpeechPromptAssembler;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.neocortex.memory.engagement.EngagementEvent;
import io.casehub.neocortex.memory.relationship.QualitySignal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    @Override
    public SpeechPromptAssembler wrapAssembler(SpeechPromptAssembler base, String agentId, String tenantId,
                                                Supplier<String> subjectIdSupplier) {
        var sections = buildSections(agentId, tenantId);
        return new SocialPromptAssembler(base, sections, agentId, tenantId, subjectIdSupplier);
    }

    @Override
    public void initialize(String agentId, String tenantId) {
        if (agentRegistry.isResolvable()) {
            agentRegistry.get().findById(agentId, tenantId)
                    .ifPresent(desc -> record(() -> drives.tick(agentId, tenantId, desc)));
        }
    }

    @Override
    public void tick(String agentId, String tenantId, Set<String> activeSubjects) {
        record(() -> mood.tick(agentId, tenantId));
        record(() -> strategy.tick(agentId, tenantId));
        if (narrative.isResolvable()) {
            record(() -> narrative.get().tick(agentId, tenantId));
        }
        if (goals.isResolvable() && agentRegistry.isResolvable()) {
            agentRegistry.get().findById(agentId, tenantId)
                    .ifPresent(desc -> record(() -> goals.get().tick(agentId, tenantId, desc)));
        }
        for (String subjectId : activeSubjects) {
            record(() -> userModel.tick(agentId, subjectId, tenantId));
            record(() -> mentalModel.tick(agentId, subjectId, tenantId));
        }
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
        if (subjectId != null) {
            record(() -> userModel.record(
                    new InteractionSignal.CustomSignal(userMessage, QualitySignal.NEUTRAL),
                    agentId, subjectId, tenantId));
            record(() -> mentalModel.record(
                    new MentalStateSignal.VerbalCue(userMessage, CueType.BELIEF_STATEMENT),
                    agentId, subjectId, tenantId));
            record(() -> strategy.record(
                    new EngagementSignal.TurnOutcome(
                            new EngagementEvent(agentId, subjectId, tenantId,
                                    null, UUID.randomUUID().toString(), Instant.now(),
                                    userMessage.isBlank() ? "[interaction]" : userMessage,
                                    null, Map.of(), true, null,
                                    (int) response.length(), null, null, null),
                            Map.of(), response),
                    agentId, subjectId, tenantId));
        }
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
        sections.add(new MoodPromptSection(mood));
        sections.add(new DrivePromptSection(drives));
        sections.add(new MentalModelPromptSection(mentalModel));
        sections.add(new UserModelPromptSection(userModel));
        sections.add(new StrategyPromptSection(strategy));
        if (narrative.isResolvable()) {
            sections.add(new NarrativePromptSection(narrative.get()));
        }
        if (goals.isResolvable()) {
            sections.add(new GoalPromptSection(goals.get()));
        }
        return sections;
    }

    private void record(Runnable action) {
        try { action.run(); }
        catch (Exception e) { LOG.log(System.Logger.Level.WARNING, "Signal recording failed", e); }
    }
}
