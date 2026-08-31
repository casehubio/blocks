package io.casehub.blocks.agentic.judgment;

@FunctionalInterface
public interface JudgmentDispatcher {

    JudgmentResponse dispatch(JudgmentDispatchRequest request);
}
