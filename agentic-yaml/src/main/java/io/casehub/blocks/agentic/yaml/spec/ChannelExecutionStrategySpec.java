package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ChannelExecutionStrategySpec.ConversationStrategySpec.class,
              name = "conversation"),
        @Type(value = ChannelExecutionStrategySpec.FanInStrategySpec.class,
              name = "fan-in"),
        @Type(value = ChannelExecutionStrategySpec.BarrierStrategySpec.class,
              name = "barrier")
})
public sealed interface ChannelExecutionStrategySpec {

    record ConversationStrategySpec(
            @Nullable TurnPolicySpec turnPolicy,
            @Nullable List<TerminationSpec> termination,
            @Nullable EpistemicRuleSpec epistemicRule,
            @Nullable ConvergencePolicySpec convergencePolicy,
            List<AgentParticipantSpec> participants
    ) implements ChannelExecutionStrategySpec {}

    record FanInStrategySpec(
            @Nullable Duration executionTimeout
    ) implements ChannelExecutionStrategySpec {}

    record BarrierStrategySpec(
            @Nullable Duration executionTimeout
    ) implements ChannelExecutionStrategySpec {}
}
