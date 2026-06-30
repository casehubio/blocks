package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.worker.api.Worker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CollectAllTest {

    @Test
    void collectsAllResultsIntoList() {
        var r1 = AgentResult.success(AgentRef.worker(mock(Worker.class)), "a");
        var r2 = AgentResult.success(AgentRef.worker(mock(Worker.class)), "b");
        var agg = new CollectAll<String>();

        var aggregated = agg.aggregate(List.of(r1, r2), new AggregationContext<>("state"))
                .await().indefinitely();

        assertThat(aggregated).isInstanceOf(AggregationResult.Resolved.class);
        @SuppressWarnings("unchecked")
        var collected = (List<AgentResult>) ((AggregationResult.Resolved) aggregated).value();
        assertThat(collected).hasSize(2);
    }
}
