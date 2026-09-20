package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.casehub.blocks.agentic.FailurePolicy;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = PatternSpec.Supervisor.class, name = "supervisor"),
        @Type(value = PatternSpec.Debate.class, name = "debate"),
        @Type(value = PatternSpec.Loop.class, name = "loop"),
        @Type(value = PatternSpec.Parallel.class, name = "parallel"),
        @Type(value = PatternSpec.Voting.class, name = "voting"),
        @Type(value = PatternSpec.Conditional.class, name = "conditional"),
        @Type(value = PatternSpec.Sequence.class, name = "sequence"),
        @Type(value = PatternSpec.Htn.class, name = "htn")
})
public sealed interface PatternSpec {

    @Nullable RoutingSpec routing();
    @Nullable List<TerminationSpec> termination();
    @Nullable AggregationSpec aggregation();
    @Nullable ActivationSpec activation();
    @Nullable DecompositionSpec decomposition();
    @Nullable FailurePolicy failurePolicy();
    @Nullable String task();
    List<AgentRefSpec> agents();
    @Nullable JudgmentSpec judgment();
    @Nullable GovernanceSpec governance();

    record Supervisor(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance
    ) implements PatternSpec {}

    record Debate(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance,
            @Nullable AgentRefSpec judge,
            int maxRounds
    ) implements PatternSpec {}

    record Loop(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance,
            int maxIterations,
            @Nullable String exitCondition
    ) implements PatternSpec {}

    record Parallel(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance
    ) implements PatternSpec {}

    record Voting(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance
    ) implements PatternSpec {}

    record Conditional(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance,
            List<BranchSpec> branches
    ) implements PatternSpec {}

    record Sequence(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance
    ) implements PatternSpec {}

    record Htn(
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance,
            TaskNodeSpec rootTask
    ) implements PatternSpec {}
}
