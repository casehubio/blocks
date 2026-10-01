package io.casehub.blocks.spring;

import io.casehub.api.spi.routing.RoutingPromptAssembler;
import io.casehub.api.spi.routing.RoutingSignalAssembler;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
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
import io.casehub.neocortex.cognition.core.CognitiveAttentionMediator;
import io.casehub.neocortex.cognition.drive.DriveComposer;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.goal.CrossAxisGoalEnricher;
import io.casehub.neocortex.cognition.goal.DriveGoalFormationStrategy;
import io.casehub.neocortex.cognition.goal.LlmCrossAxisGoalEnricher;
import io.casehub.neocortex.cognition.goal.NarrativeGoalEscalationPolicy;
import io.casehub.neocortex.cognition.goal.DriveGoalMapper;
import io.casehub.neocortex.cognition.goal.GoalEscalationConfig;
import io.casehub.neocortex.cognition.goal.GoalEscalationPolicy;
import io.casehub.neocortex.cognition.goal.GoalProposalConfig;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognition.innerlife.CivilityConstraint;
import io.casehub.neocortex.cognition.innerlife.InnerLifeConfig;
import io.casehub.neocortex.cognition.innerlife.InnerLifeOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelConfig;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.narrative.NarrativeConfig;
import io.casehub.neocortex.cognition.narrative.NarrativeContentSummariser;
import io.casehub.neocortex.cognition.narrative.NarrativeOrchestrator;
import io.casehub.neocortex.cognition.narrative.NarrativePipeline;
import io.casehub.neocortex.cognition.personality.PersonalityEvolutionConfig;
import io.casehub.neocortex.cognition.personality.PersonalityEvolutionOrchestrator;
import io.casehub.neocortex.cognition.personality.TraitPressureSource;
import io.casehub.neocortex.cognition.strategy.StrategyLearningConfig;
import io.casehub.neocortex.cognition.strategy.StrategyLearningOrchestrator;
import io.casehub.neocortex.cognition.temporal.ReflectionRetrievalOrchestrator;
import io.casehub.neocortex.cognition.temporal.TemporalFocusOrchestrator;
import io.casehub.neocortex.cognition.usermodel.UserModelConfig;
import io.casehub.neocortex.cognition.usermodel.UserModelOrchestrator;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@AutoConfiguration
@ConditionalOnClass(MoodOrchestrator.class)
public class BlocksAutoConfiguration {

    // ── NoOps ──

