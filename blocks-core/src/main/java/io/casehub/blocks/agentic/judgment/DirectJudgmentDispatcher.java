package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.AgentInvoker;

import java.util.List;
import java.util.Map;

public final class DirectJudgmentDispatcher implements JudgmentDispatcher {

    private final AgentInvoker<Object> invoker = AgentInvoker.defaultInvoker();

    @Override
    public JudgmentResponse dispatch(JudgmentDispatchRequest request) {
        AgentRef agentRef = request.caller().agentRef();
        if (agentRef == null) {
            throw new IllegalArgumentException(
                    "DirectJudgmentDispatcher requires CallerRef with non-null agentRef");
        }

        AgentResult result = invoker.invoke(agentRef, request.context().executionContext())
                                     .await().indefinitely();

        return mapResult(result, request.caller());
    }

    private JudgmentResponse mapResult(AgentResult result, CallerRef caller) {
        var callerIdentity = CallerIdentity.of(caller.id(), "agent");
        Object output = result.output();

        if (output instanceof JudgmentResponse jr) {
            return jr;
        }
        if (output instanceof Map<?, ?> map) {
            Object decision = map.get("decision");
            return new JudgmentResponse(decision, List.of(), callerIdentity);
        }
        return new JudgmentResponse(output, List.of(), callerIdentity);
    }
}
