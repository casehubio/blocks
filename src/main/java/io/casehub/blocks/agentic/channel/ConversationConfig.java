package io.casehub.blocks.agentic.channel;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.conversation.ConversationProjection;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.conversation.orchestration.AgentParticipant;
import io.casehub.blocks.conversation.orchestration.PromptAssembler;
import io.casehub.blocks.conversation.orchestration.ResponseMessageBuilder;
import io.casehub.blocks.conversation.orchestration.TurnPolicy;
import io.casehub.qhorus.api.message.MessageView;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public record ConversationConfig<T>(
        PromptAssembler promptAssembler,
        ResponseMessageBuilder responseBuilder,
        Function<T, MessageView> triggerMapper,
        @Nullable Supplier<ConversationProjection> projectionFactory,
        @Nullable AgentInvoker<String> conversationInvoker,
        @Nullable Function<AgentRef, AgentParticipant> participantMapper,
        @Nullable TurnPolicy turnPolicyOverride,
        @Nullable TerminationCondition<ConversationState> terminationOverride
) {
    public ConversationConfig {
        Objects.requireNonNull(promptAssembler, "promptAssembler");
        Objects.requireNonNull(responseBuilder, "responseBuilder");
        Objects.requireNonNull(triggerMapper, "triggerMapper");
    }
}
