package io.casehub.blocks.conversation;

import io.casehub.api.model.TaskStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Pure state-transition operations on {@link ConversationState}.
 *
 * <p>Each method takes an existing state and returns a new state with the
 * requested change applied. No parsing, no I/O — just the fold mechanics
 * that any conversation projection (sentinel-based or typed-message) needs.
 */
public final class ConversationFold {

    private ConversationFold() {}

    public static ConversationState createPoint(ConversationState state,
                                                String pointId,
                                                String topic,
                                                Long messageId,
                                                io.casehub.qhorus.api.message.MessageType messageType,
                                                String sender,
                                                java.time.Instant createdAt,
                                                PointClassification classification,
                                                String role, int round,
                                                String entryType, String content) {
        var thread = new ArrayList<ThreadEntry>();
        thread.add(new ThreadEntry(pointId, messageId, messageType, sender, createdAt, role, round, entryType, content));
        var point = new ConversationPoint(pointId, topic, classification, thread,
                                          ConversationProtocol.STATUS_OPEN);

        var points = new LinkedHashMap<>(state.points());
        points.put(pointId, point);

        return new ConversationState(points, new ArrayList<>(state.humanFlags()),
                                     new ArrayList<>(state.memos()), new LinkedHashMap<>(state.subTaskFindings()));
    }

    public static ConversationState respondToPoint(ConversationState state,
                                                   String targetId,
                                                   Long messageId,
                                                   io.casehub.qhorus.api.message.MessageType messageType,
                                                   String sender,
                                                   java.time.Instant createdAt,
                                                   String role, int round,
                                                   String entryType, String content,
                                                   String newStatus) {
        if (!state.points().containsKey(targetId)) {return state;}

        ConversationPoint existing = state.points().get(targetId);
        var               thread   = new ArrayList<>(existing.thread());
        thread.add(new ThreadEntry(null, messageId, messageType, sender, createdAt, role, round, entryType, content));

        String resolvedStatus = newStatus != null ? newStatus : existing.status();
        var updated = new ConversationPoint(existing.id(), existing.topic(), existing.classification(),
                                            thread, resolvedStatus);

        var points = new LinkedHashMap<>(state.points());
        points.put(targetId, updated);

        return new ConversationState(points, new ArrayList<>(state.humanFlags()),
                                     new ArrayList<>(state.memos()), new LinkedHashMap<>(state.subTaskFindings()));
    }

    public static ConversationState reprioritisePoint(ConversationState state,
                                                      String targetId,
                                                      Long messageId,
                                                      io.casehub.qhorus.api.message.MessageType messageType,
                                                      String sender,
                                                      java.time.Instant createdAt,
                                                      String role, int round,
                                                      Priority newPriority,
                                                      String content) {
        if (!state.points().containsKey(targetId)) {return state;}

        ConversationPoint existing = state.points().get(targetId);
        var               thread   = new ArrayList<>(existing.thread());
        thread.add(new ThreadEntry(null, messageId, messageType, sender, createdAt,
                                   role, round, "REPRIORITISE", content));

        PointClassification oldClass = existing.classification();
        PointClassification newClass = new PointClassification(newPriority,
                                                               oldClass.scope(), oldClass.location());
        var updated = new ConversationPoint(existing.id(), existing.topic(), newClass,
                                            thread, existing.status());

        var points = new LinkedHashMap<>(state.points());
        points.put(targetId, updated);

        return new ConversationState(points, new ArrayList<>(state.humanFlags()),
                                     new ArrayList<>(state.memos()), new LinkedHashMap<>(state.subTaskFindings()));
    }


    public static ConversationState flagHuman(ConversationState state,
                                              String targetId,
                                              Long messageId,
                                              String sender,
                                              java.time.Instant createdAt,
                                              String role, int round,
                                              String content) {
        var points = new LinkedHashMap<>(state.points());
        if (targetId != null && points.containsKey(targetId)) {
            ConversationPoint p      = points.get(targetId);
            var               thread = new ArrayList<>(p.thread());
            thread.add(new ThreadEntry(null, messageId, null, sender, createdAt, role, round, ConversationProtocol.FLAG_HUMAN, content));
            points.put(targetId, new ConversationPoint(p.id(), p.topic(), p.classification(),
                                                       thread, ConversationProtocol.STATUS_ESCALATED));
        }

        var flags = new ArrayList<>(state.humanFlags());
        flags.add(new FlagEntry(null, round, role, content));

        return new ConversationState(points, flags,
                                     new ArrayList<>(state.memos()), new LinkedHashMap<>(state.subTaskFindings()));
    }

    public static ConversationState addMemo(ConversationState state,
                                            String role, int round,
                                            String content) {
        var memos = new ArrayList<>(state.memos());
        memos.add(new RoundMemo(role, round, content));
        return new ConversationState(state.points(), new ArrayList<>(state.humanFlags()),
                                     memos, new LinkedHashMap<>(state.subTaskFindings()));
    }

    public static ConversationState requestSubTask(ConversationState state,
                                                   String subTaskId,
                                                   String taskType,
                                                   String requestingRole,
                                                   String pointId) {
        var findings = new LinkedHashMap<>(state.subTaskFindings());
        findings.put(subTaskId, new SubTaskFinding(subTaskId, taskType, requestingRole,
                                                   pointId, null, null, TaskStatus.PENDING));

        return new ConversationState(state.points(), new ArrayList<>(state.humanFlags()),
                                     new ArrayList<>(state.memos()), findings);
    }

    public static ConversationState completeSubTask(ConversationState state,
                                                    String subTaskId,
                                                    String taskType,
                                                    String role,
                                                    String pointId,
                                                    String finding) {
        var            findings = new LinkedHashMap<>(state.subTaskFindings());
        SubTaskFinding existing = findings.get(subTaskId);

        String requestedBy = existing != null ? existing.requestedBy() : role;
        String resolvedPointId = existing != null && existing.pointId() != null
                                 ? existing.pointId() : pointId;

        findings.put(subTaskId, new SubTaskFinding(subTaskId, taskType, requestedBy,
                                                   resolvedPointId, finding, null, TaskStatus.COMPLETED));

        return new ConversationState(state.points(), new ArrayList<>(state.humanFlags()),
                                     new ArrayList<>(state.memos()), findings);
    }

    public static ConversationState errorSubTask(ConversationState state,
                                                 String subTaskId,
                                                 String taskType,
                                                 String role,
                                                 String reason) {
        var            findings = new LinkedHashMap<>(state.subTaskFindings());
        SubTaskFinding existing = findings.get(subTaskId);

        String requestedBy     = existing != null ? existing.requestedBy() : role;
        String resolvedPointId = existing != null ? existing.pointId() : null;

        findings.put(subTaskId, new SubTaskFinding(subTaskId, taskType, requestedBy,
                                                   resolvedPointId, null, reason, TaskStatus.FAULTED));

        return new ConversationState(state.points(), new ArrayList<>(state.humanFlags()),
                                     new ArrayList<>(state.memos()), findings);
    }
}
