package io.casehub.blocks.summarisation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class KeyedAccumulator<K, E> {

    private final Function<E, K> keyExtractor;
    private final Predicate<List<LevelEvent<E>>> completionTest;
    private final long staleTimeout;
    private final Map<K, List<LevelEvent<E>>> groups = new LinkedHashMap<>();
    private final Map<K, Long> lastEventTime = new LinkedHashMap<>();

    public KeyedAccumulator(Function<E, K> keyExtractor,
                            Predicate<List<LevelEvent<E>>> completionTest,
                            long staleTimeout) {
        this.keyExtractor = keyExtractor;
        this.completionTest = completionTest;
        this.staleTimeout = staleTimeout;
    }

    public synchronized void collect(LevelEvent<E> event) {
        K key = Objects.requireNonNull(keyExtractor.apply(event.payload()),
            "keyExtractor returned null");
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        lastEventTime.put(key, event.timestamp());
    }

    public synchronized List<List<LevelEvent<E>>> drain(long now) {
        if (groups.isEmpty()) return List.of();
        List<List<LevelEvent<E>>> result = new ArrayList<>();
        var it = groups.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            K key = entry.getKey();
            List<LevelEvent<E>> group = entry.getValue();
            boolean completed = completionTest.test(group);
            boolean stale = staleTimeout > 0
                && (now - lastEventTime.get(key)) >= staleTimeout;
            if (completed || stale) {
                result.add(List.copyOf(group));
                it.remove();
                lastEventTime.remove(key);
            }
        }
        return result;
    }

    public synchronized void clear() {
        groups.clear();
        lastEventTime.clear();
    }

    public synchronized int groupCount() {
        return groups.size();
    }

    public synchronized int eventCount() {
        return groups.values().stream().mapToInt(List::size).sum();
    }
}
