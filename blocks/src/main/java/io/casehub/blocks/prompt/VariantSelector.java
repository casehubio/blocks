package io.casehub.blocks.prompt;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class VariantSelector {

    private final double experimentRatio;
    private final int circuitBreakerThreshold;
    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();

    public VariantSelector(double experimentRatio, int circuitBreakerThreshold) {
        this.experimentRatio = experimentRatio;
        this.circuitBreakerThreshold = circuitBreakerThreshold;
    }

    public String selectSlot(UUID caseId, String capabilityName) {
        int hash = Objects.hash(caseId, capabilityName);
        int bucket = (hash & 0x7FFFFFFF) % 100;
        if (bucket >= (int) (experimentRatio * 100)) {
            return "control";
        }
        if (isCircuitOpen(capabilityName)) {
            return "control";
        }
        return "experiment";
    }

    public void recordOutcome(String capabilityName, boolean success) {
        if (success) {
            consecutiveFailures.remove(capabilityName);
        } else {
            consecutiveFailures
                    .computeIfAbsent(capabilityName, k -> new AtomicInteger(0))
                    .incrementAndGet();
        }
    }

    private boolean isCircuitOpen(String capabilityName) {
        var counter = consecutiveFailures.get(capabilityName);
        return counter != null && counter.get() >= circuitBreakerThreshold;
    }
}
