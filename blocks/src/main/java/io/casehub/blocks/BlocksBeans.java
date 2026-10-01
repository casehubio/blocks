package io.casehub.blocks;

import io.casehub.api.spi.routing.RoutingPromptAssembler;
import io.casehub.api.spi.routing.RoutingSignalAssembler;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.blocks.agentic.cognition.CognitionAvatarAdapter;
import io.casehub.blocks.attestation.NoOpAttestationIntentWriter;
import io.casehub.blocks.channel.summary.ChannelSummariser;
import io.casehub.blocks.channel.summary.HeuristicMessageSummariser;
import io.casehub.blocks.channel.summary.NoOpThreadSummaryStore;
import io.casehub.blocks.channel.summary.ThreadSummaryObserver;
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
import io.casehub.blocks.memory.NoOpReflectionQueryStore;
import io.casehub.blocks.memory.NoOpReflectionStore;
import io.casehub.blocks.memory.NoOpSemanticIntegrityChecker;
import io.casehub.blocks.memory.ReflectionQueryStore;
import io.casehub.blocks.prompt.SystemPromptCustomiser;
import io.casehub.blocks.routing.agent.CbrAgentRoutingStrategy;
import io.casehub.blocks.routing.agent.CbrCaseOutcomeWeights;
import io.casehub.blocks.routing.agent.CbrOutcomeWeights;
import io.casehub.blocks.routing.agent.CbrRoutingPromptSection;
import io.casehub.blocks.routing.agent.CoordinationOutcomeWeights;
import io.casehub.blocks.routing.agent.CoordinationSignalProvider;
import io.casehub.blocks.routing.agent.DefaultCbrCaseOutcomeWeights;
import io.casehub.blocks.routing.agent.DefaultCbrOutcomeWeights;
import io.casehub.blocks.routing.agent.DefaultCoordinationOutcomeWeights;
import io.casehub.blocks.routing.agent.DispositionAwareRouting;
import io.casehub.blocks.routing.agent.LlmAgentRoutingStrategy;
import io.casehub.blocks.routing.agent.PlanCompositionAnalyser;
import io.casehub.blocks.routing.agent.PredecessorAnalyser;
import io.casehub.blocks.summarisation.ContentSummariser;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativeSummariser;
import io.casehub.eidos.api.AgentGraphQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.DispositionEvolution;
import io.casehub.eidos.api.DispositionHealth;
import io.casehub.eidos.api.DispositionProfileStore;
import io.casehub.eidos.api.DispositionSignalStore;
import io.casehub.eidos.api.GoalSignalStore;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.neocortex.cognition.core.CognitionCore;
import io.casehub.neocortex.cognition.core.CognitionPhase;
import io.casehub.neocortex.cognition.core.CognitiveAttentionMediator;
import io.casehub.neocortex.cognition.core.CognitiveProfileParticipant;
import io.casehub.neocortex.cognition.core.ConsolidationMediator;
import io.casehub.neocortex.cognition.core.DomainActivationParticipant;
import io.casehub.neocortex.cognition.drive.DriveComposer;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.goal.CognitiveGoalConfig;
import io.casehub.neocortex.cognition.goal.CrossAxisGoalEnricher;
import io.casehub.neocortex.cognition.goal.DriveGoalFormationStrategy;
import io.casehub.neocortex.cognition.goal.DriveGoalMapper;
import io.casehub.neocortex.cognition.goal.GoalEscalationConfig;
import io.casehub.neocortex.cognition.goal.GoalEscalationPolicy;
import io.casehub.neocortex.cognition.goal.GoalProposalConfig;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognition.innerlife.CivilityConstraint;
import io.casehub.neocortex.cognition.innerlife.InnerLifeConfig;
import io.casehub.neocortex.cognition.innerlife.InnerLifeOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelConfig;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelMemory;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.narrative.NarrativeConfig;
import io.casehub.neocortex.cognition.narrative.NarrativeContentSummariser;
import io.casehub.neocortex.cognition.narrative.NarrativeMemory;
import io.casehub.neocortex.cognition.narrative.NarrativeOrchestrator;
import io.casehub.neocortex.cognition.narrative.NarrativePipeline;
import io.casehub.neocortex.cognition.personality.PersonalityEvolutionConfig;
import io.casehub.neocortex.cognition.personality.PersonalityEvolutionOrchestrator;
import io.casehub.neocortex.cognition.personality.TraitPressureSource;
import io.casehub.neocortex.cognition.prompt.DomainActivationPromptSection;
import io.casehub.neocortex.cognition.prompt.EmergentGoalPromptSection;
import io.casehub.neocortex.cognition.prompt.EntityKnowledgePromptSection;
import io.casehub.neocortex.cognition.prompt.SocialComparisonPromptSection;
import io.casehub.neocortex.cognition.strategy.StrategyLearningConfig;
import io.casehub.neocortex.cognition.strategy.StrategyLearningOrchestrator;
import io.casehub.neocortex.cognition.strategy.StrategyMemory;
import io.casehub.neocortex.cognition.usermodel.UserModelConfig;
import io.casehub.neocortex.cognition.usermodel.UserModelOrchestrator;
import io.casehub.neocortex.cognition.usermodel.UserProfileMemory;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.qhorus.api.channel.ThreadSummaryUpdatedEvent;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import io.casehub.neocortex.cognition.goal.LlmCrossAxisGoalEnricher;
import io.casehub.neocortex.cognition.goal.NarrativeGoalEscalationPolicy;

