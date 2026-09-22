package io.casehub.blocks.spring;

import io.casehub.blocks.agentic.social.CbrMentalModelStore;
import io.casehub.blocks.agentic.social.CbrStrategyStore;
import io.casehub.blocks.agentic.social.CbrUserProfileStore;
import io.casehub.blocks.agentic.social.CivilityConstraint;
import io.casehub.blocks.agentic.social.InnerLifeConfig;
import io.casehub.blocks.agentic.social.InnerLifeOrchestrator;
import io.casehub.blocks.agentic.social.MentalModelConfig;
import io.casehub.blocks.agentic.social.MentalModelOrchestrator;
import io.casehub.blocks.agentic.social.MentalModelStore;
import io.casehub.blocks.agentic.social.MoodConfig;
import io.casehub.blocks.agentic.social.MoodOrchestrator;
import io.casehub.blocks.agentic.social.PersonalityEvolutionConfig;
import io.casehub.blocks.agentic.social.PersonalityEvolutionOrchestrator;
import io.casehub.blocks.agentic.social.StrategyLearningConfig;
import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.agentic.social.StrategyStore;
import io.casehub.blocks.agentic.social.TraitPressureSource;
import io.casehub.blocks.agentic.social.UserModelConfig;
import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.UserProfileStore;
import io.casehub.blocks.agentic.social.drive.DriveComposer;
import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.social.drive.DriveOrchestrator;
import io.casehub.blocks.agentic.social.emergence.NormDetectionConfig;
import io.casehub.blocks.agentic.social.emergence.SocialNormDetector;
import io.casehub.blocks.agentic.social.goal.CrossAxisGoalEnricher;
import io.casehub.blocks.agentic.social.goal.DriveGoalFormationStrategy;
import io.casehub.blocks.agentic.social.goal.DriveGoalMapper;
import io.casehub.blocks.agentic.social.goal.GoalEscalationConfig;
import io.casehub.blocks.agentic.social.goal.GoalEscalationPolicy;
import io.casehub.blocks.agentic.social.goal.GoalProposalConfig;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.agentic.social.goal.LlmCrossAxisGoalEnricher;
import io.casehub.blocks.agentic.social.goal.NarrativeGoalEscalationPolicy;
import io.casehub.blocks.agentic.social.narrative.CbrNarrativeStore;
import io.casehub.blocks.agentic.social.narrative.NarrativeConfig;
import io.casehub.blocks.agentic.social.narrative.NarrativeContentSummariser;
import io.casehub.blocks.agentic.social.narrative.NarrativeOrchestrator;
import io.casehub.blocks.agentic.social.narrative.NarrativePipeline;
import io.casehub.blocks.agentic.social.narrative.NarrativeStore;
import io.casehub.blocks.agentic.social.narrative.NoOpNarrativeStore;
import io.casehub.blocks.agentic.social.prompt.SocialAvatarCognition;
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
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.AgentGraphQuery;
import io.casehub.eidos.api.DispositionEvolution;
import io.casehub.eidos.api.DispositionHealth;
import io.casehub.eidos.api.DispositionProfileStore;
import io.casehub.eidos.api.DispositionSignalStore;
import io.casehub.eidos.api.GoalSignalStore;
import io.casehub.api.spi.routing.RoutingPromptAssembler;
import io.casehub.api.spi.routing.RoutingSignalAssembler;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.blocks.prompt.SystemPromptCustomiser;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import io.casehub.qhorus.api.spi.SummaryResult;
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

    @Bean
    public NoOpNarrativeStore noOpNarrativeStore() {
        return new NoOpNarrativeStore();
    }

    // ── DefaultBean stores ──

    @Bean
    @ConditionalOnMissingBean
    public CbrUserProfileStore cbrUserProfileStore(
            CbrRecordStore cbrStore, UserModelConfig config) {
        return new CbrUserProfileStore(cbrStore, config);
    }

    @Bean
    @ConditionalOnMissingBean
    public CbrMentalModelStore cbrMentalModelStore(
            CbrRecordStore cbrStore, MentalModelConfig config) {
        return new CbrMentalModelStore(cbrStore, config);
    }

    @Bean
    @ConditionalOnMissingBean
    public CbrStrategyStore cbrStrategyStore(
            CbrRecordStore cbrStore, StrategyLearningConfig config) {
        return new CbrStrategyStore(cbrStore, config);
    }

    @Bean
    @ConditionalOnMissingBean
    public CbrNarrativeStore cbrNarrativeStore(
            CbrRecordStore cbrStore, NarrativeConfig config) {
        return new CbrNarrativeStore(cbrStore, config);
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
    public NarrativeOrchestrator narrativeOrchestrator(NarrativeStore store) {
        return new NarrativeOrchestrator(store);
    }

    @Bean
    public MentalModelOrchestrator mentalModelOrchestrator(
            MentalModelStore modelStore, AgentProvider agentProvider,
            MentalModelConfig config) {
        return new MentalModelOrchestrator(modelStore, agentProvider, config);
    }

    @Bean
    public UserModelOrchestrator userModelOrchestrator(
            UserProfileStore profileStore, AgentProvider agentProvider,
            UserModelConfig config) {
        return new UserModelOrchestrator(profileStore, agentProvider, config);
    }

    @Bean
    public StrategyLearningOrchestrator strategyLearningOrchestrator(
            StrategyStore strategyStore, CbrRecordStore cbrStore,
            ReflectionOrchestrator reflectionOrchestrator,
            AgentProvider agentProvider, StrategyLearningConfig config) {
        return new StrategyLearningOrchestrator(
                strategyStore, cbrStore, reflectionOrchestrator,
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
        return new DriveOrchestrator(
                hygieneOrchestrator, strategy, userModel, mentalModel,
                moodOrchestrator, composer, config, narrativeOrchestrator);
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

    @Bean
    public SocialNormDetector socialNormDetector(
            CbrRecordStore cbrStore, NormDetectionConfig config) {
        return new SocialNormDetector(cbrStore, config);
    }

    @Bean
    public SocialAvatarCognition socialAvatarCognition(
            MoodOrchestrator mood, DriveOrchestrator drives,
            MentalModelOrchestrator mentalModel,
            UserModelOrchestrator userModel,
            StrategyLearningOrchestrator strategy,
            Optional<NarrativeOrchestrator> narrativeOrchestrator,
            Optional<GoalProposalOrchestrator> goalProposalOrchestrator,
            Optional<InnerLifeOrchestrator> innerLifeOrchestrator,
            Optional<AgentRegistry> agentRegistry) {
        return new SocialAvatarCognition(
                mood, drives, mentalModel, userModel, strategy,
                narrativeOrchestrator, goalProposalOrchestrator,
                innerLifeOrchestrator, agentRegistry);
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
            ReflectionQueryStore reflectionQueryStore, CbrNarrativeStore cbrStore) {
        return new NarrativePipeline(summariser, config, reflectionQueryStore, cbrStore);
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
