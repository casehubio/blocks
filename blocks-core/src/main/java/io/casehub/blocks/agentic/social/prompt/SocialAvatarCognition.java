package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.CognitionConfig;
import io.casehub.blocks.agentic.social.CognitionCore;
import io.casehub.blocks.agentic.social.CognitionPhase;
import io.casehub.blocks.agentic.social.CognitiveAttentionMediator;
import io.casehub.blocks.agentic.social.ConsolidationMediator;
import io.casehub.blocks.agentic.social.ReflectionRetrievalOrchestrator;
import io.casehub.blocks.agentic.social.TemporalFocusOrchestrator;
import io.casehub.blocks.agentic.social.InnerLifeOrchestrator;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.goal.CognitiveGoalConfig;
import io.casehub.blocks.agentic.social.goal.CognitiveGoalOrchestrator;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.mindmap.GoalAppraisal;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.blocks.speech.AvatarCognition;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.blocks.speech.SpeechPromptAssembler;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.neocortex.memory.engagement.EngagementEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SocialAvatarCognition implements AvatarCognition {

    private static final System.Logger LOG = System.getLogger(SocialAvatarCognition.class.getName());

    private final           MoodOrchestrator                   mood;
    private final           DriveOrchestrator                  drives;
    private final           MentalModelOrchestrator            mentalModel;
    private final           UserModelOrchestrator              userModel;
    private final           StrategyLearningOrchestrator       strategy;
    private final           Optional<NarrativeOrchestrator>    narrative;
    private final           Optional<GoalProposalOrchestrator> goals;
    private final           Optional<AgentRegistry>            agentRegistry;
    private final           CognitionCore                      core;
    private final @Nullable CognitiveGoalOrchestrator          cognitiveGoals;

    private SocialAvatarCognition(Builder builder) {
        this.mood          = builder.mood;
        this.drives        = builder.drives;
        this.mentalModel   = builder.mentalModel;
        this.userModel     = builder.userModel;
        this.strategy      = builder.strategy;
        this.narrative     = builder.narrative;
        this.goals         = builder.goals;
        this.agentRegistry = builder.agentRegistry;
        this.core          = new CognitionCore(mood, drives, userModel, mentalModel, strategy,
                                               narrative.orElse(null), goals.orElse(null), null,
                                               builder.innerLife.orElse(null), null, CognitionConfig.all(),
                                               null, null,
                                               builder.attentionMediator.orElse(null),
                                               builder.engagementPersister.orElse(null),
                                               builder.temporalFocus.orElse(null),
                                               builder.reflectionOrchestrator.orElse(null),
                                               builder.consolidationMediator.orElse(null));

        if (builder.mindMapStore.isPresent() && builder.goalAppraisal.isPresent()
            && builder.memoryStore.isPresent()) {
            this.cognitiveGoals = new CognitiveGoalOrchestrator(
                    builder.mindMapStore.get(),
                    builder.goalAppraisal.get(),
                    builder.memoryStore.get(),
                    builder.cognitiveGoalConfig,
                    (agentId, tenantId) -> mood.currentMood(agentId, tenantId)
                                               .map(ms -> new PadProjection(ms.pleasure(), ms.arousal(), ms.dominance()))
                                               .orElse(PadProjection.NEUTRAL),
                    java.time.Clock.systemUTC());
            core.addParticipant(CognitionPhase.TERMINAL, this.cognitiveGoals);

            var cgo           = this.cognitiveGoals;
            var driveGoalOrch = goals.orElse(null);
            var config        = builder.cognitiveGoalConfig;
            core.setSectionCustomizer(sections -> {
                var result = new java.util.ArrayList<>(sections);
                for (int i = 0; i < result.size(); i++) {
                    if (result.get(i) instanceof EmergentGoalPromptSection) {
                        result.set(i, new EmergentGoalPromptSection(driveGoalOrch, cgo, config));
                        return result;
                    }
                }
                result.add(new EmergentGoalPromptSection(driveGoalOrch, cgo, config));
                return result;
            });
        } else {
            this.cognitiveGoals = null;
        }
    }

    public SocialAvatarCognition(MoodOrchestrator mood,
                                 DriveOrchestrator drives,
                                 MentalModelOrchestrator mentalModel,
                                 UserModelOrchestrator userModel,
                                 StrategyLearningOrchestrator strategy,
                                 Optional<NarrativeOrchestrator> narrative,
                                 Optional<GoalProposalOrchestrator> goals,
                                 Optional<InnerLifeOrchestrator> innerLife,
                                 Optional<AgentRegistry> agentRegistry,
                                 Optional<CognitiveAttentionMediator> attentionMediator,
                                 Optional<Consumer<EngagementEvent>> engagementPersister,
                                 Optional<TemporalFocusOrchestrator> temporalFocus,
                                 Optional<ReflectionRetrievalOrchestrator> reflectionOrchestrator,
                                 Optional<ConsolidationMediator> consolidationMediator) {
        this(builder()
                     .mood(mood).drives(drives).mentalModel(mentalModel)
                     .userModel(userModel).strategy(strategy)
                     .narrative(narrative).goals(goals).innerLife(innerLife)
                     .agentRegistry(agentRegistry).attentionMediator(attentionMediator)
                     .engagementPersister(engagementPersister).temporalFocus(temporalFocus)
                     .reflectionOrchestrator(reflectionOrchestrator)
                     .consolidationMediator(consolidationMediator));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<CognitiveGoalOrchestrator> cognitiveGoals() {
        return Optional.ofNullable(cognitiveGoals);
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
        if (core.innerLife() == null || !core.config().innerLifeEnabled() || agentRegistry.isEmpty()) {return null;}
        return agentRegistry.get().findById(agentId, tenantId)
                            .map(desc -> {
                                var support = new ProactiveSpeechSupport(core.innerLife(), desc);
                                return support.evaluateProactive(channelContext);
                            })
                            .orElse(null);
    }

    @Override
    public void recordInteraction(String agentId, String tenantId,
                                  @Nullable String subjectId,
                                  String userMessage, String response) {
        core.recordInteraction(agentId, tenantId, subjectId, userMessage, response, null);
        if (core.innerLife() != null && agentRegistry.isPresent()) {
            agentRegistry.get().findById(agentId, tenantId)
                         .ifPresent(desc -> record(() -> core.innerLife().observeResponse(desc)));
        }
    }

    List<PromptSection> buildSections(String agentId, String tenantId) {
        return new ArrayList<>(core.promptSections());
    }

    private @Nullable AgentDescriptor resolveDescriptor(String agentId, String tenantId) {
        if (agentRegistry.isEmpty()) {return null;}
        return agentRegistry.get().findById(agentId, tenantId).orElse(null);
    }

    private void record(Runnable action) {
        try {action.run();} catch (Exception e) {LOG.log(System.Logger.Level.WARNING, "Signal recording failed", e);}
    }

    public static final class Builder {
        private MoodOrchestrator                     mood;
        private DriveOrchestrator                    drives;
        private MentalModelOrchestrator              mentalModel;
        private UserModelOrchestrator                userModel;
        private StrategyLearningOrchestrator         strategy;
        private Optional<NarrativeOrchestrator>      narrative           = Optional.empty();
        private Optional<GoalProposalOrchestrator>   goals               = Optional.empty();
        private Optional<InnerLifeOrchestrator>      innerLife           = Optional.empty();
        private Optional<AgentRegistry>              agentRegistry       = Optional.empty();
        private Optional<CognitiveAttentionMediator> attentionMediator   = Optional.empty();
        private Optional<java.util.function.Consumer<io.casehub.neocortex.memory.engagement.EngagementEvent>>  engagementPersister = Optional.empty();
        private Optional<TemporalFocusOrchestrator>  temporalFocus       = Optional.empty();
        private Optional<ReflectionRetrievalOrchestrator> reflectionOrchestrator = Optional.empty();
        private Optional<ConsolidationMediator>      consolidationMediator = Optional.empty();
        private Optional<MindMapStore>               mindMapStore        = Optional.empty();
        private Optional<GoalAppraisal>              goalAppraisal       = Optional.empty();
        private Optional<CaseMemoryStore>            memoryStore         = Optional.empty();
        private CognitiveGoalConfig                  cognitiveGoalConfig = CognitiveGoalConfig.defaults();

        private Builder()                                                                        {}

        public Builder mood(MoodOrchestrator mood)                                               {
                                                                                                     this.mood = mood;
                                                                                                     return this;
                                                                                                 }

        public Builder drives(DriveOrchestrator drives)                                          {
                                                                                                     this.drives = drives;
                                                                                                     return this;
                                                                                                 }

        public Builder mentalModel(MentalModelOrchestrator mentalModel)                          {
                                                                                                     this.mentalModel = mentalModel;
                                                                                                     return this;
                                                                                                 }

        public Builder userModel(UserModelOrchestrator userModel)                                {
                                                                                                     this.userModel = userModel;
                                                                                                     return this;
                                                                                                 }

        public Builder strategy(StrategyLearningOrchestrator strategy)                           {
                                                                                                     this.strategy = strategy;
                                                                                                     return this;
                                                                                                 }

        public Builder narrative(Optional<NarrativeOrchestrator> narrative)                      {
                                                                                                     this.narrative = narrative;
                                                                                                     return this;
                                                                                                 }

        public Builder goals(Optional<GoalProposalOrchestrator> goals)                           {
                                                                                                     this.goals = goals;
                                                                                                     return this;
                                                                                                 }

        public Builder innerLife(Optional<InnerLifeOrchestrator> innerLife)                      {
                                                                                                     this.innerLife = innerLife;
                                                                                                     return this;
                                                                                                 }

        public Builder agentRegistry(Optional<AgentRegistry> agentRegistry)                      {
                                                                                                     this.agentRegistry = agentRegistry;
                                                                                                     return this;
                                                                                                 }

        public Builder attentionMediator(Optional<CognitiveAttentionMediator> attentionMediator) {
                                                                                                     this.attentionMediator = attentionMediator;
                                                                                                     return this;
                                                                                                 }

        public Builder engagementPersister(Optional<java.util.function.Consumer<io.casehub.neocortex.memory.engagement.EngagementEvent>> engagementPersister) {
                                                                                                     this.engagementPersister = engagementPersister;
                                                                                                     return this;
                                                                                                 }

        public Builder temporalFocus(Optional<TemporalFocusOrchestrator> temporalFocus) {
                                                                                                     this.temporalFocus = temporalFocus;
                                                                                                     return this;
                                                                                                 }

        public Builder reflectionOrchestrator(Optional<ReflectionRetrievalOrchestrator> reflectionOrchestrator) {
                                                                                                     this.reflectionOrchestrator = reflectionOrchestrator;
                                                                                                     return this;
                                                                                                 }

        public Builder consolidationMediator(Optional<ConsolidationMediator> consolidationMediator) {
                                                                                                     this.consolidationMediator = consolidationMediator;
                                                                                                     return this;
                                                                                                 }

        public Builder mindMapStore(Optional<MindMapStore> mindMapStore)                         {
                                                                                                     this.mindMapStore = mindMapStore;
                                                                                                     return this;
                                                                                                 }

        public Builder goalAppraisal(Optional<GoalAppraisal> goalAppraisal)                      {
                                                                                                     this.goalAppraisal = goalAppraisal;
                                                                                                     return this;
                                                                                                 }

        public Builder memoryStore(Optional<CaseMemoryStore> memoryStore)                        {
                                                                                                     this.memoryStore = memoryStore;
                                                                                                     return this;
                                                                                                 }

        public Builder cognitiveGoalConfig(CognitiveGoalConfig config)                           {
                                                                                                     this.cognitiveGoalConfig = config;
                                                                                                     return this;
                                                                                                 }

        public SocialAvatarCognition build() {
            java.util.Objects.requireNonNull(mood, "mood");
            java.util.Objects.requireNonNull(drives, "drives");
            java.util.Objects.requireNonNull(mentalModel, "mentalModel");
            java.util.Objects.requireNonNull(userModel, "userModel");
            java.util.Objects.requireNonNull(strategy, "strategy");
            return new SocialAvatarCognition(this);
        }
    }
}
