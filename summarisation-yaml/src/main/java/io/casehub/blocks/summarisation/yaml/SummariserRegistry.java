package io.casehub.blocks.summarisation.yaml;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SummariserRegistry {

    private final Map<String, SummariserFactory<?>> factories = new ConcurrentHashMap<>();

    public SummariserRegistry() {
        register("pass-through", config -> Summariser.ofSync(batch ->
                                                                     batch.stream().map(LevelEvent::payload).toList()));
        register("tiered", config -> createTiered(config));
    }

    public void register(String typeId, SummariserFactory<?> factory) {
        factories.put(typeId, factory);
    }

    @SuppressWarnings("unchecked")
    public <IN, OUT> Summariser<IN, OUT> create(String typeId, Map<String, Object> config) {
        var factory = factories.get(typeId);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown summariser type: " + typeId
                    + ". Registered types: " + factories.keySet());
        }
        return (Summariser<IN, OUT>) factory.create(config);
    }

    public boolean hasType(String typeId) {
        return factories.containsKey(typeId);
    }

    @SuppressWarnings("unchecked")
    private Summariser<Object, Object> createTiered(Map<String, Object> config) {
        if (!config.containsKey("smallThreshold")) {
            throw new IllegalArgumentException("tiered summariser requires 'smallThreshold'");
        }
        if (!config.containsKey("large")) {
            throw new IllegalArgumentException("tiered summariser requires 'large' delegate type");
        }

        final int smallThreshold = ((Number) config.get("smallThreshold")).intValue();
        final int mediumThreshold = config.containsKey("mediumThreshold")
                                    ? ((Number) config.get("mediumThreshold")).intValue() : smallThreshold;

        final String smallType  = (String) config.getOrDefault("small", "pass-through");
        final String mediumType = (String) config.getOrDefault("medium", (String) config.get("large"));
        final String largeType  = (String) config.get("large");

        final Summariser<Object, Object> small  = create(smallType, Map.of());
        final Summariser<Object, Object> medium = create(mediumType, Map.of());
        final Summariser<Object, Object> large  = create(largeType, Map.of());

        return batch -> {
            if (batch.size() <= smallThreshold) {return small.summarise(batch);}
            if (batch.size() <= mediumThreshold) {return medium.summarise(batch);}
            return large.summarise(batch);
        };
    }


}
