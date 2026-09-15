package io.casehub.blocks.agentic.belief;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BeliefSet<T> {

    private final Map<String, Belief<T>> beliefs;

    public BeliefSet() {
        this.beliefs = Map.of();
    }

    private BeliefSet(Map<String, Belief<T>> beliefs) {
        this.beliefs = Collections.unmodifiableMap(new LinkedHashMap<>(beliefs));
    }

    public @Nullable Belief<T> get(String key) {
        return beliefs.get(key);
    }

    public boolean contains(String key) {
        return beliefs.containsKey(key);
    }

    public List<Belief<T>> all() {
        return List.copyOf(beliefs.values());
    }

    public int size() {
        return beliefs.size();
    }

    public boolean isEmpty() {
        return beliefs.isEmpty();
    }

    public BeliefSet<T> expand(Belief<T> belief) {
        var updated = new LinkedHashMap<>(beliefs);
        updated.put(belief.key(), belief);
        return new BeliefSet<>(updated);
    }

    public BeliefSet<T> contract(String key) {
        if (!beliefs.containsKey(key)) return this;
        var updated = new LinkedHashMap<>(beliefs);
        updated.remove(key);
        return new BeliefSet<>(updated);
    }

    public BeliefSet<T> revise(Belief<T> belief, ConsistencyChecker<T> checker) {
        Objects.requireNonNull(checker);
        var candidate = expand(belief);
        if (checker.isConsistent(candidate)) return candidate;

        var sorted = new java.util.ArrayList<>(beliefs.values().stream()
                .filter(b -> !b.key().equals(belief.key()))
                .sorted(java.util.Comparator.comparingInt(Belief::entrenchment))
                .toList());

        var revised = new LinkedHashMap<String, Belief<T>>();
        revised.put(belief.key(), belief);

        for (int i = sorted.size() - 1; i >= 0; i--) {
            revised.put(sorted.get(i).key(), sorted.get(i));
            var test = new BeliefSet<>(revised);
            if (!checker.isConsistent(test)) {
                revised.remove(sorted.get(i).key());
            }
        }

        return new BeliefSet<>(revised);
    }
}
