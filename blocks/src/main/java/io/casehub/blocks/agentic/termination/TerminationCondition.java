package io.casehub.blocks.agentic.termination;

public interface TerminationCondition<T> {
    TerminationDecision evaluate(TerminationContext<T> context);

    default TerminationCondition<T> or(TerminationCondition<T> other) {
        return ctx -> {
            var first = this.evaluate(ctx);
            if (first instanceof TerminationDecision.Continue) {
                return other.evaluate(ctx);
            }
            var second = other.evaluate(ctx);
            if (second instanceof TerminationDecision.Continue) {
                return first;
            }
            return higherPriority(first, second);
        };
    }

    default TerminationCondition<T> and(TerminationCondition<T> other) {
        return ctx -> {
            var first = this.evaluate(ctx);
            if (first instanceof TerminationDecision.Continue) {return first;}
            var second = other.evaluate(ctx);
            if (second instanceof TerminationDecision.Continue) {return second;}
            return higherPriority(first, second);
        };
    }

    private static TerminationDecision higherPriority(
            TerminationDecision a, TerminationDecision b) {
        return priority(a) >= priority(b) ? a : b;
    }

    private static int priority(TerminationDecision d) {
        return switch (d) {
            case TerminationDecision.Escalate e -> 3;
            case TerminationDecision.Failed f -> 2;
            case TerminationDecision.Complete c -> 1;
            case TerminationDecision.Continue ignored -> 0;
        };
    }
}
