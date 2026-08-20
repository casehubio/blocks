package io.casehub.blocks.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.SupersessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultIntegrityCheckerTest {

    private CbrCaseMemoryStore store;
    private SemanticIntegrityChecker semanticChecker;
    private DefaultIntegrityChecker checker;
    private static final MemoryDomain DOMAIN = new MemoryDomain("agent");

    @BeforeEach
    void setUp() {
        store = mock(CbrCaseMemoryStore.class);
        semanticChecker = mock(SemanticIntegrityChecker.class);
        when(semanticChecker.checkSemantic(any(), any(), any())).thenReturn(List.of());
        checker = new DefaultIntegrityChecker(store, semanticChecker, List.of("test-case"));
    }

    @Test
    void detectsOrphanedSupersession() {
        var status = new SupersessionStatus("case-1", true, Instant.now(),
                "missing-case", "consolidation", null);
        when(store.findSupersededCases("tenant-1", DOMAIN)).thenReturn(List.of(status));
        when(store.getSupersessionStatus("missing-case", "tenant-1"))
                .thenReturn(SupersessionStatus.NOT_SUPERSEDED);
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of());

        var violations = checker.check("agent-1", "tenant-1", DOMAIN);
        assertThat(violations).anyMatch(v -> v.type() == ViolationType.ORPHANED_SUPERSESSION);
    }

    @Test
    void noViolationsWhenSupersessionChainIsValid() {
        var status = new SupersessionStatus("case-1", true, Instant.now(),
                "case-2", "consolidation", null);
        when(store.findSupersededCases("tenant-1", DOMAIN)).thenReturn(List.of(status));
        when(store.getSupersessionStatus("case-2", "tenant-1"))
                .thenReturn(new SupersessionStatus("case-2", false, null, null, null, null));
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of());

        var violations = checker.check("agent-1", "tenant-1", DOMAIN);
        assertThat(violations).noneMatch(v -> v.type() == ViolationType.ORPHANED_SUPERSESSION);
    }

    @Test
    void returnsEmptyWhenNoIssues() {
        when(store.findSupersededCases("tenant-1", DOMAIN)).thenReturn(List.of());
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of());

        var violations = checker.check("agent-1", "tenant-1", DOMAIN);
        assertThat(violations).isEmpty();
    }

    @Test
    void delegatesToSemanticCheckerForFlaggedItems() {
        var status = new SupersessionStatus("case-1", true, Instant.now(),
                "missing-case", "consolidation", null);
        when(store.findSupersededCases("tenant-1", DOMAIN)).thenReturn(List.of(status));
        when(store.getSupersessionStatus("missing-case", "tenant-1"))
                .thenReturn(SupersessionStatus.NOT_SUPERSEDED);
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of());

        var semanticViolation = new IntegrityViolation("case-1",
                ViolationType.SEMANTIC_CONFLICT, "contradictory", false);
        when(semanticChecker.checkSemantic(any(), any(), any()))
                .thenReturn(List.of(semanticViolation));

        var violations = checker.check("agent-1", "tenant-1", DOMAIN);
        assertThat(violations).anyMatch(v -> v.type() == ViolationType.SEMANTIC_CONFLICT);
    }
}
