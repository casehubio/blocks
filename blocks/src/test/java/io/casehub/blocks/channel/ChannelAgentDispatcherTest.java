package io.casehub.blocks.channel;

import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChannelAgentDispatcherTest {

    @Mock Consumer<MessageDispatch> messageSink;
    @Mock io.casehub.qhorus.api.gateway.OutboundMessage outboundMessage;

    Function<AgentTask, String> agentProvider = task -> "LLM output for: " + task.assembledInput();

    ChannelAgentHandler matchingHandler = new ChannelAgentHandler() {
        public boolean handles(ChannelAgentRequest r) { return true; }
        public AgentTask prepareTask(ChannelAgentRequest r) { return new AgentTask("system", "user"); }
        public MessageDispatch buildResponse(UUID channelId, String senderId,
                                             String llmOutput, ChannelAgentRequest trigger) {
            return MessageDispatch.builder()
                    .channelId(channelId).sender(senderId)
                    .type(MessageType.STATUS).content("FINDING: " + llmOutput)
                    .correlationId(trigger.correlationId())
                    .actorType(io.casehub.platform.api.identity.ActorType.AGENT)
                    .build();
        }
    };

    UUID channelId = UUID.randomUUID();

    @Test
    void handler_found_dispatches_result() {
        var dispatcher = new ChannelAgentDispatcher(agentProvider, messageSink,
                List.of(matchingHandler), "test-agent");
        dispatcher.dispatch(new ChannelAgentRequest(channelId, "sub-1", outboundMessage, "general"));
        verify(messageSink).accept(argThat(d -> d.content().contains("FINDING:")));
    }

    @Test
    void no_handler_calls_onError() {
        ChannelAgentHandler noMatch = new ChannelAgentHandler() {
            public boolean handles(ChannelAgentRequest r) { return false; }
            public AgentTask prepareTask(ChannelAgentRequest r) { throw new UnsupportedOperationException(); }
            public MessageDispatch buildResponse(UUID c, String s, String o, ChannelAgentRequest t) { throw new UnsupportedOperationException(); }
        };
        List<String> errors = new ArrayList<>();
        var dispatcher = new ChannelAgentDispatcher(agentProvider, messageSink,
                List.of(noMatch), "test-agent") {
            @Override protected void onError(ChannelAgentRequest request, String reason) {
                errors.add(reason);
            }
        };
        dispatcher.dispatch(new ChannelAgentRequest(channelId, "sub-1", outboundMessage, "general"));
        assertThat(errors).containsExactly("No handler matched this sub-task request.");
        verify(messageSink, never()).accept(any());
    }

    @Test
    void provider_throws_calls_onError() {
        Function<AgentTask, String> failing = task -> { throw new RuntimeException("timeout"); };
        List<String> errors = new ArrayList<>();
        var dispatcher = new ChannelAgentDispatcher(failing, messageSink,
                List.of(matchingHandler), "test-agent") {
            @Override protected void onError(ChannelAgentRequest request, String reason) {
                errors.add(reason);
            }
        };
        dispatcher.dispatch(new ChannelAgentRequest(channelId, "sub-1", outboundMessage, "general"));
        assertThat(errors).containsExactly("Sub-agent analysis failed.");
    }

    @Test
    void parse_exception_calls_onError() {
        ChannelAgentHandler throwingHandler = new ChannelAgentHandler() {
            public boolean handles(ChannelAgentRequest r) { return true; }
            public AgentTask prepareTask(ChannelAgentRequest r) { return new AgentTask("s", "u"); }
            public MessageDispatch buildResponse(UUID c, String s, String o, ChannelAgentRequest t) {
                throw new AgentResultParseException("bad format");
            }
        };
        List<String> errors = new ArrayList<>();
        var dispatcher = new ChannelAgentDispatcher(agentProvider, messageSink,
                List.of(throwingHandler), "test-agent") {
            @Override protected void onError(ChannelAgentRequest request, String reason) {
                errors.add(reason);
            }
        };
        dispatcher.dispatch(new ChannelAgentRequest(channelId, "sub-1", outboundMessage, "general"));
        assertThat(errors).containsExactly("Sub-agent returned an unreadable result.");
    }

    @Test
    void first_match_routing_skips_non_matching() {
        ChannelAgentHandler noMatch = new ChannelAgentHandler() {
            public boolean handles(ChannelAgentRequest r) { return false; }
            public AgentTask prepareTask(ChannelAgentRequest r) { throw new UnsupportedOperationException(); }
            public MessageDispatch buildResponse(UUID c, String s, String o, ChannelAgentRequest t) { throw new UnsupportedOperationException(); }
        };
        var dispatcher = new ChannelAgentDispatcher(agentProvider, messageSink,
                List.of(noMatch, matchingHandler), "test-agent");
        dispatcher.dispatch(new ChannelAgentRequest(channelId, "sub-1", outboundMessage, "general"));
        verify(messageSink).accept(argThat(d -> d.content().contains("FINDING:")));
    }

    @Test
    void senderId_used_in_response() {
        var dispatcher = new ChannelAgentDispatcher(agentProvider, messageSink,
                List.of(matchingHandler), "my-custom-agent");
        dispatcher.dispatch(new ChannelAgentRequest(channelId, "sub-1", outboundMessage, "general"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<MessageDispatch> cap = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageSink).accept(cap.capture());
        assertThat(cap.getValue().sender()).isEqualTo("my-custom-agent");
    }

    @Test
    void topic_passedThroughToHandler() {
        var topicCaptor = new java.util.concurrent.atomic.AtomicReference<String>();
        var handler = new ChannelAgentHandler() {
            @Override
            public boolean handles(ChannelAgentRequest r) {return true;}

            @Override
            public AgentTask prepareTask(ChannelAgentRequest r) {
                topicCaptor.set(r.topic());
                return new AgentTask("system", "user");
            }

            @Override
            public io.casehub.qhorus.api.message.MessageDispatch buildResponse(
                    java.util.UUID c, String s, String o, ChannelAgentRequest t) {
                return io.casehub.qhorus.api.message.MessageDispatch.builder()
                                                                    .channelId(c).sender(s).type(io.casehub.qhorus.api.message.MessageType.RESPONSE)
                                                                    .content(o).correlationId("sub-1").inReplyTo(1L)
                                                                    .actorType(io.casehub.platform.api.identity.ActorType.AGENT)
                                                                    .topic(t.topic())
                                                                    .build();
            }
        };

        var dispatcher = new ChannelAgentDispatcher(s -> "output", messageSink, java.util.List.of(handler), "test");
        dispatcher.dispatch(new ChannelAgentRequest(channelId, "sub-1", outboundMessage, "risk-assessment"));

        assertThat(topicCaptor.get()).isEqualTo("risk-assessment");
        verify(messageSink).accept(argThat(d -> "risk-assessment".equals(d.topic())));
    }
}
