package io.casehub.blocks.agentic.model;

import io.casehub.blocks.agentic.AgentRef;

public sealed interface ExecutionState
        permits ExecutionState.Idle, ExecutionState.Running,
                ExecutionState.WaitingForAgent, ExecutionState.WaitingForEvent,
                ExecutionState.Complete, ExecutionState.Faulted,
                ExecutionState.Cancelled {

    record Idle() implements ExecutionState {}
    record Running(int iteration) implements ExecutionState {}
    record WaitingForAgent(AgentRef agent) implements ExecutionState {}
    record WaitingForEvent() implements ExecutionState {}
    record Complete() implements ExecutionState {}
    record Faulted() implements ExecutionState {}
    record Cancelled() implements ExecutionState {}
}
