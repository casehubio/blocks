package io.casehub.blocks.agentic.personality;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.CapabilityHealth.ProbeContext;
import io.casehub.eidos.api.DispositionEvolution;
import io.casehub.eidos.api.DispositionEvolution.EvolutionResult;
import io.casehub.eidos.api.DispositionHealth;
import io.casehub.eidos.api.DispositionHealth.DispositionStatus;
import io.casehub.eidos.api.DispositionProfileStore;
import io.casehub.eidos.api.DispositionSignalStore;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.PersonalityTransitionSchema;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class PersonalityEvolutionOrchestrator {

    private final DispositionSignalStore signalStore;
    private final DispositionHealth health;
    private final DispositionEvolution evolution;
    private final DispositionProfileStore profileStore;
    private final CbrCaseMemoryStore cbrStore;
    private final List<TraitPressureSource<?>> pressureSources;
    private final PersonalityEvolutionConfig config;

    private final ConcurrentHashMap<String, AtomicBoolean> haltFlags = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    @Inject
    public PersonalityEvolutionOrchestrator(
            final DispositionSignalStore signalStore,
            final DispositionHealth health,
            final DispositionEvolution evolution,
            final DispositionProfileStore profileStore,
            final CbrCaseMemoryStore cbrStore,
            final Instance<TraitPressureSource<?>> pressureSources,
            final PersonalityEvolutionConfig config) {
        this.signalStore = signalStore;
        this.health = health;
        this.evolution = evolution;
        this.profileStore = profileStore;
        this.cbrStore = cbrStore;
        this.pressureSources = pressureSources.stream().toList();
        this.config = config;
    }

    @SuppressWarnings("unchecked")
    public <E> void record(final E event, final AgentDescriptor descriptor) {
        var agentKey = agentKey(descriptor);
        if (haltFlags.computeIfAbsent(agentKey, k -> new AtomicBoolean()).get()) {
            return;
        }
        var profile = descriptor.disposition().dispositionProfile();
        if (profile == null || profile.isEmpty()) {
            return;
        }
        var profileTerms = profile.stream().map(DispositionValue::term).toList();

        for (var source : pressureSources) {
            if (source.eventType().isInstance(event)) {
                var typedSource = (TraitPressureSource<E>) source;
                var activations = typedSource.translate(event, descriptor);
                if (activations != null) {
                    for (var activation : activations) {
                        if (profileTerms.contains(activation.functionTerm())) {
                            signalStore.recordActivation(
                                    descriptor.agentId(), descriptor.tenancyId(),
                                    activation.functionTerm(), activation.valence());
                        }
                    }
                }
                return;
            }
        }
    }

    public EvolutionTick tick(final AgentDescriptor descriptor, final ProbeContext probeContext) {
        var agentKey = agentKey(descriptor);
        var lock = tickLocks.computeIfAbsent(agentKey, k -> new ReentrantLock());
        lock.lock();
        try {
            return doTick(descriptor, probeContext, agentKey);
        } finally {
            lock.unlock();
        }
    }

    private EvolutionTick doTick(final AgentDescriptor descriptor,
                                  final ProbeContext probeContext,
                                  final String agentKey) {
        signalStore.decay(descriptor.agentId(), descriptor.tenancyId(), config.decayFactor());

        var status = health.probe(descriptor, probeContext);

        return switch (status) {
            case DispositionStatus.Aligned a -> {
                haltFlags.computeIfAbsent(agentKey, k -> new AtomicBoolean()).set(false);
                yield new EvolutionTick.Stable();
            }
            case DispositionStatus.Drifted d -> {
                if (d.driftMagnitude() >= config.l2Ceiling()) {
                    haltFlags.computeIfAbsent(agentKey, k -> new AtomicBoolean()).set(true);
                    yield new EvolutionTick.Halted(d.driftMagnitude());
                } else {
                    haltFlags.computeIfAbsent(agentKey, k -> new AtomicBoolean()).set(false);
                    yield new EvolutionTick.Drifting(d.driftMagnitude());
                }
            }
            case DispositionStatus.EvolutionPending p -> {
                var result = evolution.evaluate(descriptor, p);
                yield switch (result) {
                    case EvolutionResult.Evolved e -> {
                        profileStore.update(descriptor.agentId(), descriptor.tenancyId(), e.newProfile());
                        recordTransitionCase(descriptor, e, p.type().name());
                        signalStore.clear(descriptor.agentId(), descriptor.tenancyId());
                        haltFlags.computeIfAbsent(agentKey, k -> new AtomicBoolean()).set(false);
                        yield new EvolutionTick.Evolved(e.previousTypeLabel(), e.newTypeLabel(), e.newProfile());
                    }
                    case EvolutionResult.Dampened d -> {
                        signalStore.decay(descriptor.agentId(), descriptor.tenancyId(), d.decayFactor());
                        haltFlags.computeIfAbsent(agentKey, k -> new AtomicBoolean()).set(true);
                        yield new EvolutionTick.Dampened(d.decayFactor());
                    }
                };
            }
        };
    }

    private void recordTransitionCase(final AgentDescriptor descriptor,
                                       final EvolutionResult.Evolved evolved,
                                       final String triggerType) {
        var oldProfile = descriptor.disposition().dispositionProfile();
        var oldSorted = oldProfile != null
                        ? oldProfile.stream().sorted(Comparator.comparingDouble(DispositionValue::weight).reversed()).toList()
                        : List.<DispositionValue>of();
        var newSorted = evolved.newProfile().stream()
                               .sorted(Comparator.comparingDouble(DispositionValue::weight).reversed()).toList();

        var features = Map.<String, FeatureValue>of(
                "agent_id", FeatureValue.string(descriptor.agentId()),
                "old_dominant", FeatureValue.string(oldSorted.isEmpty() ? "unknown" : oldSorted.get(0).term()),
                "new_dominant", FeatureValue.string(newSorted.isEmpty() ? "unknown" : newSorted.get(0).term()),
                "old_auxiliary", FeatureValue.string(oldSorted.size() < 2 ? "unknown" : oldSorted.get(1).term()),
                "new_auxiliary", FeatureValue.string(newSorted.size() < 2 ? "unknown" : newSorted.get(1).term()),
                "trigger_type", FeatureValue.string(triggerType));
        var transitionCase = new FeatureVectorCbrCase(
                "personality-transition: " + evolved.previousTypeLabel() + " -> " + evolved.newTypeLabel(),
                triggerType, null, null, features, null, descriptor.agentId());
        cbrStore.store(transitionCase, PersonalityTransitionSchema.CASE_TYPE,
                       descriptor.agentId(), new MemoryDomain("agent"), descriptor.tenancyId(), null, null);
    }

    private static String agentKey(final AgentDescriptor descriptor) {
        return descriptor.tenancyId() + ":" + descriptor.agentId();
    }
}
