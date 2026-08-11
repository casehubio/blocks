package io.casehub.blocks.agentic.aggregation;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.worker.api.Worker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PassThroughTest {

    @Test
    void returnsSingleResultOutput() {
        var result = AgentResult.success(AgentRef.worker(mock(Worker.class)), "output");
        var agg = new PassThrough<String>();
        var ctx = new AggregationContext<>("state");

        var aggregated = agg.aggregate(List.of(result), ctx).await().indefinitely();
        assertThat(aggregated).isInstanceOf(AggregationResult.Resolved.class);
        assertThat(((AggregationResult.Resolved) aggregated).value()).isEqualTo("output");
    }

    @Test
    void returnsNullForEmptyResults() {
        var agg = new PassThrough<String>();
        var aggregated = agg.aggregate(List.of(), new AggregationContext<>("state"))
                .await().indefinitely();
        assertThat(aggregated).isInstanceOf(AggregationResult.Resolved.class);
        assertThat(((AggregationResult.Resolved) aggregated).value()).isNull();
    }
}
