package io.casehub.engine.agentic;

import io.casehub.actorstate.ActorStateAggregator;
import io.casehub.api.engine.LoopControl;
import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.api.spi.CaseChannelProvider;
import io.casehub.api.spi.DispatchBudget;
import io.casehub.api.spi.FailureClassifier;
import io.casehub.api.spi.RiskDecision;
import io.casehub.api.spi.WorkerContextProvider;
import io.casehub.api.spi.WorkerProvisioner;
import io.casehub.api.spi.WorkerStatusListener;
import io.casehub.api.spi.recovery.ErrorClassifier;
import io.casehub.api.spi.routing.AgentRoutingStrategy;
import io.casehub.api.spi.routing.RoutingResult;
import io.casehub.api.spi.routing.WorkloadDataProvider;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.CapabilityHealth;
import io.casehub.eidos.api.VocabularyRegistry;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.CaseMetaModelRepository;
import io.casehub.engine.common.spi.CrossTenantCaseInstanceRepository;
import io.casehub.engine.common.spi.CrossTenantEventLogRepository;
import io.casehub.engine.common.spi.EventLogRepository;
import io.casehub.engine.common.spi.GoalDecomposer;
import io.casehub.engine.common.spi.PlanAdaptationEvaluator;
import io.casehub.engine.common.spi.PlanItemStore;
import io.casehub.engine.common.spi.scheduler.WorkerExecutionRoutingStrategy;
import io.casehub.engine.internal.routing.NoOpWorkloadDataProvider;
import io.casehub.engine.internal.worker.DefaultErrorClassifier;
import io.casehub.engine.internal.worker.DefaultFailureClassifier;
import io.casehub.engine.internal.worker.EmptyWorkerContextProvider;
import io.casehub.engine.internal.worker.NoOpAgentRegistry;
import io.casehub.engine.internal.worker.NoOpCapabilityHealth;
import io.casehub.engine.internal.worker.NoOpCaseChannelProvider;
import io.casehub.engine.internal.worker.NoOpDispatchBudget;
import io.casehub.engine.internal.worker.NoOpGoalDecomposer;
import io.casehub.engine.internal.worker.NoOpPlanAdaptationEvaluator;
import io.casehub.engine.internal.worker.NoOpPlanItemStore;
import io.casehub.engine.internal.worker.NoOpVocabularyRegistry;
import io.casehub.api.spi.ProvisionResult;
import io.casehub.engine.internal.worker.NoOpWorkerStatusListener;
import io.casehub.engine.internal.context.InMemoryCaseContextStoreFactory;
import io.casehub.persistence.memory.InMemoryCaseInstanceRepository;
import io.casehub.persistence.memory.InMemoryCaseMetaModelRepository;
import io.casehub.persistence.memory.InMemoryEventLogRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class IntegrationTestBeans {

  @Produces
  @DefaultBean
  @Singleton
  InMemoryEventLogRepository eventLogRepository() {
    return new InMemoryEventLogRepository();
  }

  @Produces
  @DefaultBean
  @Singleton
  @io.casehub.engine.common.qualifier.CrossTenant
  CrossTenantEventLogRepository crossTenantEventLogRepository(InMemoryEventLogRepository shared) {
    return shared;
  }

  @Produces
  @DefaultBean
  @Singleton
  InMemoryCaseInstanceRepository caseInstanceRepository(InMemoryEventLogRepository eventLogRepository) {
    return new InMemoryCaseInstanceRepository(eventLogRepository);
  }

  @Produces
  @DefaultBean
  @Singleton
  @io.casehub.engine.common.qualifier.CrossTenant
  CrossTenantCaseInstanceRepository crossTenantCaseInstanceRepository(InMemoryCaseInstanceRepository shared) {
    return shared;
  }

  @Produces
  @DefaultBean
  @Singleton
  CaseMetaModelRepository caseMetaModelRepository() {
    return new InMemoryCaseMetaModelRepository();
  }

  @Produces
  @DefaultBean
  @Singleton
  PlanItemStore planItemStore() {
    return new NoOpPlanItemStore();
  }

  @Produces
  @DefaultBean
  CaseChannelProvider caseChannelProvider() {
    return new NoOpCaseChannelProvider();
  }

  @Produces
  @DefaultBean
  DispatchBudget dispatchBudget() {
    return new NoOpDispatchBudget();
  }

  @Produces
  @DefaultBean
  FailureClassifier failureClassifier() {
    return new DefaultFailureClassifier();
  }

  @Produces
  @DefaultBean
  ErrorClassifier errorClassifier() {
    return new DefaultErrorClassifier();
  }

  @Produces
  @DefaultBean
  WorkloadDataProvider workloadDataProvider() {
    return new NoOpWorkloadDataProvider();
  }

  @Produces
  @DefaultBean
  WorkerContextProvider workerContextProvider(
      CaseChannelProvider caseChannelProvider,
      io.casehub.platform.api.identity.CurrentPrincipal currentPrincipal) {
    return new EmptyWorkerContextProvider(caseChannelProvider, currentPrincipal);
  }

  @Produces
  @DefaultBean
  WorkerProvisioner workerProvisioner() {
    return new WorkerProvisioner() {
      @Override
      public ProvisionResult provision(java.util.Set<String> capabilities,
          io.casehub.api.model.ProvisionContext context) {
        return ProvisionResult.empty();
      }

      @Override
      public void terminate(String workerId, String tenancyId) {}

      @Override
      public java.util.Set<String> getCapabilities() {
        return java.util.Set.of();
      }
    };
  }

  @Produces
  @DefaultBean
  WorkerStatusListener workerStatusListener() {
    return new NoOpWorkerStatusListener();
  }

  @Produces
  @DefaultBean
  AgentRegistry agentRegistry() {
    return new NoOpAgentRegistry();
  }

  @Produces
  @DefaultBean
  CapabilityHealth capabilityHealth() {
    return new NoOpCapabilityHealth();
  }

  @Produces
  @DefaultBean
  VocabularyRegistry vocabularyRegistry() {
    return new NoOpVocabularyRegistry();
  }

  @Produces
  @DefaultBean
  GoalDecomposer goalDecomposer() {
    return new NoOpGoalDecomposer();
  }

  @Produces
  @DefaultBean
  PlanAdaptationEvaluator planAdaptationEvaluator() {
    return new NoOpPlanAdaptationEvaluator();
  }

  @Produces
  @DefaultBean
  ActorStateAggregator actorStateAggregator() {
    return new ActorStateAggregator(List.of(), java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
  }

  @Produces
  @DefaultBean
  LoopControl loopControl() {
    return (context, eligible) -> eligible;
  }

  @Produces
  @DefaultBean
  ActionRiskClassifier actionRiskClassifier() {
    return (action, context) -> new RiskDecision.Autonomous();
  }

  @Produces
  @DefaultBean
  AgentRoutingStrategy agentRoutingStrategy() {
    return new AgentRoutingStrategy() {
      @Override
      public String id() {
        return "first-candidate";
      }

      @Override
      public RoutingResult select(
          io.casehub.api.spi.routing.AgentRoutingContext context,
          List<io.casehub.api.spi.routing.AgentCandidate> candidates) {
        if (candidates.isEmpty()) {
          return RoutingResult.unresolvable("no candidates");
        }
        return RoutingResult.assigned(candidates.get(0).workerId(), "test: first candidate");
      }
    };
  }

  @Produces
  @DefaultBean
  WorkerExecutionRoutingStrategy workerExecutionRoutingStrategy() {
    return new WorkerExecutionRoutingStrategy() {
      @Override
      public String id() {
        return "noop";
      }

      @Override
      public Optional<io.casehub.engine.common.spi.scheduler.WorkerExecutionManager> select(
          List<io.casehub.engine.common.spi.scheduler.WorkerExecutionManager> candidates,
          io.casehub.worker.api.Worker worker,
          io.casehub.worker.api.Capability capability,
          String tenancyId) {
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
      }
    };
  }

  @Produces
  @DefaultBean
  io.casehub.api.context.CaseContextStoreFactory caseContextStoreFactory() {
    return InMemoryCaseContextStoreFactory.INSTANCE;
  }

}
