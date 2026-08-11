package io.casehub.blocks.agentic.routing;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class StageAwareCandidateSupplier implements Supplier<List<RoutingCandidate>> {

    private final Supplier<List<RoutingCandidate>> delegate;
    private final StageGate stageGate;
    private final Function<AgentRef, String> bindingNameResolver;

    public StageAwareCandidateSupplier(
            Supplier<List<RoutingCandidate>> delegate,
            StageGate stageGate,
            Function<AgentRef, String> bindingNameResolver) {
        this.delegate = delegate;
        this.stageGate = stageGate;
        this.bindingNameResolver = bindingNameResolver;
    }

    @Override
    public List<RoutingCandidate> get() {
        var candidates = delegate.get();
        var allStaged = stageGate.allStagedBindings();
        var activeStaged = stageGate.activeStagedBindings();

        return candidates.stream()
                .filter(c -> {
                    var name = bindingNameResolver.apply(c.ref());
                    return !allStaged.contains(name) || activeStaged.contains(name);
                })
                .toList();
    }
}
