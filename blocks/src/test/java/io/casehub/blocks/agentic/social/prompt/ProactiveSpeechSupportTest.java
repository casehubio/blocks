package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.InnerLifeOrchestrator;
import io.casehub.blocks.agentic.social.InnerLifeTick;
import io.casehub.eidos.api.AgentDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProactiveSpeechSupportTest {

    @Test
    void returnsContentOnInitiated() {
        var innerLife = mock(InnerLifeOrchestrator.class);
        var descriptor = mock(AgentDescriptor.class);
        when(innerLife.tick(descriptor, "context"))
                .thenReturn(new InnerLifeTick.Initiated("Hello there!", null, 0.8));
        var support = new ProactiveSpeechSupport(innerLife, descriptor);
        assertThat(support.evaluateProactive("context")).isEqualTo("Hello there!");
    }

    @Test
    void returnsNullOnSilent() {
        var innerLife = mock(InnerLifeOrchestrator.class);
        var descriptor = mock(AgentDescriptor.class);
        when(innerLife.tick(descriptor, "context"))
                .thenReturn(new InnerLifeTick.Silent(null));
        var support = new ProactiveSpeechSupport(innerLife, descriptor);
        assertThat(support.evaluateProactive("context")).isNull();
    }

    @Test
    void returnsNullOnSilentWithReason() {
        var innerLife = mock(InnerLifeOrchestrator.class);
        var descriptor = mock(AgentDescriptor.class);
        when(innerLife.tick(descriptor, "ctx"))
                .thenReturn(new InnerLifeTick.Silent("civility gate"));
        var support = new ProactiveSpeechSupport(innerLife, descriptor);
        assertThat(support.evaluateProactive("ctx")).isNull();
    }
}