// SocialNormDetector removed — needs migration (follow-up issue)

@ApplicationScoped
public class BlocksBeans {

    @Inject Instance<MemoryHygieneOrchestrator> hygieneOrchestratorInstance;
    @Inject Instance<NarrativeOrchestrator> narrativeOrchestratorInstance;
    @Inject Instance<GoalProposalOrchestrator> goalProposalOrchestratorInstance;
    @Inject Instance<InnerLifeOrchestrator> innerLifeOrchestratorInstance;
    @Inject Instance<AgentRegistry> agentRegistryInstance;
    @Inject Instance<CivilityConstraint> civilityConstraintInstance;
    @Inject Instance<TraitPressureSource<?>> traitPressureSourceInstance;
    @Inject Instance<DriveGoalMapper> driveGoalMapperInstance;
    @Inject Instance<DriveGoalFormationStrategy> driveGoalFormationStrategyInstance;
    @Inject Instance<GoalEscalationPolicy> goalEscalationPolicyInstance;
    @Inject Instance<CrossAxisGoalEnricher> crossAxisGoalEnricherInstance;
    @Inject Instance<GoalSignalStore> goalSignalStoreInstance;
    @Inject Instance<AgentProvider> agentProviderInstance;
    @Inject Instance<TrustCandidateClassifier> classifierInstance;
    @Inject Instance<TrustScoreSource> scoreSourceInstance;
    @Inject Instance<TrustRoutingPolicyProvider> policyProviderInstance;
    @Inject Instance<SystemPromptCustomiser> systemPromptCustomiserInstance;
    @Inject Instance<CognitiveAttentionMediator> attentionMediatorInstance;
    @Inject Instance<io.casehub.neocortex.memory.engagement.runtime.EngagementRecorderCore> engagementRecorderInstance;
    @Inject Instance<io.casehub.neocortex.cognition.temporal.TemporalFocusOrchestrator> temporalFocusOrchestratorInstance;
    @Inject Instance<io.casehub.neocortex.cognition.temporal.ReflectionRetrievalOrchestrator> reflectionOrchestratorInstance;
    @Inject Instance<ConsolidationMediator> consolidationMediatorInstance;
    @Inject Instance<io.casehub.neocortex.mindmap.MindMapStore> mindMapStoreInstance;
    @Inject Instance<io.casehub.neocortex.mindmap.GoalAppraisal> goalAppraisalInstance;
    @Inject Instance<io.casehub.neocortex.memory.CaseMemoryStore> caseMemoryStoreInstance;
    @Inject Instance<io.casehub.neocortex.cognitive.index.CognitiveProfile> cognitiveProfileInstance;
    @Inject Instance<io.casehub.neocortex.cognitive.index.DomainActivation> domainActivationInstance;
    @Inject Instance<AgentGraphQuery> agentGraphQueryInstance;
    @Inject Instance<RoutingSignalAssembler> routingSignalAssemblerInstance;
    @Inject Instance<ManagedExecutor> managedExecutorInstance;
    @Inject Event<ThreadSummaryUpdatedEvent> summaryUpdatedEvent;

    // ── NoOps ──

