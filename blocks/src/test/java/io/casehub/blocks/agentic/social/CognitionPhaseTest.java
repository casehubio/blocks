package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionPhaseTest {

    @Test
    void phasesAreOrderedByDeclaration() {
        var phases = CognitionPhase.values();
        assertThat(phases).containsExactly(
                CognitionPhase.FOUNDATION,
                CognitionPhase.SOURCE,
                CognitionPhase.SOURCE_PER_SUBJECT,
                CognitionPhase.DERIVED,
                CognitionPhase.TERMINAL);
    }

    @Test
    void tickContextCarriesAllFields() {
        SubjectResolver resolver = (a, t) -> Set.of();
        var ctx = new CognitionTickContext("agent-1", "tenant-1", null, resolver);
        assertThat(ctx.agentId()).isEqualTo("agent-1");
        assertThat(ctx.tenantId()).isEqualTo("tenant-1");
        assertThat(ctx.descriptor()).isNull();
        assertThat(ctx.resolver()).isSameAs(resolver);
    }
}
