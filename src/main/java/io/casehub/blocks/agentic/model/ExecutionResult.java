package io.casehub.blocks.agentic.model;

public sealed interface ExecutionResult
        permits ExecutionResult.Completed, ExecutionResult.Failed,
                ExecutionResult.Escalated, ExecutionResult.Cancelled {

    record Completed(Object result) implements ExecutionResult {}
    record Failed(String reason, Throwable cause) implements ExecutionResult {}
    record Escalated(String reason) implements ExecutionResult {}
    record Cancelled() implements ExecutionResult {}
}
