package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.worker.api.Worker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MajorityVoteTest {

    @Test
    void selectsMostCommonOutput() {
        var r1 = AgentResult.success(AgentRef.worker(mock(Worker.class)), "yes");
        var r2 = AgentResult.success(AgentRef.worker(mock(Worker.class)), "no");
        var r3 = AgentResult.success(AgentRef.worker(mock(Worker.class)), "yes");
        var agg = new MajorityVote<String>();

        var aggregated = agg.aggregate(List.of(r1, r2, r3), new AggregationContext<>("state"))
                .await().indefinitely();

        assertThat(aggregated).isInstanceOf(AggregationResult.Resolved.class);
        assertThat(((AggregationResult.Resolved) aggregated).value()).isEqualTo("yes");
    }

    @Test
    void deadlocksOnTie() {
        var r1 = AgentResult.success(AgentRef.worker(mock(Worker.class)), "yes");
        var r2 = AgentResult.success(AgentRef.worker(mock(Worker.class)), "no");
        var agg = new MajorityVote<String>();

        var aggregated = agg.aggregate(List.of(r1, r2), new AggregationContext<>("state"))
                .await().indefinitely();

        assertThat(aggregated).isInstanceOf(AggregationResult.Deadlocked.class);
    }
}
