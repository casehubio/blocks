package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.activation.OnExplicitDispatch;
import io.casehub.blocks.agentic.aggregation.CollectAll;
import io.casehub.blocks.agentic.decomposition.DecompositionContext;
import io.casehub.blocks.agentic.decomposition.StaticDecomposition;
import io.casehub.blocks.agentic.decomposition.TaskNode;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.OrchestratedDriver;
import io.casehub.blocks.agentic.routing.SequentialRouting;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

import java.util.ArrayList;
import java.util.List;

public class HtnBuilder<T> extends AbstractPatternBuilder<T, HtnBuilder<T>> {

    private TaskNode<T> rootTask;

    public HtnBuilder() {
        this.task = "htn";
        this.routing = new SequentialRouting<>();
        this.decomposition = new StaticDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new CollectAll<>();
        this.termination = ctx -> Uni.createFrom().item(
                ctx.iterationCount() >= 1
                        ? new TerminationDecision.Complete(ctx.results())
                        : TerminationDecision.Continue.INSTANCE);
    }

    public HtnBuilder<T> rootTask(TaskNode<T> rootTask) {
        this.rootTask = rootTask;
        return this;
    }

    @Override
    public Uni<ExecutionResult> execute(T initialContext) {
        if (rootTask == null) {
            throw new IllegalStateException("rootTask must be set before execute()");
        }

        // Pre-decompose task tree eagerly using initial state
        return flatten(rootTask, initialContext)
                .map(tasks -> {
                    // Extract agents from primitive tasks
                    var agents = tasks.stream()
                            .map(t -> new RoutingCandidate(t.agent(), null))
                            .toList();

                    // Create termination condition that allows enough iterations for all tasks
                    // SequentialRouting executes one agent per iteration
                    var localTermination = (TerminationCondition<T>) ctx -> Uni.createFrom().item(
                            ctx.iterationCount() >= agents.size()
                                    ? new TerminationDecision.Complete(ctx.results())
                                    : TerminationDecision.Continue.INSTANCE);

                    // Build LOCAL execution model (does not mutate builder fields)
                    var localModel = new ExecutionModel<>(
                            routing,
                            decomposition,
                            activation,
                            aggregation,
                            localTermination,
                            () -> agents,
                            failurePolicy,
                            listeners,
                            task
                    );

                    return localModel;
                })
                .flatMap(localModel -> new OrchestratedDriver<T>().execute(localModel, initialContext));
    }

    private Uni<List<TaskNode.LeafTask<T>>> flatten(TaskNode<T> node, T state) {
        return switch (node) {
            case TaskNode.LeafTask<T> leaf -> Uni.createFrom().item(List.of(leaf));

            case TaskNode.CompoundTask<T> compound -> {
                var matchingMethod = compound.methods().stream()
                        .filter(m -> m.guard().test(state))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "No decomposition method guard matched for task: " + compound.name()));

                var ctx = new DecompositionContext<>(state, List.of(), 0);
                yield matchingMethod.strategy()
                        .decompose(compound, ctx)
                        .flatMap(children -> {
                            var childFlattens = children.stream()
                                    .map(child -> flatten(child, state))
                                    .toList();

                            return Uni.combine().all().unis(childFlattens)
                                    .combinedWith(results -> {
                                        var flattened = new ArrayList<TaskNode.LeafTask<T>>();
                                        for (var result : results) {
                                            @SuppressWarnings("unchecked")
                                            var taskList = (List<TaskNode.LeafTask<T>>) result;
                                            flattened.addAll(taskList);
                                        }
                                        return flattened;
                                    });
                        });
            }
        };
    }
}