    @Produces @DefaultBean
    public NoOpReflectionStore noOpReflectionStore() {
        return new NoOpReflectionStore();
    }

    @Produces @DefaultBean
    public NoOpSemanticIntegrityChecker noOpSemanticIntegrityChecker() {
        return new NoOpSemanticIntegrityChecker();
    }

    @Produces @DefaultBean
    public NoOpReflectionQueryStore noOpReflectionQueryStore() {
        return new NoOpReflectionQueryStore();
    }

    @Produces @DefaultBean
    public NoOpAttestationIntentWriter noOpAttestationIntentWriter() {
        return new NoOpAttestationIntentWriter();
    }

    @Produces @DefaultBean
    public NoOpThreadSummaryStore noOpThreadSummaryStore() {
        return new NoOpThreadSummaryStore();
    }

    @Produces
    @DefaultBean
    public NarrativeMemory narrativeMemory(
            CbrRecordStore cbrStore, NarrativeConfig config) {
        return new NarrativeMemory(cbrStore, config);
    }

    // ── DefaultBean stores ──

    @Produces
    @DefaultBean
    public UserProfileMemory userProfileMemory(
            CbrRecordStore cbrStore, UserModelConfig config) {
        return new UserProfileMemory(cbrStore, config);
    }

    @Produces
    @DefaultBean
    public MentalModelMemory mentalModelMemory(
            CbrRecordStore cbrStore, MentalModelConfig config) {
        return new MentalModelMemory(cbrStore, config);
    }

    @Produces
    @DefaultBean
    public StrategyMemory strategyMemory(
            CbrRecordStore cbrStore, StrategyLearningConfig config) {
        return new StrategyMemory(cbrStore, config);
    }

    @Produces
    @DefaultBean
    public NarrativeMemory cbrNarrativeMemory(
            CbrRecordStore cbrStore, NarrativeConfig config) {
        return new NarrativeMemory(cbrStore, config);
    }

    // ── DefaultBean weights / pure logic ──

    @Produces @DefaultBean
    public DefaultCbrCaseOutcomeWeights defaultCbrCaseOutcomeWeights() {
        return new DefaultCbrCaseOutcomeWeights();
    }

    @Produces @DefaultBean
    public DefaultCoordinationOutcomeWeights defaultCoordinationOutcomeWeights() {
        return new DefaultCoordinationOutcomeWeights();
    }

    @Produces @DefaultBean
    public DefaultCbrOutcomeWeights defaultCbrOutcomeWeights() {
        return new DefaultCbrOutcomeWeights();
    }

    @Produces @DefaultBean
    public HeuristicMessageSummariser heuristicMessageSummariser() {
        return new HeuristicMessageSummariser();
    }

    // ── Pure logic ──

    @Produces @ApplicationScoped
    public CbrRoutingPromptSection cbrRoutingPromptSection() {
        return new CbrRoutingPromptSection();
    }

    @Produces @ApplicationScoped
    public DispositionAwareRouting dispositionAwareRouting() {
        return new DispositionAwareRouting();
    }

    @Produces @ApplicationScoped
    public DriveComposer driveComposer() {
        return new DriveComposer();
    }

    // ── Orchestrators ──

    @Produces @ApplicationScoped
    public MoodOrchestrator moodOrchestrator(MoodConfig config) {
        return new MoodOrchestrator(config);
    }

    @Produces
    @ApplicationScoped
    public NarrativeOrchestrator narrativeOrchestrator(NarrativeMemory store) {
        return new NarrativeOrchestrator(store);
    }

    @Produces
    @ApplicationScoped
    public MentalModelOrchestrator mentalModelOrchestrator(
            MentalModelMemory modelMemory, AgentProvider agentProvider,
            MentalModelConfig config) {
        return new MentalModelOrchestrator(modelMemory, agentProvider, config);
    }

    @Produces
    @ApplicationScoped
    public UserModelOrchestrator userModelOrchestrator(
            UserProfileMemory profileMemory, AgentProvider agentProvider,
            UserModelConfig config) {
        return new UserModelOrchestrator(profileMemory, agentProvider, config);
    }

    @Produces
    @ApplicationScoped
    public StrategyLearningOrchestrator strategyLearningOrchestrator(
            StrategyMemory strategyMemory,
            ReflectionOrchestrator reflectionOrchestrator,
            AgentProvider agentProvider, StrategyLearningConfig config) {
        return new StrategyLearningOrchestrator(
                strategyMemory, reflectionOrchestrator,
                agentProvider, config);
    }

