package io.casehub.blocks.conversation.orchestration;

import io.casehub.blocks.agentic.AgentRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentParticipantTest {

    @Test
    void agentId_delegatesToAgentRefName() {
        var ref = AgentRef.external("reviewer", ignored -> null);
        var participant = new AgentParticipant(ref, "REV", "You are a reviewer.");
        assertThat(participant.agentId()).isEqualTo("reviewer");
    }

    @Test
    void role_returnsConstructorValue() {
        var ref = AgentRef.external("impl", ignored -> null);
        var participant = new AgentParticipant(ref, "IMP", "You implement.");
        assertThat(participant.role()).isEqualTo("IMP");
    }

    @Test
    void systemPrompt_returnsConstructorValue() {
        var ref = AgentRef.external("impl", ignored -> null);
        var participant = new AgentParticipant(ref, "IMP", "You implement code changes.");
        assertThat(participant.systemPrompt()).isEqualTo("You implement code changes.");
    }
}
