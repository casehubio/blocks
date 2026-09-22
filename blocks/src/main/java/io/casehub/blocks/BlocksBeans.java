package io.casehub.blocks;

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
import io.casehub.blocks.memory.MemoryHygieneOrchestrator;
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
import io.casehub.qhorus.api.channel.ThreadSummaryUpdatedEvent;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.eclipse.microprofile.context.ManagedExecutor;

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

    @Produces @ApplicationScoped
    public NoOpNarrativeStore noOpNarrativeStore() {
        return new NoOpNarrativeStore();
    }

    // ── DefaultBean stores ──

    @Produces @DefaultBean
    public CbrUserProfileStore cbrUserProfileStore(
            CbrRecordStore cbrStore, UserModelConfig config) {
        return new CbrUserProfileStore(cbrStore, config);
    }

    @Produces @DefaultBean
    public CbrMentalModelStore cbrMentalModelStore(
            CbrRecordStore cbrStore, MentalModelConfig config) {
        return new CbrMentalModelStore(cbrStore, config);
    }

    @Produces @DefaultBean
    public CbrStrategyStore cbrStrategyStore(
            CbrRecordStore cbrStore, StrategyLearningConfig config) {
        return new CbrStrategyStore(cbrStore, config);
    }

    @Produces @DefaultBean
    public CbrNarrativeStore cbrNarrativeStore(
            CbrRecordStore cbrStore, NarrativeConfig config) {
        return new CbrNarrativeStore(cbrStore, config);
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

    @Produces @ApplicationScoped
    public NarrativeOrchestrator narrativeOrchestrator(NarrativeStore store) {
        return new NarrativeOrchestrator(store);
    }

    @Produces @ApplicationScoped
    public MentalModelOrchestrator mentalModelOrchestrator(
            MentalModelStore modelStore, AgentProvider agentProvider,
            MentalModelConfig config) {
        return new MentalModelOrchestrator(modelStore, agentProvider, config);
    }

    @Produces @ApplicationScoped
    public UserModelOrchestrator userModelOrchestrator(
            UserProfileStore profileStore, AgentProvider agentProvider,
            UserModelConfig config) {
        return new UserModelOrchestrator(profileStore, agentProvider, config);
    }

    @Produces @ApplicationScoped
    public StrategyLearningOrchestrator strategyLearningOrchestrator(
            StrategyStore strategyStore, CbrRecordStore cbrStore,
            ReflectionOrchestrator reflectionOrchestrator,
            AgentProvider agentProvider, StrategyLearningConfig config) {
        return new StrategyLearningOrchestrator(
                strategyStore, cbrStore, reflectionOrchestrator,
                agentProvider, config);
    }

    @Produces @ApplicationScoped
    public DriveOrchestrator driveOrchestrator(
            StrategyLearningOrchestrator strategy,
            UserModelOrchestrator userModel,
            MentalModelOrchestrator mentalModel,
            MoodOrchestrator moodOrchestrator,
            DriveComposer composer, DriveConfig config) {
        return new DriveOrchestrator(
                optionalFrom(hygieneOrchestratorInstance),
                strategy, userModel, mentalModel, moodOrchestrator,
                composer, config,
                optionalFrom(narrativeOrchestratorInstance));
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

    @Produces @ApplicationScoped
    public SocialNormDetector socialNormDetector(
            CbrRecordStore cbrStore, NormDetectionConfig config) {
        return new SocialNormDetector(cbrStore, config);
    }

    @Produces @ApplicationScoped
    public SocialAvatarCognition socialAvatarCognition(
            MoodOrchestrator mood, DriveOrchestrator drives,
            MentalModelOrchestrator mentalModel,
            UserModelOrchestrator userModel,
            StrategyLearningOrchestrator strategy) {
        return new SocialAvatarCognition(
                mood, drives, mentalModel, userModel, strategy,
                optionalFrom(narrativeOrchestratorInstance),
                optionalFrom(goalProposalOrchestratorInstance),
                optionalFrom(innerLifeOrchestratorInstance),
                optionalFrom(agentRegistryInstance));
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

    @Produces @ApplicationScoped
    public NarrativePipeline narrativePipeline(
            NarrativeContentSummariser summariser, NarrativeConfig config,
            ReflectionQueryStore reflectionQueryStore, CbrNarrativeStore cbrStore) {
        return new NarrativePipeline(summariser, config, reflectionQueryStore, cbrStore);
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
