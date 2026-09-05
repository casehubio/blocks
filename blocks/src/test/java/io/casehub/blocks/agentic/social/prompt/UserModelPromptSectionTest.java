package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.UserModelOrchestrator;
import io.casehub.blocks.agentic.social.UserProfile;
import io.casehub.blocks.speech.PromptContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserModelPromptSectionTest {

    @Test
    void rendersProfileForSubject() {
        var orchestrator = mock(UserModelOrchestrator.class);
        var now = Instant.now();
        var profile = new UserProfile("a1", "user1", "t1",
                "familiar", 0.7, 15, 10, 2, 3,
                now, now, now, "concise", "Java, testing", "prefers examples", null, Map.of());
        when(orchestrator.currentProfile("a1", "user1", "t1")).thenReturn(profile);
        var section = new UserModelPromptSection(orchestrator);
        var result = section.contribute(new PromptContext("a1", "t1", "user1"));
        assertThat(result).isNotNull();
        assertThat(result).contains("familiar");
        assertThat(result).contains("Java, testing");
        assertThat(result).contains("0.7");
    }

    @Test
    void returnsNullWhenNoSubjectId() {
        var orchestrator = mock(UserModelOrchestrator.class);
        var section = new UserModelPromptSection(orchestrator);
        assertThat(section.contribute(new PromptContext("a1", "t1", null))).isNull();
    }

    @Test
    void returnsNullWhenNoProfile() {
        var orchestrator = mock(UserModelOrchestrator.class);
        when(orchestrator.currentProfile("a1", "user1", "t1")).thenReturn(null);
        var section = new UserModelPromptSection(orchestrator);
        assertThat(section.contribute(new PromptContext("a1", "t1", "user1"))).isNull();
    }
}