    @Produces
    @ApplicationScoped
    public DriveOrchestrator driveOrchestrator(
            StrategyLearningOrchestrator strategy,
            UserModelOrchestrator userModel,
            MentalModelOrchestrator mentalModel,
            MoodOrchestrator moodOrchestrator,
            DriveComposer composer, DriveConfig config) {
        var hygieneAdapter = hygieneOrchestratorInstance.isResolvable()
                ? new io.casehub.blocks.agentic.cognition.MemoryHygieneSpiAdapter(hygieneOrchestratorInstance.get())
                : null;
        var curiosity = new io.casehub.neocortex.cognition.drive.CuriosityDrive(hygieneAdapter);
        var competence = new io.casehub.neocortex.cognition.drive.CompetenceDrive(strategy);
        var affiliation = new io.casehub.neocortex.cognition.drive.AffiliationDrive(
                userModel, config.affiliationDecayThreshold(), config.affiliationStaleDuration());
        var autonomy = new io.casehub.neocortex.cognition.drive.AutonomyDrive(
                mentalModel, config.autonomyConfidenceFloor());
        return new DriveOrchestrator(
                curiosity, competence, affiliation, autonomy,
                moodOrchestrator, composer, config, java.time.Clock.systemUTC(),
                nullableFrom(narrativeOrchestratorInstance));
    }

    @Produces @ApplicationScoped
    public PersonalityEvolutionOrchestrator personalityEvolutionOrchestrator(
            DispositionSignalStore signalStore, DispositionHealth health,
            DispositionEvolution evolution, DispositionProfileStore profileStore,
            CbrRecordStore cbrStore, PersonalityEvolutionConfig config) {
        return new PersonalityEvolutionOrchestrator(
                signalStore, health, evolution, profileStore, cbrStore,
                listFrom(traitPressureSourceInstance), config);
    }

    @Produces @ApplicationScoped
    public InnerLifeOrchestrator innerLifeOrchestrator(
            ReflectionOrchestrator reflectionOrchestrator,
            AgentProvider agentProvider, InnerLifeConfig innerLifeConfig,
            DriveOrchestrator driveOrchestrator) {
        return new InnerLifeOrchestrator(
                reflectionOrchestrator, agentProvider,
                listFrom(civilityConstraintInstance),
                innerLifeConfig, driveOrchestrator);
    }

    // socialNormDetector removed — SocialNormDetector needs migration to neocortex (follow-up issue)
    @SuppressWarnings("unused")
    private void socialNormDetectorPlaceholder() {}

