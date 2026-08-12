package io.casehub.blocks.agentic.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@FunctionalInterface
public interface EventSource {

    Cancellation subscribe(Consumer<DriverEvent> sink);

    interface Cancellation {
        void cancel();

        static Cancellation of(Runnable action) {
            return action::run;
        }

        static Cancellation composite(List<Cancellation> cancellations) {
            return () -> cancellations.forEach(Cancellation::cancel);
        }
    }

    static EventSource merge(EventSource... sources) {
        return sink -> {
            var cancellations = new ArrayList<Cancellation>();
            try {
                for (var source : sources) {
                    cancellations.add(source.subscribe(sink));
                }
            } catch (Exception e) {
                cancellations.forEach(Cancellation::cancel);
                throw e;
            }
            return Cancellation.composite(cancellations);
        };
    }

    static EventSource ticker(Duration interval, ScheduledExecutorService executor) {
        return sink -> {
            var future = executor.scheduleAtFixedRate(
                () -> sink.accept(DriverEvent.timer()),
                interval.toMillis(), interval.toMillis(),
                TimeUnit.MILLISECONDS);
            return Cancellation.of(() -> future.cancel(false));
        };
    }
}
