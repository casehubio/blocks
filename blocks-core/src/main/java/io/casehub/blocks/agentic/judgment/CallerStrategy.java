package io.casehub.blocks.agentic.judgment;

import java.util.List;

public sealed interface CallerStrategy {

    record Single(CallerRef caller) implements CallerStrategy {}

    record FanOut(List<CallerRef> callers, AgreementPolicy agreementPolicy) implements CallerStrategy {
        public FanOut { callers = List.copyOf(callers); }
    }

    record EscalationChain(List<CallerRef> callers) implements CallerStrategy {
        public EscalationChain { callers = List.copyOf(callers); }
    }

    static Single single(CallerRef caller) { return new Single(caller); }

    static Single single() { return new Single(CallerRef.agent("default", null)); }

    static FanOut fanOut(List<CallerRef> callers, AgreementPolicy policy) {
        return new FanOut(callers, policy);
    }
}
