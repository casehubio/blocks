package io.casehub.blocks.agentic.pattern;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.routing.LlmSelectedRouting;
import io.casehub.blocks.agentic.termination.MaxIterationsTermination;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static io.casehub.blocks.agentic.pattern.Patterns.supervisor;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SupervisorBuilderTest {

    @Mock AgentProvider mockProvider;

    private final AgentRef agent = AgentRef.external((Object input) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "done")));

    @Test
    void buildsValidModelWithDefaults() {
        var model = supervisor()
                .agents(agent)
                .terminate(new MaxIterationsTermination<>(1))
                .build();

        assertThat(model.routing()).isNotNull();
        assertThat(model.decomposition()).isNotNull();
        assertThat(model.activation()).isNotNull();
        assertThat(model.aggregation()).isNotNull();
        assertThat(model.termination()).isInstanceOf(MaxIterationsTermination.class);
    }

    @Test
    void executesEndToEnd() {
        var result = supervisor()
                .agents(agent)
                .terminate(new MaxIterationsTermination<>(2))
                .execute("state").await().indefinitely();

        assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    }

    @Test
    void overridesRouting() {
        var model = supervisor()
                .agents(agent)
                .route(new FirstMatchRouting<>(c -> true))
                .terminate(new MaxIterationsTermination<>(1))
                .build();

        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
    }

    @Test
    void stateRendererThenRouteUsesCustomRoute() {
        var customRouting = new FirstMatchRouting<String>(c -> true);
        var model = Patterns.<String>supervisor(mockProvider)
                .stateRenderer(Object::toString)
                .route(customRouting)
                .agents(agent)
                .build();
        assertThat(model.routing()).isSameAs(customRouting);
    }

    @Test
    void routeThenStateRendererUsesLlmRouting() {
        var customRouting = new FirstMatchRouting<String>(c -> true);
        var model = Patterns.<String>supervisor(mockProvider)
                .route(customRouting)
                .stateRenderer(Object::toString)
                .agents(agent)
                .build();
        assertThat(model.routing()).isInstanceOf(LlmSelectedRouting.class);
    }
}
