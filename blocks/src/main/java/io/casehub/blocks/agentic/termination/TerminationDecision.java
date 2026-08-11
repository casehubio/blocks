package io.casehub.blocks.agentic.termination;

public sealed interface TerminationDecision
        permits TerminationDecision.Continue, TerminationDecision.Complete,
                TerminationDecision.Failed, TerminationDecision.Escalate {

    record Continue() implements TerminationDecision {
        public static final Continue INSTANCE = new Continue();
    }

    record Complete(Object result) implements TerminationDecision {}
    record Failed(String reason) implements TerminationDecision {}
    record Escalate(String reason) implements TerminationDecision {}
}
