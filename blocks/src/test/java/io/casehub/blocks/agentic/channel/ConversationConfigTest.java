package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.conversation.orchestration.PromptAssembler;
import io.casehub.blocks.conversation.orchestration.ResponseMessageBuilder;
import io.casehub.qhorus.api.message.MessageView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ConversationConfigTest {

    @Test
    void rejectsNullPromptAssembler() {
        assertThatThrownBy(() -> new ConversationConfig<String>(
                null, mock(ResponseMessageBuilder.class), s -> mock(MessageView.class),
                null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("promptAssembler");
    }

    @Test
    void rejectsNullResponseBuilder() {
        assertThatThrownBy(() -> new ConversationConfig<String>(
                mock(PromptAssembler.class), null, s -> mock(MessageView.class),
                null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("responseBuilder");
    }

    @Test
    void rejectsNullTriggerMapper() {
        assertThatThrownBy(() -> new ConversationConfig<String>(
                mock(PromptAssembler.class), mock(ResponseMessageBuilder.class),
                null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("triggerMapper");
    }

    @Test
    void optionalFieldsAcceptNull() {
        var config = new ConversationConfig<String>(
                mock(PromptAssembler.class), mock(ResponseMessageBuilder.class),
                s -> mock(MessageView.class), null, null, null, null, null);
        assertThat(config.projectionFactory()).isNull();
        assertThat(config.conversationInvoker()).isNull();
        assertThat(config.participantMapper()).isNull();
        assertThat(config.turnPolicyOverride()).isNull();
        assertThat(config.terminationOverride()).isNull();
    }

    @Test
    void requiredFieldsAccessible() {
        var assembler = mock(PromptAssembler.class);
        var builder = mock(ResponseMessageBuilder.class);
        java.util.function.Function<String, MessageView> mapper = s -> mock(MessageView.class);
        var config = new ConversationConfig<>(assembler, builder, mapper,
                null, null, null, null, null);
        assertThat(config.promptAssembler()).isSameAs(assembler);
        assertThat(config.responseBuilder()).isSameAs(builder);
        assertThat(config.triggerMapper()).isSameAs(mapper);
    }
}
