package io.casehub.blocks.summarisation.narrative;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import org.jspecify.annotations.Nullable;

public abstract class AbstractNarrativeSignalStrategy implements NarrativeSignalStrategy {

    private static final EventLevel SIGNAL_LEVEL = new EventLevel("decision-signal", 0);

    private final EventStreamBus<DecisionSignal> signalBus;
    private final DecisionNarrativePipeline pipeline;

    protected AbstractNarrativeSignalStrategy(EventStreamBus<DecisionSignal> signalBus,
                                               DecisionNarrativePipeline pipeline) {
        this.signalBus = signalBus;
        this.pipeline = pipeline;
    }

    protected void emit(DecisionSignal signal) {
        signalBus.publish(new LevelEvent<>(
                signal, signal.timestamp().toEpochMilli(),
                SIGNAL_LEVEL, extractTenancyId(signal)));
    }

    public void onCaseClose(String caseId) {
        pipeline.evictCaseState(caseId);
    }

    protected abstract @Nullable String extractTenancyId(DecisionSignal signal);
}