    @Produces
    @ApplicationScoped
    public CognitionAvatarAdapter cognitionAvatarAdapter(
            MoodOrchestrator mood, DriveOrchestrator drives,
            MentalModelOrchestrator mentalModel,
            UserModelOrchestrator userModel,
            StrategyLearningOrchestrator strategy,
            CognitiveGoalConfig cognitiveGoalConfig) {
        var hygieneAdapterForCore = hygieneOrchestratorInstance.isResolvable()
                ? new io.casehub.blocks.agentic.cognition.MemoryHygieneSpiAdapter(hygieneOrchestratorInstance.get())
                : null;
        var core = new CognitionCore(mood, drives, userModel, mentalModel, strategy,
                                     nullableFrom(narrativeOrchestratorInstance),
                                     nullableFrom(goalProposalOrchestratorInstance),
                                     hygieneAdapterForCore,
                                     nullableFrom(innerLifeOrchestratorInstance),
                                     null, io.casehub.neocortex.cognition.core.CognitionConfig.all(),
                                     nullableFrom(mindMapStoreInstance), null,
                                     nullableFrom(attentionMediatorInstance),
                                     optionalFrom(engagementRecorderInstance)
                                             .map(r -> (java.util.function.Consumer<io.casehub.neocortex.memory.engagement.EngagementEvent>) r::record)
                                             .orElse(null),
                                     nullableFrom(temporalFocusOrchestratorInstance),
                                     nullableFrom(reflectionOrchestratorInstance),
                                     nullableFrom(consolidationMediatorInstance));

        io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator cognitiveGoals = null;
        if (mindMapStoreInstance.isResolvable() && goalAppraisalInstance.isResolvable()
            && caseMemoryStoreInstance.isResolvable()) {
            cognitiveGoals = new io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator(
                    mindMapStoreInstance.get(),
                    goalAppraisalInstance.get(),
                    caseMemoryStoreInstance.get(),
                    cognitiveGoalConfig,
                    (agentId, tenantId) -> mood.currentMood(agentId, tenantId)
                                               .map(ms -> new io.casehub.neocortex.cognitive.PadProjection(
                                                       ms.pleasure(), ms.arousal(), ms.dominance()))
                                               .orElse(io.casehub.neocortex.cognitive.PadProjection.NEUTRAL),
                    java.time.Clock.systemUTC());
            core.addParticipant(CognitionPhase.TERMINAL, cognitiveGoals);
            var cgo           = cognitiveGoals;
            var driveGoalOrch = nullableFrom(goalProposalOrchestratorInstance);
            core.chainSectionCustomizer(sections -> {
                var result = new java.util.ArrayList<>(sections);
                for (int i = 0; i < result.size(); i++) {
                    if (result.get(i) instanceof EmergentGoalPromptSection) {
                        result.set(i, new EmergentGoalPromptSection(driveGoalOrch, cgo, cognitiveGoalConfig));
                        return result;
                    }
                }
                result.add(new EmergentGoalPromptSection(driveGoalOrch, cgo, cognitiveGoalConfig));
                return result;
            });
        }

        optionalFrom(cognitiveProfileInstance).ifPresent(cp -> {
            var profileParticipant = new CognitiveProfileParticipant(
                    cp, nullableFrom(attentionMediatorInstance),
                    nullableFrom(temporalFocusOrchestratorInstance),
                    io.casehub.neocortex.cognition.core.CognitionConfig.all());
            core.addParticipant(CognitionPhase.TERMINAL, profileParticipant);
            core.chainSectionCustomizer(sections -> {
                var result    = new java.util.ArrayList<>(sections);
                var knowledge = profileParticipant.lastEntityKnowledge();
                if (!knowledge.isEmpty()) {
                    result.add(new EntityKnowledgePromptSection(knowledge));
                }
                var socialComparisons = profileParticipant.lastSocialComparisons();
                if (!socialComparisons.isEmpty()) {
                    result.add(new SocialComparisonPromptSection(socialComparisons));
                }
                return result;
            });
        });

        if (domainActivationInstance.isResolvable() && mindMapStoreInstance.isResolvable()) {
            var domainActivationParticipant = new DomainActivationParticipant(
                    domainActivationInstance.get(),
                    mindMapStoreInstance.get(),
                    nullableFrom(consolidationMediatorInstance),
                    io.casehub.neocortex.cognition.core.CognitionConfig.all());
            core.addParticipant(CognitionPhase.TERMINAL, domainActivationParticipant);
            core.chainSectionCustomizer(sections -> {
                var result   = new java.util.ArrayList<>(sections);
                var snapshot = domainActivationParticipant.lastSnapshot();
                if (snapshot != null && snapshot.hasRenderableCorrelations()) {
                    result.add(new DomainActivationPromptSection(snapshot));
                }
                return result;
            });
        }

        return new CognitionAvatarAdapter(core,
                                          nullableFrom(agentRegistryInstance), cognitiveGoals);
    }

    // ── Goal ──

    @Produces @ApplicationScoped
    public NarrativeGoalEscalationPolicy narrativeGoalEscalationPolicy(
            GoalEscalationConfig config) {
        return new NarrativeGoalEscalationPolicy(config);
    }

    @Produces @ApplicationScoped
    public LlmCrossAxisGoalEnricher llmCrossAxisGoalEnricher(AgentProvider agentProvider) {
        return new LlmCrossAxisGoalEnricher(agentProvider);
    }

    @Produces @ApplicationScoped
    public GoalProposalOrchestrator goalProposalOrchestrator(
            DriveOrchestrator driveOrchestrator,
            GoalProposalConfig config, GoalEscalationConfig escalationConfig) {
        return new GoalProposalOrchestrator(
                driveOrchestrator,
                listFrom(driveGoalMapperInstance),
                nullableFrom(driveGoalFormationStrategyInstance),
                optionalFrom(goalSignalStoreInstance),
                nullableFrom(narrativeOrchestratorInstance),
                nullableFrom(goalEscalationPolicyInstance),
                nullableFrom(crossAxisGoalEnricherInstance),
                config, escalationConfig, java.time.Clock.systemUTC());
    }

