package io.casehub.blocks.summarisation.cloudevents;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.cloudevents.CloudEvent;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

public class CloudEventIngestionAdapter<E> {

    private final EventStreamBus<E> outputBus;
    private final EventLevel level;
    private final String typePrefix;
    private final Function<byte[], E> deserializer;
    private final Function<E, E> normalizer;

    public CloudEventIngestionAdapter(EventStreamBus<E> outputBus,
                                      EventLevel level,
                                      String typePrefix,
                                      Function<byte[], E> deserializer) {
        this(outputBus, level, typePrefix, deserializer, Function.identity());
    }

    public CloudEventIngestionAdapter(EventStreamBus<E> outputBus,
                                      EventLevel level,
                                      String typePrefix,
                                      Function<byte[], E> deserializer,
                                      Function<E, E> normalizer) {
        this.outputBus = Objects.requireNonNull(outputBus);
        this.level = Objects.requireNonNull(level);
        this.typePrefix = Objects.requireNonNull(typePrefix);
        this.deserializer = Objects.requireNonNull(deserializer);
        this.normalizer = Objects.requireNonNull(normalizer);
    }

    public void accept(CloudEvent ce) {
        if (!ce.getType().startsWith(typePrefix)) {
            return;
        }
        var data = ce.getData();
        if (data == null) {
            return;
        }
        E payload = normalizer.apply(deserializer.apply(data.toBytes()));
        long timestamp = ce.getTime() != null
                ? ce.getTime().toInstant().toEpochMilli()
                : System.currentTimeMillis();
        @Nullable String tenancyId = ce.getExtension("tenancyid") instanceof String s ? s : null;
        outputBus.publish(new LevelEvent<>(payload, timestamp, level, tenancyId));
    }
}
