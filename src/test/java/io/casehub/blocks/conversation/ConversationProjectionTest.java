package io.casehub.blocks.conversation;

import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.qhorus.api.message.MessageView;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationProjectionTest {

    private final TestConversationProjection projection = new TestConversationProjection();

    // ── helpers ──────────────────────────────────────────────────────────────

    private MessageView message(String content, String correlationId) {
        var msg = mock(MessageView.class);
        when(msg.content()).thenReturn(content);
        when(msg.correlationId()).thenReturn(correlationId);
        return msg;
    }

    private String encode(Map<String, String> meta, String body) {
        return ChannelMessageMeta.encode("TEST:", meta, body);
    }

    private Map<String, String> meta(String... pairs) {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    // ── identity ─────────────────────────────────────────────────────────────

    @Test
    void identity_returnsFreshEmptyState() {
        ConversationState s1 = projection.identity();
        ConversationState s2 = projection.identity();
        assertThat(s1).isNotSameAs(s2);
        assertThat(s1.points()).isEmpty();
        assertThat(s1.humanFlags()).isEmpty();
        assertThat(s1.memos()).isEmpty();
        assertThat(s1.subTaskFindings()).isEmpty();
    }

    // ── point initiation ─────────────────────────────────────────────────────

    @Test
    void pointInitiation_createsPointWithOpenStatus() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1",
                "priority", "HIGH",
                "scope", "global",
                "location", "section 3.2"), "This needs attention"), "point-1");

        var result = projection.apply(state, msg);

        assertThat(result.points()).containsKey("point-1");
        ConversationPoint point = result.points().get("point-1");
        assertThat(point.id()).isEqualTo("point-1");
        assertThat(point.status()).isEqualTo(ConversationProtocol.STATUS_OPEN);
        assertThat(point.classification().priority()).isEqualTo(Priority.HIGH);
        assertThat(point.classification().scope()).isEqualTo("global");
        assertThat(point.classification().location()).isEqualTo("section 3.2");
        assertThat(point.thread()).hasSize(1);
        ThreadEntry entry = point.thread().get(0);
        assertThat(entry.entryId()).isEqualTo("point-1");
        assertThat(entry.role()).isEqualTo("REVIEWER");
        assertThat(entry.round()).isEqualTo(1);
        assertThat(entry.entryType()).isEqualTo("OPEN_TOPIC");
        assertThat(entry.content()).isEqualTo("This needs attention");
    }

    @Test
    void pointInitiation_nullCorrelationId_stateUnchanged() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1"), "body"), null);

        var result = projection.apply(state, msg);

        assertThat(result.points()).isEmpty();
    }

    // ── point response ───────────────────────────────────────────────────────

    @Test
    void pointResponse_appendsToThreadAndUpdatesStatus() {
        var state = projection.identity();
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1",
                "priority", "MEDIUM"), "Initial point"), "point-1"));

        var response = message(encode(meta(
                "entryType", "ACCEPT",
                "role", "IMPLEMENTOR",
                "round", "1"), "I agree with this"), "point-1");

        var result = projection.apply(state, response);

        ConversationPoint point = result.points().get("point-1");
        assertThat(point.status()).isEqualTo("ACCEPTED");
        assertThat(point.thread()).hasSize(2);
        ThreadEntry second = point.thread().get(1);
        assertThat(second.role()).isEqualTo("IMPLEMENTOR");
        assertThat(second.entryType()).isEqualTo("ACCEPT");
        assertThat(second.content()).isEqualTo("I agree with this");
    }

    @Test
    void pointResponse_nonExistentPoint_stateUnchanged() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "ACCEPT",
                "role", "IMPLEMENTOR",
                "round", "1"), "accepting"), "no-such-point");

        var result = projection.apply(state, msg);

        assertThat(result.points()).isEmpty();
    }

    @Test
    void pointResponse_nullCorrelationId_stateUnchanged() {
        var state = projection.identity();
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1"), "point body"), "point-1"));

        var response = message(encode(meta(
                "entryType", "ACCEPT",
                "role", "IMPLEMENTOR",
                "round", "1"), "response body"), null);

        var result = projection.apply(state, response);

        // Point should still exist unmodified
        assertThat(result.points().get("point-1").thread()).hasSize(1);
    }

    // ── unknown domain type ──────────────────────────────────────────────────

    @Test
    void unknownDomainType_appendsToThread_statusUnchanged() {
        var state = projection.identity();
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1"), "initial"), "point-1"));

        var msg = message(encode(meta(
                "entryType", "SOME_UNKNOWN_TYPE",
                "role", "REVIEWER",
                "round", "2"), "unknown action"), "point-1");

        var result = projection.apply(state, msg);

        ConversationPoint point = result.points().get("point-1");
        assertThat(point.thread()).hasSize(2);
        assertThat(point.status()).isEqualTo(ConversationProtocol.STATUS_OPEN);
        assertThat(point.thread().get(1).entryType()).isEqualTo("SOME_UNKNOWN_TYPE");
    }

    // ── MEMO ─────────────────────────────────────────────────────────────────

    @Test
    void memo_appendsToMemosList() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "MEMO",
                "role", "REVIEWER",
                "round", "2"), "Round 2 observations"), "corr-1");

        var result = projection.apply(state, msg);

        assertThat(result.memos()).hasSize(1);
        RoundMemo memo = result.memos().get(0);
        assertThat(memo.role()).isEqualTo("REVIEWER");
        assertThat(memo.round()).isEqualTo(2);
        assertThat(memo.content()).isEqualTo("Round 2 observations");
    }

    @Test
    void memo_missingRole_usesUnknown() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "MEMO",
                "round", "1"), "memo without role"), "corr-1");

        var result = projection.apply(state, msg);

        assertThat(result.memos()).hasSize(1);
        assertThat(result.memos().get(0).role()).isEqualTo("UNKNOWN");
    }

    // ── SUB_TASK_REQUEST ─────────────────────────────────────────────────────

    @Test
    void subTaskRequest_createsSubTaskFindingWithPendingStatus() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "SUB_TASK_REQUEST",
                "role", "REVIEWER",
                "subTaskId", "st-1",
                "taskType", "VERIFY",
                "pointId", "point-1"), "verify this claim"), "corr-1");

        var result = projection.apply(state, msg);

        assertThat(result.subTaskFindings()).containsKey("st-1");
        SubTaskFinding finding = result.subTaskFindings().get("st-1");
        assertThat(finding.subTaskId()).isEqualTo("st-1");
        assertThat(finding.taskType()).isEqualTo("VERIFY");
        assertThat(finding.requestedBy()).isEqualTo("REVIEWER");
        assertThat(finding.pointId()).isEqualTo("point-1");
        assertThat(finding.finding()).isNull();
        assertThat(finding.errorReason()).isNull();
        assertThat(finding.status()).isEqualTo(SubTaskStatus.PENDING);
    }

    @Test
    void subTaskRequest_missingSubTaskId_fallsBackToCorrelationId() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "SUB_TASK_REQUEST",
                "role", "REVIEWER",
                "taskType", "CUSTOM"), "task body"), "corr-fallback");

        var result = projection.apply(state, msg);

        assertThat(result.subTaskFindings()).containsKey("corr-fallback");
    }

    // ── SUB_TASK_FINDING ─────────────────────────────────────────────────────

    @Test
    void subTaskFinding_updatesToComplete_preservesRequestedBy() {
        // First, create a request from REVIEWER
        var state = projection.identity();
        state = projection.apply(state, message(encode(meta(
                "entryType", "SUB_TASK_REQUEST",
                "role", "REVIEWER",
                "subTaskId", "st-1",
                "taskType", "VERIFY",
                "pointId", "point-1"), "verify claim"), "corr-1"));

        // Then, a finding from ARBITRATOR — requestedBy must stay REVIEWER
        var findingMsg = message(encode(meta(
                "entryType", "SUB_TASK_FINDING",
                "role", "ARBITRATOR",
                "subTaskId", "st-1",
                "taskType", "VERIFY"), "Claim verified"), "corr-2");

        var result = projection.apply(state, findingMsg);

        SubTaskFinding f = result.subTaskFindings().get("st-1");
        assertThat(f.status()).isEqualTo(SubTaskStatus.COMPLETE);
        assertThat(f.finding()).isEqualTo("Claim verified");
        assertThat(f.requestedBy()).isEqualTo("REVIEWER");  // Bug fix R2-03
        assertThat(f.pointId()).isEqualTo("point-1");
    }

    @Test
    void subTaskFinding_withoutPriorRequest_usesRoleFromFindingMessage() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "SUB_TASK_FINDING",
                "role", "ARBITRATOR",
                "subTaskId", "st-new",
                "taskType", "VERIFY",
                "pointId", "point-2"), "finding content"), "corr-1");

        var result = projection.apply(state, msg);

        SubTaskFinding f = result.subTaskFindings().get("st-new");
        assertThat(f.status()).isEqualTo(SubTaskStatus.COMPLETE);
        assertThat(f.requestedBy()).isEqualTo("ARBITRATOR");
    }

    // ── SUB_TASK_ERROR ───────────────────────────────────────────────────────

    @Test
    void subTaskError_updatesToError_preservesRequestedBy() {
        // First, create a request from IMPLEMENTOR
        var state = projection.identity();
        state = projection.apply(state, message(encode(meta(
                "entryType", "SUB_TASK_REQUEST",
                "role", "IMPLEMENTOR",
                "subTaskId", "st-2",
                "taskType", "CUSTOM",
                "pointId", "point-3"), "do something"), "corr-1"));

        // Error from ARBITRATOR — requestedBy must stay IMPLEMENTOR
        var errorMsg = message(encode(meta(
                "entryType", "SUB_TASK_ERROR",
                "role", "ARBITRATOR",
                "subTaskId", "st-2",
                "taskType", "CUSTOM"), "task failed"), "corr-2");

        var result = projection.apply(state, errorMsg);

        SubTaskFinding f = result.subTaskFindings().get("st-2");
        assertThat(f.status()).isEqualTo(SubTaskStatus.ERROR);
        assertThat(f.errorReason()).isEqualTo("task failed");
        assertThat(f.requestedBy()).isEqualTo("IMPLEMENTOR");  // Bug fix R2-03
        assertThat(f.pointId()).isEqualTo("point-3");
    }

    @Test
    void subTaskError_withoutPriorRequest_usesRoleFromErrorMessage() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "SUB_TASK_ERROR",
                "role", "ARBITRATOR",
                "subTaskId", "st-orphan",
                "taskType", "CUSTOM"), "error reason"), "corr-1");

        var result = projection.apply(state, msg);

        SubTaskFinding f = result.subTaskFindings().get("st-orphan");
        assertThat(f.status()).isEqualTo(SubTaskStatus.ERROR);
        assertThat(f.requestedBy()).isEqualTo("ARBITRATOR");
    }

    // ── FLAG_HUMAN ───────────────────────────────────────────────────────────

    @Test
    void flagHuman_withTargetPoint_addsToThreadAndFlags() {
        var state = projection.identity();
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1"), "a point"), "point-1"));

        var flagMsg = message(encode(meta(
                "entryType", "FLAG_HUMAN",
                "role", "REVIEWER",
                "round", "2"), "human help needed"), "point-1");

        var result = projection.apply(state, flagMsg);

        // Thread gets a FLAG_HUMAN entry
        ConversationPoint point = result.points().get("point-1");
        assertThat(point.thread()).hasSize(2);
        assertThat(point.thread().get(1).entryType()).isEqualTo(ConversationProtocol.FLAG_HUMAN);
        assertThat(point.status()).isEqualTo(ConversationProtocol.STATUS_ESCALATED);

        // Flags list gets an entry
        assertThat(result.humanFlags()).hasSize(1);
        FlagEntry flag = result.humanFlags().get(0);
        assertThat(flag.role()).isEqualTo("REVIEWER");
        assertThat(flag.round()).isEqualTo(2);
        assertThat(flag.content()).isEqualTo("human help needed");
    }

    @Test
    void flagHuman_withoutTargetPoint_flagEntryOnly() {
        var state = projection.identity();
        var flagMsg = message(encode(meta(
                "entryType", "FLAG_HUMAN",
                "role", "REVIEWER",
                "round", "3"), "general escalation"), "nonexistent-point");

        var result = projection.apply(state, flagMsg);

        // No point update
        assertThat(result.points()).isEmpty();

        // Flags list gets an entry
        assertThat(result.humanFlags()).hasSize(1);
        assertThat(result.humanFlags().get(0).content()).isEqualTo("general escalation");
    }

    @Test
    void flagHuman_nullCorrelationId_flagEntryOnly() {
        var state = projection.identity();
        var flagMsg = message(encode(meta(
                "entryType", "FLAG_HUMAN",
                "role", "REVIEWER",
                "round", "1"), "escalation without target"), null);

        var result = projection.apply(state, flagMsg);

        assertThat(result.points()).isEmpty();
        assertThat(result.humanFlags()).hasSize(1);
    }

    // ── RESTART_CONTEXT ──────────────────────────────────────────────────────

    @Test
    void restartContext_stateUnchanged() {
        var state = projection.identity();
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1"), "a point"), "point-1"));

        var restartMsg = message(encode(meta(
                "entryType", "RESTART_CONTEXT",
                "role", "SYSTEM",
                "round", "2"), "context restarted"), "corr-x");

        var result = projection.apply(state, restartMsg);

        assertThat(result).isSameAs(state);
    }

    // ── missing metadata ─────────────────────────────────────────────────────

    @Test
    void missingMetadata_plainContent_stateUnchanged() {
        var state = projection.identity();
        var msg = message("Just plain text, no sentinel", "corr-1");

        var result = projection.apply(state, msg);

        assertThat(result).isSameAs(state);
    }

    @Test
    void missingEntryType_stateUnchanged() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "role", "REVIEWER",
                "round", "1"), "no entryType"), "corr-1");

        var result = projection.apply(state, msg);

        assertThat(result).isSameAs(state);
    }

    // ── missing role ─────────────────────────────────────────────────────────

    @Test
    void missingRole_onPointInitiation_stateUnchanged() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "round", "1"), "no role"), "point-1");

        var result = projection.apply(state, msg);

        assertThat(result.points()).isEmpty();
    }

    @Test
    void missingRole_onPointResponse_stateUnchanged() {
        var state = projection.identity();
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1"), "the point"), "point-1"));

        var response = message(encode(meta(
                "entryType", "ACCEPT",
                "round", "1"), "response without role"), "point-1");

        var result = projection.apply(state, response);

        assertThat(result.points().get("point-1").thread()).hasSize(1);
    }

    // ── priority parsing ─────────────────────────────────────────────────────

    @Test
    void priority_unknownValue_defaultsToLow() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1",
                "priority", "CRITICAL"), "point with unknown priority"), "point-1");

        var result = projection.apply(state, msg);

        assertThat(result.points().get("point-1").classification().priority())
                .isEqualTo(Priority.LOW);
    }

    @Test
    void priority_absentValue_defaultsToLow() {
        var state = projection.identity();
        var msg = message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1"), "point without priority"), "point-1");

        var result = projection.apply(state, msg);

        assertThat(result.points().get("point-1").classification().priority())
                .isEqualTo(Priority.LOW);
    }

    // ── multi-point accumulation ─────────────────────────────────────────────

    @Test
    void multiplePoints_accumulateIndependently() {
        var state = projection.identity();
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1",
                "priority", "HIGH"), "first point"), "point-1"));
        state = projection.apply(state, message(encode(meta(
                "entryType", "OPEN_TOPIC",
                "role", "REVIEWER",
                "round", "1",
                "priority", "MEDIUM"), "second point"), "point-2"));
        state = projection.apply(state, message(encode(meta(
                "entryType", "ACCEPT",
                "role", "IMPLEMENTOR",
                "round", "1"), "accepting first"), "point-1"));

        assertThat(state.points()).hasSize(2);
        assertThat(state.points().get("point-1").status()).isEqualTo("ACCEPTED");
        assertThat(state.points().get("point-1").thread()).hasSize(2);
        assertThat(state.points().get("point-2").status()).isEqualTo(ConversationProtocol.STATUS_OPEN);
        assertThat(state.points().get("point-2").thread()).hasSize(1);
    }

    // ── apply never throws ──────────────────────────────────────────────────

    @Test
    void apply_nullContent_doesNotThrow() {
        var state = projection.identity();
        var msg = message(null, "corr-1");

        var result = projection.apply(state, msg);

        assertThat(result).isSameAs(state);
    }

    @Test
    void apply_emptyContent_doesNotThrow() {
        var state = projection.identity();
        var msg = message("", "corr-1");

        var result = projection.apply(state, msg);

        assertThat(result).isSameAs(state);
    }

    // ── test subclass ────────────────────────────────────────────────────────

    static class TestConversationProjection extends ConversationProjection {
        @Override
        protected String sentinel() {
            return "TEST:";
        }

        @Override
        protected boolean isPointInitiator(String entryType) {
            return "OPEN_TOPIC".equals(entryType);
        }

        @Override
        protected String statusAfter(String entryType) {
            return switch (entryType) {
                case "ACCEPT" -> "ACCEPTED";
                case "REJECT" -> "REJECTED";
                case "CHALLENGE" -> "CHALLENGED";
                default -> null;
            };
        }
    }
}
