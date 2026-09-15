package io.casehub.blocks.agentic.social.narrative;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.memory.ReflectionQueryStore;
import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.SummarisationRunner;

public class NarrativePipeline {

    static final EventLevel REFLECTIONS =
            new EventLevel("reflections", 0);
    static final EventLevel NARRATIVES =
            new EventLevel("narratives", 1);

    private final EventStreamBus<ReflectionEntry> reflectionBus;
    private final EventStreamBus<NarrativeState> narrativeBus;
    private final SummarisationRunner<ReflectionEntry, NarrativeState> runner;
    private final ReflectionEventAdapter adapter;

    public NarrativePipeline(
            NarrativeContentSummariser summariser,
            NarrativeConfig config,
            ReflectionQueryStore reflectionQueryStore,
            CbrNarrativeStore cbrStore) {
        this(summariser, config, reflectionQueryStore, cbrStore,
                new EventStreamBus<>(), new EventStreamBus<>());
    }

    NarrativePipeline(
            NarrativeContentSummariser summariser,
            NarrativeConfig config,
            ReflectionQueryStore reflectionQueryStore,
            CbrNarrativeStore cbrStore,
            EventStreamBus<ReflectionEntry> reflectionBus,
            EventStreamBus<NarrativeState> narrativeBus) {
        this.reflectionBus = reflectionBus;
        this.narrativeBus = narrativeBus;

        this.adapter = new ReflectionEventAdapter(
                reflectionQueryStore, reflectionBus, REFLECTIONS);

        this.runner = SummarisationRunner
                .<ReflectionEntry, NarrativeState>builder(
                        summariser.asSummariser(),
                        narrativeBus, NARRATIVES)
                .emissionPolicy(
                        new NarrativeEmissionPolicy(config.synthesisGate()))
                .stateStore(new CbrStateStore(cbrStore))
                .stateKeyResolver(batch ->
                        batch.get(0).payload().agentId() + ":"
                        + batch.get(0).tenancyId())
                .outputProcessor(
                        new NarrativeOutputProcessor(config))
                .build();

        reflectionBus.subscribe(e -> true, runner::collect);
    }

    public void tick(String agentId, String tenantId) {
        adapter.publishNewReflections(agentId, tenantId);
        runner.tick(System.currentTimeMillis());
    }

    public EventStreamBus<NarrativeState> narrativeBus() {
        return narrativeBus;
    }
}