    @Bean
    @ConditionalOnMissingBean
    public NoOpReflectionStore noOpReflectionStore() {
        return new NoOpReflectionStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public NoOpSemanticIntegrityChecker noOpSemanticIntegrityChecker() {
        return new NoOpSemanticIntegrityChecker();
    }

    @Bean
    @ConditionalOnMissingBean
    public NoOpReflectionQueryStore noOpReflectionQueryStore() {
        return new NoOpReflectionQueryStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public NoOpAttestationIntentWriter noOpAttestationIntentWriter() {
        return new NoOpAttestationIntentWriter();
    }

    @Bean
    @ConditionalOnMissingBean
    public NoOpThreadSummaryStore noOpThreadSummaryStore() {
        return new NoOpThreadSummaryStore();
    }

    // noOpNarrativeStore removed — narrative persistence uses NarrativeMemory
    @SuppressWarnings("unused")
    private void noOpNarrativeStorePlaceholder() {}

    // ── DefaultBean stores ──


    @Bean
    @ConditionalOnMissingBean
    public io.casehub.neocortex.cognition.goal.CognitiveGoalConfig cognitiveGoalConfig() {
        return io.casehub.neocortex.cognition.goal.CognitiveGoalConfig.defaults();
    }

    @Bean
    @ConditionalOnMissingBean
    public io.casehub.neocortex.cognition.usermodel.UserProfileMemory userProfileMemory(
            CbrRecordStore cbrStore, UserModelConfig config) {
        return new io.casehub.neocortex.cognition.usermodel.UserProfileMemory(cbrStore, config);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.casehub.neocortex.cognition.mentalmodel.MentalModelMemory mentalModelMemory(
            CbrRecordStore cbrStore, MentalModelConfig config) {
        return new io.casehub.neocortex.cognition.mentalmodel.MentalModelMemory(cbrStore, config);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.casehub.neocortex.cognition.strategy.StrategyMemory strategyMemory(
            CbrRecordStore cbrStore, StrategyLearningConfig config) {
        return new io.casehub.neocortex.cognition.strategy.StrategyMemory(cbrStore, config);
    }

    @Bean
    @ConditionalOnMissingBean
    public io.casehub.neocortex.cognition.narrative.NarrativeMemory narrativeMemory(
            CbrRecordStore cbrStore, NarrativeConfig config) {
        return new io.casehub.neocortex.cognition.narrative.NarrativeMemory(cbrStore, config);
    }

    // ── DefaultBean weights / pure logic ──

    @Bean
    @ConditionalOnMissingBean
    public DefaultCbrCaseOutcomeWeights defaultCbrCaseOutcomeWeights() {
        return new DefaultCbrCaseOutcomeWeights();
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultCoordinationOutcomeWeights defaultCoordinationOutcomeWeights() {
        return new DefaultCoordinationOutcomeWeights();
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultCbrOutcomeWeights defaultCbrOutcomeWeights() {
        return new DefaultCbrOutcomeWeights();
    }

    @Bean
    @ConditionalOnMissingBean
    public HeuristicMessageSummariser heuristicMessageSummariser() {
        return new HeuristicMessageSummariser();
    }

    // ── Pure logic ──

    @Bean
    public CbrRoutingPromptSection cbrRoutingPromptSection() {
        return new CbrRoutingPromptSection();
    }

    @Bean
    public DispositionAwareRouting dispositionAwareRouting() {
        return new DispositionAwareRouting();
    }

    @Bean
    public DriveComposer driveComposer() {
        return new DriveComposer();
    }

    // ── Orchestrators ──

    @Bean
    public MoodOrchestrator moodOrchestrator(MoodConfig config) {
        return new MoodOrchestrator(config);
    }

    @Bean
    public NarrativeOrchestrator narrativeOrchestrator(
            io.casehub.neocortex.cognition.narrative.NarrativeMemory store) {
        return new NarrativeOrchestrator(store);
    }

    @Bean
    public MentalModelOrchestrator mentalModelOrchestrator(
            io.casehub.neocortex.cognition.mentalmodel.MentalModelMemory modelMemory,
            AgentProvider agentProvider, MentalModelConfig config) {
        return new MentalModelOrchestrator(modelMemory, agentProvider, config);
    }

    @Bean
    public UserModelOrchestrator userModelOrchestrator(
            io.casehub.neocortex.cognition.usermodel.UserProfileMemory profileMemory,
            AgentProvider agentProvider, UserModelConfig config) {
        return new UserModelOrchestrator(profileMemory, agentProvider, config);
    }

    @Bean
    public StrategyLearningOrchestrator strategyLearningOrchestrator(
            io.casehub.neocortex.cognition.strategy.StrategyMemory strategyMemory,
            ReflectionOrchestrator reflectionOrchestrator,
            AgentProvider agentProvider, StrategyLearningConfig config) {
        return new StrategyLearningOrchestrator(
                strategyMemory, reflectionOrchestrator,
                agentProvider, config);
    }

    @Bean
    public DriveOrchestrator driveOrchestrator(
            Optional<MemoryHygieneOrchestrator> hygieneOrchestrator,
            StrategyLearningOrchestrator strategy,
            UserModelOrchestrator userModel,
            MentalModelOrchestrator mentalModel,
            MoodOrchestrator moodOrchestrator,
            DriveComposer composer, DriveConfig config,
            Optional<NarrativeOrchestrator> narrativeOrchestrator) {
        var hygieneAdapter = hygieneOrchestrator
                                     .map(io.casehub.blocks.agentic.cognition.MemoryHygieneSpiAdapter::new)
                                     .orElse(null);
        var curiosity  = new io.casehub.neocortex.cognition.drive.CuriosityDrive(hygieneAdapter);
        var competence = new io.casehub.neocortex.cognition.drive.CompetenceDrive(strategy);
        var affiliation = new io.casehub.neocortex.cognition.drive.AffiliationDrive(
                userModel, config.affiliationDecayThreshold(), config.affiliationStaleDuration());
        var autonomy = new io.casehub.neocortex.cognition.drive.AutonomyDrive(
                mentalModel, config.autonomyConfidenceFloor());
        return new DriveOrchestrator(
                curiosity, competence, affiliation, autonomy,
                moodOrchestrator, composer, config, java.time.Clock.systemUTC(),
                narrativeOrchestrator.orElse(null));
    }

    @Bean
    public PersonalityEvolutionOrchestrator personalityEvolutionOrchestrator(
            DispositionSignalStore signalStore, DispositionHealth health,
            DispositionEvolution evolution, DispositionProfileStore profileStore,
            CbrRecordStore cbrStore,
            List<TraitPressureSource<?>> traitPressureSources,
            PersonalityEvolutionConfig config) {
        return new PersonalityEvolutionOrchestrator(
                signalStore, health, evolution, profileStore, cbrStore,
                traitPressureSources, config);
    }

    @Bean
    public InnerLifeOrchestrator innerLifeOrchestrator(
            ReflectionOrchestrator reflectionOrchestrator,
            AgentProvider agentProvider,
            List<CivilityConstraint> civilityConstraints,
            InnerLifeConfig innerLifeConfig,
            DriveOrchestrator driveOrchestrator) {
        return new InnerLifeOrchestrator(
                reflectionOrchestrator, agentProvider,
                civilityConstraints, innerLifeConfig, driveOrchestrator);
    }

    // socialNormDetector removed — needs migration to neocortex (follow-up issue)
    @SuppressWarnings("unused")
    private void socialNormDetectorPlaceholder() {}

    @Bean
    public io.casehub.blocks.agentic.cognition.CognitionAvatarAdapter cognitionAvatarAdapter(
            MoodOrchestrator mood, DriveOrchestrator drives,
            MentalModelOrchestrator mentalModel,
            UserModelOrchestrator userModel,
            StrategyLearningOrchestrator strategy,
            Optional<NarrativeOrchestrator> narrativeOrchestrator,
            Optional<GoalProposalOrchestrator> goalProposalOrchestrator,
            Optional<MemoryHygieneOrchestrator> hygieneOrchestrator,
            Optional<InnerLifeOrchestrator> innerLifeOrchestrator,
            Optional<AgentRegistry> agentRegistry,
            Optional<CognitiveAttentionMediator> attentionMediator,
            Optional<io.casehub.neocortex.memory.engagement.runtime.EngagementRecorderCore> engagementRecorder,
            Optional<TemporalFocusOrchestrator> temporalFocus,
            Optional<ReflectionRetrievalOrchestrator> reflectionOrchestrator,
            Optional<io.casehub.neocortex.cognition.core.ConsolidationMediator> consolidationMediator,
            Optional<io.casehub.neocortex.mindmap.MindMapStore> mindMapStore,
            Optional<io.casehub.neocortex.mindmap.GoalAppraisal> goalAppraisal,
            Optional<io.casehub.neocortex.memory.CaseMemoryStore> memoryStore,
            io.casehub.neocortex.cognition.goal.CognitiveGoalConfig cognitiveGoalConfig,
            Optional<io.casehub.neocortex.cognitive.index.CognitiveProfile> cognitiveProfile,
            Optional<io.casehub.neocortex.cognitive.index.DomainActivation> domainActivation) {
        var hygieneAdapter = hygieneOrchestrator
                                     .map(io.casehub.blocks.agentic.cognition.MemoryHygieneSpiAdapter::new)
                                     .orElse(null);
        var core = new io.casehub.neocortex.cognition.core.CognitionCore(
                mood, drives, userModel, mentalModel, strategy,
                narrativeOrchestrator.orElse(null),
                goalProposalOrchestrator.orElse(null),
                hygieneAdapter,
                innerLifeOrchestrator.orElse(null),
                null, io.casehub.neocortex.cognition.core.CognitionConfig.all(),
                mindMapStore.orElse(null), null,
                attentionMediator.orElse(null),
                engagementRecorder.map(r -> (java.util.function.Consumer<io.casehub.neocortex.memory.engagement.EngagementEvent>) r::record).orElse(null),
                temporalFocus.orElse(null),
                reflectionOrchestrator.orElse(null),
                consolidationMediator.orElse(null));

        io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator cognitiveGoals = null;
        if (mindMapStore.isPresent() && goalAppraisal.isPresent() && memoryStore.isPresent()) {
            cognitiveGoals = new io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator(
                    mindMapStore.get(), goalAppraisal.get(), memoryStore.get(), cognitiveGoalConfig,
                    (agentId, tenantId) -> mood.currentMood(agentId, tenantId)
                                               .map(ms -> new io.casehub.neocortex.cognitive.PadProjection(ms.pleasure(), ms.arousal(), ms.dominance()))
                                               .orElse(io.casehub.neocortex.cognitive.PadProjection.NEUTRAL),
                    java.time.Clock.systemUTC());
            core.addParticipant(io.casehub.neocortex.cognition.core.CognitionPhase.TERMINAL, cognitiveGoals);
        }

        cognitiveProfile.ifPresent(cp -> {
            var profileParticipant = new io.casehub.neocortex.cognition.core.CognitiveProfileParticipant(
                    cp, attentionMediator.orElse(null), temporalFocus.orElse(null),
                    io.casehub.neocortex.cognition.core.CognitionConfig.all());
            core.addParticipant(io.casehub.neocortex.cognition.core.CognitionPhase.TERMINAL, profileParticipant);
        });

        if (domainActivation.isPresent() && mindMapStore.isPresent()) {
            var domainParticipant = new io.casehub.neocortex.cognition.core.DomainActivationParticipant(
                    domainActivation.get(), mindMapStore.get(), consolidationMediator.orElse(null),
                    io.casehub.neocortex.cognition.core.CognitionConfig.all());
            core.addParticipant(io.casehub.neocortex.cognition.core.CognitionPhase.TERMINAL, domainParticipant);
        }

        return new io.casehub.blocks.agentic.cognition.CognitionAvatarAdapter(
                core, agentRegistry.orElse(null), cognitiveGoals);
    }

    // ── Goal ──

    @Bean
    public NarrativeGoalEscalationPolicy narrativeGoalEscalationPolicy(
            GoalEscalationConfig config) {
        return new NarrativeGoalEscalationPolicy(config);
    }

    @Bean
    public LlmCrossAxisGoalEnricher llmCrossAxisGoalEnricher(AgentProvider agentProvider) {
        return new LlmCrossAxisGoalEnricher(agentProvider);
    }

    @Bean
    public GoalProposalOrchestrator goalProposalOrchestrator(
            DriveOrchestrator driveOrchestrator,
            List<DriveGoalMapper> driveGoalMappers,
            Optional<DriveGoalFormationStrategy> driveGoalFormationStrategy,
            Optional<GoalSignalStore> goalSignalStore,
            Optional<NarrativeOrchestrator> narrativeOrchestrator,
            Optional<GoalEscalationPolicy> goalEscalationPolicy,
            Optional<CrossAxisGoalEnricher> crossAxisGoalEnricher,
            GoalProposalConfig config, GoalEscalationConfig escalationConfig) {
        return new GoalProposalOrchestrator(
                driveOrchestrator,
                driveGoalMappers,
                driveGoalFormationStrategy.orElse(null),
                goalSignalStore,
                narrativeOrchestrator.orElse(null),
                goalEscalationPolicy.orElse(null),
                crossAxisGoalEnricher.orElse(null),
                config, escalationConfig, Clock.systemUTC());
    }

    // ── Narrative pipeline ──

    @Bean
    public NarrativeContentSummariser narrativeContentSummariser(
            AgentProvider agentProvider, NarrativeConfig config) {
        return new NarrativeContentSummariser(agentProvider, config);
    }

    @Bean
    public NarrativePipeline narrativePipeline(
            NarrativeContentSummariser summariser, NarrativeConfig config,
            ReflectionQueryStore reflectionQueryStore,
            io.casehub.neocortex.cognition.narrative.NarrativeMemory narrativeMemory) {
        return new NarrativePipeline(summariser, config, reflectionQueryStore, narrativeMemory, null);
    }

    // ── Channel summary ──

    @Bean
    public ChannelSummariser channelSummariser(
            ContentSummariser<Message, SummaryResult> delegate) {
        return new ChannelSummariser(delegate);
    }

    @Bean
    public ThreadSummaryObserver threadSummaryObserver(
            ContentSummariser<Message, SummaryResult> contentSummariser,
            CrossTenantMessageStore messageStore,
            ThreadSummaryStore threadSummaryStore,
            ApplicationEventPublisher publisher) {
        return new ThreadSummaryObserver(
                contentSummariser, messageStore, threadSummaryStore,
                event -> publisher.publishEvent(event), null);
    }

    // ── Routing ──

    @Bean
    public CoordinationSignalProvider coordinationSignalProvider(
            CoordinationOutcomeWeights outcomeWeights) {
        return new CoordinationSignalProvider(outcomeWeights);
    }

    @Bean
    public PlanCompositionAnalyser planCompositionAnalyser(
            CbrCaseOutcomeWeights caseOutcomeWeights) {
        return new PlanCompositionAnalyser(caseOutcomeWeights);
    }

    @Bean
    public PredecessorAnalyser predecessorAnalyser(
            CbrCaseOutcomeWeights caseOutcomeWeights) {
        return new PredecessorAnalyser(caseOutcomeWeights);
    }

    @Bean
    public LlmAgentRoutingStrategy llmAgentRoutingStrategy(
            Optional<AgentProvider> agentProvider,
            Optional<TrustCandidateClassifier> classifier,
            Optional<TrustScoreSource> scoreSource,
            Optional<TrustRoutingPolicyProvider> policyProvider,
            RoutingPromptAssembler promptAssembler,
            Optional<SystemPromptCustomiser> systemPromptCustomiser) {
        return new LlmAgentRoutingStrategy(
                agentProvider.orElse(null),
                classifier.orElse(null),
                scoreSource.orElse(null),
                policyProvider.orElse(null),
                promptAssembler,
                systemPromptCustomiser.orElse(null),
                4000);
    }

    @Bean
    public CbrAgentRoutingStrategy cbrAgentRoutingStrategy(
            Optional<AgentGraphQuery> agentGraphQuery,
            Optional<TrustCandidateClassifier> classifier,
            Optional<TrustScoreSource> scoreSource,
            Optional<TrustRoutingPolicyProvider> policyProvider,
            CbrOutcomeWeights outcomeWeights,
            Optional<RoutingSignalAssembler> routingSignalAssembler) {
        return new CbrAgentRoutingStrategy(
                agentGraphQuery.orElse(null),
                classifier.orElse(null),
                scoreSource.orElse(null),
                policyProvider.orElse(null),
                outcomeWeights,
                routingSignalAssembler.orElse(null));
    }

    // ── Summarisation ──

    @Bean
    public DecisionNarrativePipeline decisionNarrativePipeline(
            AgentProvider agentProvider) {
        return new DecisionNarrativePipeline(agentProvider);
    }

    @Bean
    public DecisionNarrativeSummariser decisionNarrativeSummariser(
            AgentProvider agentProvider) {
        return new DecisionNarrativeSummariser(agentProvider);
    }
}
