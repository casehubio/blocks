package io.casehub.blocks.agentic.social;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileTest {

    @Test
    void constructionWithRequiredFields() {
        var now = Instant.now();
        var profile = new UserProfile("agent-1", "user-1", "t1",
                "stranger", 0.0, 0, 0, 0, 0, now, now, null,
                null, null, null, null, Map.of());
        assertThat(profile.agentId()).isEqualTo("agent-1");
        assertThat(profile.subjectId()).isEqualTo("user-1");
        assertThat(profile.communicationStyle()).isNull();
        assertThat(profile.topicsOfInterest()).isNull();
    }

    @Test
    void metadataIsImmutable() {
        var now = Instant.now();
        var mutable = new HashMap<String, String>();
        mutable.put("key", "value");
        var profile = new UserProfile("a", "s", "t", "stranger", 0.0,
                0, 0, 0, 0, now, now, null, null, null, null, null, mutable);
        mutable.put("new", "entry");
        assertThat(profile.metadata()).doesNotContainKey("new");
    }

    @Test
    void nullAgentIdRejected() {
        assertThatThrownBy(() -> new UserProfile(null, "s", "t", "stranger",
                0.0, 0, 0, 0, 0, Instant.now(), Instant.now(), null,
                null, null, null, null, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullSubjectIdRejected() {
        assertThatThrownBy(() -> new UserProfile("a", null, "t", "stranger",
                0.0, 0, 0, 0, 0, Instant.now(), Instant.now(), null,
                null, null, null, null, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullRelationshipStageRejected() {
        assertThatThrownBy(() -> new UserProfile("a", "s", "t", null,
                0.0, 0, 0, 0, 0, Instant.now(), Instant.now(), null,
                null, null, null, null, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void llmFieldsAreNullable() {
        var now = Instant.now();
        var profile = new UserProfile("a", "s", "t", "acquaintance", 0.3,
                10, 7, 2, 1, now, now, now,
                "formal", "tech", "morning meetings", "improving", Map.of());
        assertThat(profile.communicationStyle()).isEqualTo("formal");
        assertThat(profile.topicsOfInterest()).isEqualTo("tech");
        assertThat(profile.preferences()).isEqualTo("morning meetings");
        assertThat(profile.synthesisNotes()).isEqualTo("improving");
    }
}
