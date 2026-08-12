package io.casehub.blocks.agentic.model;

import org.jspecify.annotations.Nullable;

/**
 * Event-driven execution driver with two modes of operation:
 *
 * <p><b>Legacy mode</b> (no EventSource): loops continuously like {@link OrchestratedDriver},
 * with cosmetic {@link ExecutionState.WaitingForEvent} transitions. Backward compatible.
 *
 * <p><b>Event-driven mode</b> (EventSource provided): dormant between iterations.
 * External events (channel messages, work item completions, timer ticks) wake the driver
 * via the event queue. An {@link EventConcurrencyPolicy} controls how queued events are
 * consumed between iterations — serialize (default), coalesce, or coalesce-by-source.
 *
 * <p>The internal loop blocks on a virtual thread. No reactive Uni chains — blocking is
 * free on virtual threads.
 */
public class ChoreographedDriver<T> extends AbstractExecutionDriver<T> {

    private final @Nullable EventSource                                           eventSource;
    private final           EventConcurrencyPolicy                                policy;
    private final           java.util.concurrent.LinkedBlockingQueue<DriverEvent> eventQueue =
            new java.util.concurrent.LinkedBlockingQueue<>();

    public ChoreographedDriver() {
        super();
        this.eventSource = null;
        this.policy      = EventConcurrencyPolicy.serialize();
    }

    public ChoreographedDriver(AgentInvoker<T> invoker) {
        super(invoker);
        this.eventSource = null;
        this.policy      = EventConcurrencyPolicy.serialize();
    }

    public ChoreographedDriver(AgentInvoker<T> invoker,
                               EventConcurrencyPolicy policy,
                               EventSource... sources) {
        super(invoker);
        this.policy      = policy;
        this.eventSource = sources.length == 0
                           ? null
                           : sources.length == 1 ? sources[0] : EventSource.merge(sources);
    }

    public void signal(DriverEvent event) {
        eventQueue.add(event);
    }

    public void signal(String source) {
        eventQueue.add(DriverEvent.signal(source));
    }

    @Override
    public void cancel() {
        eventQueue.add(DriverEvent.signal("cancelled"));
        super.cancel();
    }

    @Override
    protected ExecutionResult runLoop(ExecutionModel<T> model, T context) {
        EventSource.Cancellation subscription = null;
        try {
            if (eventSource != null) {
                subscription = eventSource.subscribe(eventQueue::add);
            }

            var start      = java.time.Instant.now();
            var allResults = new java.util.ArrayList<io.casehub.blocks.agentic.AgentResult>();
            int iteration  = 0;

            while (!isCancelled()) {
                transition(model, new ExecutionState.WaitingForEvent());

                if (eventSource != null) {
                    policy.awaitEvents(eventQueue);
                }

                if (isCancelled()) {break;}

                transition(model, new ExecutionState.Running(iteration));
                var result = executeIteration(model, context, iteration, start, allResults);
                if (result != null) {return result;}

                iteration++;
            }

            transition(model, new ExecutionState.Cancelled());
            return new ExecutionResult.Cancelled();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            transition(model, new ExecutionState.Cancelled());
            return new ExecutionResult.Cancelled();
        } finally {
            if (subscription != null) {
                subscription.cancel();
            }
        }
    }
}
