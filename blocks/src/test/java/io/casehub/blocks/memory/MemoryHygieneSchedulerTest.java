package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MemoryHygieneSchedulerTest {

    private MemoryHygieneOrchestrator orchestrator;
    private ReflectionOrchestrator reflectionOrchestrator;
    private ReflectionStore reflectionStore;
    private IntegrityChecker integrityChecker;
    private CbrCaseMemoryStore store;
    @SuppressWarnings("unchecked")
    private Consumer<HygieneEvent> eventSink = mock(Consumer.class);
    private MemoryHygieneScheduler scheduler;

    private static final MemoryDomain DOMAIN = new MemoryDomain("agent");

    @BeforeEach
    void setUp() {
        orchestrator = mock(MemoryHygieneOrchestrator.class);
        reflectionOrchestrator = mock(ReflectionOrchestrator.class);
        reflectionStore = mock(ReflectionStore.class);
        integrityChecker = mock(IntegrityChecker.class);
        store = mock(CbrCaseMemoryStore.class);
        scheduler = new MemoryHygieneScheduler(
                orchestrator, reflectionOrchestrator, reflectionStore,
                integrityChecker, store, DOMAIN, List.of("test-case"),
                50, 0.7, eventSink);
    }

    @Test
    void maintainComposesAllStages() {
        var tick = new HygieneTick.Completed(0, 1, 5, List.of());
        when(orchestrator.tick("agent-1", "tenant-1")).thenReturn(tick);
        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenReturn(List.of("insight one"));
        when(integrityChecker.check(anyString(), anyString(), any(MemoryDomain.class)))
                .thenReturn(List.of());
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of());

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Completed.class);
        var completed = (MaintenanceTick.Completed) result;
        assertThat(completed.reflectionsGenerated()).isEqualTo(1);
        assertThat(completed.hygiene()).isEqualTo(tick);
        verify(reflectionStore).store(any(ReflectionEntry.class));
        verify(eventSink).accept(any(HygieneEvent.ReflectionGenerated.class));
    }

    @Test
    void maintainReturnsFailedOnReflectionError() {
        when(orchestrator.tick(anyString(), anyString()))
                .thenReturn(new HygieneTick.Completed(0, 0, 0, List.of()));
        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("reflection failed"));

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Failed.class);
        assertThat(((MaintenanceTick.Failed) result).stage()).isEqualTo("reflection");
    }

    @Test
    void maintainPropagatesTickFailure() {
        when(orchestrator.tick(anyString(), anyString()))
                .thenReturn(new HygieneTick.Failed("store down"));

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Failed.class);
        assertThat(((MaintenanceTick.Failed) result).stage()).isEqualTo("tick");
    }

    @Test
    void maintainHandlesIdleTick() {
        when(orchestrator.tick(anyString(), anyString()))
                .thenReturn(new HygieneTick.Idle("no memories"));
        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenReturn(List.of());
        when(integrityChecker.check(anyString(), anyString(), any(MemoryDomain.class)))
                .thenReturn(List.of());
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of());

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Completed.class);
    }

    @Test
    void maintainReportsIntegrityViolations() {
        when(orchestrator.tick(anyString(), anyString()))
                .thenReturn(new HygieneTick.Completed(0, 0, 0, List.of()));
        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenReturn(List.of());
        var violation = new IntegrityViolation("c1", ViolationType.ORPHANED_SUPERSESSION,
                "orphaned", false);
        when(integrityChecker.check(anyString(), anyString(), any(MemoryDomain.class)))
                .thenReturn(List.of(violation));
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of());

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Completed.class);
        var completed = (MaintenanceTick.Completed) result;
        assertThat(completed.violations()).hasSize(1);
        verify(eventSink).accept(any(HygieneEvent.IntegrityViolationDetected.class));
    }
}
