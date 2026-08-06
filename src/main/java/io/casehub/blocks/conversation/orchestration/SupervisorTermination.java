package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.termination.TerminationContext;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.casehub.blocks.conversation.ConversationState;
import io.smallrye.mutiny.Uni;

public class SupervisorTermination implements TerminationCondition<ConversationState> {

    private final String supervisorRole;
    private final String signalEntryType;

    public SupervisorTermination(String supervisorRole, String signalEntryType) {
        this.supervisorRole = supervisorRole;
        this.signalEntryType = signalEntryType;
    }

    public SupervisorTermination(String supervisorRole) {
        this(supervisorRole, "TERMINATE");
    }

    @Override
    public Uni<TerminationDecision> evaluate(TerminationContext<ConversationState> context) {
        for (var point : context.state().points().values()) {
            for (var entry : point.thread()) {
                if (supervisorRole.equals(entry.role())
                        && signalEntryType.equals(entry.entryType())) {
                    return Uni.createFrom().item(
                            new TerminationDecision.Complete(entry.content()));
                }
            }
        }
        return Uni.createFrom().item(TerminationDecision.Continue.INSTANCE);
    }
}
