package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.KeyedSummarisationRunner;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.platform.agent.AgentProvider;

public class DecisionNarrativePipeline {

    static final EventLevel L0_SIGNALS = new EventLevel("decision-signals", 0);
    static final EventLevel L1_STEPS = new EventLevel("step-summaries", 1);
    static final EventLevel L2_NARRATIVES = new EventLevel("decision-narratives", 2);

    private final EventStreamBus<DecisionSignal> signalBus;
    private final EventStreamBus<StepDecisionSummary> stepBus;
    private final EventStreamBus<DecisionNarrative> narrativeBus;
    private final KeyedSummarisationRunner<String, DecisionSignal, StepDecisionSummary> l1;
    private final KeyedSummarisationRunner<String, StepDecisionSummary, DecisionNarrative> l2;

    public DecisionNarrativePipeline(AgentProvider agentProvider) {
        this.signalBus = new EventStreamBus<>();
        this.stepBus = new EventStreamBus<>();
        this.narrativeBus = new EventStreamBus<>();

        this.l1 = new KeyedSummarisationRunner<>(
                e -> e.payload().caseId() + ":" + e.payload().stepName(),
                group -> group.stream().anyMatch(e -> e.payload() instanceof StepOutcome),
                30_000L,
                Summariser.ofSync(new DecisionSignalSummariser()),
                stepBus, L1_STEPS);

        this.l2 = new KeyedSummarisationRunner<>(
                e -> e.payload().caseId(),
                group -> group.size() >= 1,
                60_000L,
                new DecisionNarrativeSummariser(agentProvider).asSummariser(),
                narrativeBus, L2_NARRATIVES);

        signalBus.subscribe(e -> true, l1::collect);
        stepBus.subscribe(e -> true, l2::collect);
    }

    DecisionNarrativePipeline(EventStreamBus<DecisionSignal> signalBus,
                               EventStreamBus<StepDecisionSummary> stepBus,
                               EventStreamBus<DecisionNarrative> narrativeBus,
                               KeyedSummarisationRunner<String, DecisionSignal, StepDecisionSummary> l1,
                               KeyedSummarisationRunner<String, StepDecisionSummary, DecisionNarrative> l2) {
        this.signalBus = signalBus;
        this.stepBus = stepBus;
        this.narrativeBus = narrativeBus;
        this.l1 = l1;
        this.l2 = l2;
        signalBus.subscribe(e -> true, l1::collect);
        stepBus.subscribe(e -> true, l2::collect);
    }

    public EventStreamBus<DecisionSignal> signalBus() { return signalBus; }
    public EventStreamBus<DecisionNarrative> narrativeBus() { return narrativeBus; }

    public void tick(long now) {
        l1.tick(now);
        l2.tick(now);
    }

    public void evictCaseState(String caseId) {
        l2.evictState(caseId);
    }
}