    // ── Narrative pipeline ──

    @Produces @ApplicationScoped
    public NarrativeContentSummariser narrativeContentSummariser(
            AgentProvider agentProvider, NarrativeConfig config) {
        return new NarrativeContentSummariser(agentProvider, config);
    }

    @Produces
    @ApplicationScoped
    public NarrativePipeline narrativePipeline(
            NarrativeContentSummariser summariser, NarrativeConfig config,
            ReflectionQueryStore reflectionQueryStore, NarrativeMemory narrativeMemory) {
        return new NarrativePipeline(summariser, config, reflectionQueryStore, narrativeMemory, null);
    }

    // ── Channel summary ──

    @Produces @ApplicationScoped
    public ChannelSummariser channelSummariser(
            ContentSummariser<Message, SummaryResult> delegate) {
        return new ChannelSummariser(delegate);
    }

    @Produces @ApplicationScoped
    public ThreadSummaryObserver threadSummaryObserver(
            ContentSummariser<Message, SummaryResult> contentSummariser,
            CrossTenantMessageStore messageStore,
            ThreadSummaryStore threadSummaryStore) {
        return new ThreadSummaryObserver(
                contentSummariser, messageStore, threadSummaryStore,
                event -> summaryUpdatedEvent.fireAsync(event),
                managedExecutorInstance.isResolvable()
                        ? managedExecutorInstance.get() : null);
    }

    // ── Routing ──

    @Produces @ApplicationScoped
    public CoordinationSignalProvider coordinationSignalProvider(
            CoordinationOutcomeWeights outcomeWeights) {
        return new CoordinationSignalProvider(outcomeWeights);
    }

    @Produces @ApplicationScoped
    public PlanCompositionAnalyser planCompositionAnalyser(
            CbrCaseOutcomeWeights caseOutcomeWeights) {
        return new PlanCompositionAnalyser(caseOutcomeWeights);
    }

    @Produces @ApplicationScoped
    public PredecessorAnalyser predecessorAnalyser(
            CbrCaseOutcomeWeights caseOutcomeWeights) {
        return new PredecessorAnalyser(caseOutcomeWeights);
    }

    @Produces @ApplicationScoped
    public LlmAgentRoutingStrategy llmAgentRoutingStrategy(
            RoutingPromptAssembler promptAssembler) {
        return new LlmAgentRoutingStrategy(
                nullableFrom(agentProviderInstance),
                nullableFrom(classifierInstance),
                nullableFrom(scoreSourceInstance),
                nullableFrom(policyProviderInstance),
                promptAssembler,
                nullableFrom(systemPromptCustomiserInstance),
                4000);
    }

    @Produces @ApplicationScoped
    public CbrAgentRoutingStrategy cbrAgentRoutingStrategy(
            CbrOutcomeWeights outcomeWeights) {
        return new CbrAgentRoutingStrategy(
                nullableFrom(agentGraphQueryInstance),
                nullableFrom(classifierInstance),
                nullableFrom(scoreSourceInstance),
                nullableFrom(policyProviderInstance),
                outcomeWeights,
                nullableFrom(routingSignalAssemblerInstance));
    }

    // ── Summarisation ──

    @Produces @ApplicationScoped
    public DecisionNarrativePipeline decisionNarrativePipeline(
            AgentProvider agentProvider) {
        return new DecisionNarrativePipeline(agentProvider);
    }

    @Produces @ApplicationScoped
    public DecisionNarrativeSummariser decisionNarrativeSummariser(
            AgentProvider agentProvider) {
        return new DecisionNarrativeSummariser(agentProvider);
    }

    // ── Helpers ──

    private static <T> Optional<T> optionalFrom(Instance<T> instance) {
        return instance.isResolvable() ? Optional.of(instance.get()) : Optional.empty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> List<T> listFrom(Instance<? extends T> instance) {
        return StreamSupport.stream(((Instance) instance).spliterator(), false)
                .map(o -> (T) o)
                .toList();
    }

    private static <T> T nullableFrom(Instance<T> instance) {
        return instance.isResolvable() ? instance.get() : null;
    }
}
