package io.casehub.blocks.conversation;

import io.casehub.blocks.channel.ChannelMessageMeta;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.*;

/**
 * Abstract base class for conversation-style channel projections.
 *
 * <p>Folds channel messages into {@link ConversationState} by dispatching on
 * metadata entry types. Infrastructure types ({@code MEMO}, {@code SUB_TASK_*},
 * {@code FLAG_HUMAN}, {@code RESTART_CONTEXT}) are handled by the base class;
 * domain entry types are dispatched via three hook methods that subclasses override.
 *
 * <p>{@link #apply} never throws (protocol PP-20260610-a47ef5). Malformed or
 * unrecognised messages are logged and the state is returned unchanged.
 */
public abstract class ConversationProjection implements ChannelProjection<ConversationState> {

    private static final Logger LOG = System.getLogger(ConversationProjection.class.getName());

    // ── subclass hooks ───────────────────────────────────────────────────────

    /**
     * The sentinel prefix used to parse metadata headers from message content.
     * Must match the encoding sentinel used by the channel backend.
     */
    protected abstract String sentinel();

    /**
     * Returns {@code true} if the given entry type initiates a new conversation point
     * (e.g. {@code RAISE} in DraftHouse debates). The point is created with
     * {@link ConversationProtocol#STATUS_OPEN} status.
     */
    protected abstract boolean isPointInitiator(String entryType);

    /**
     * Returns the status string a point should transition to after a response of
     * the given entry type, or {@code null} if the status should remain unchanged.
     */
    protected abstract String statusAfter(String entryType);

    // ── ChannelProjection contract ───────────────────────────────────────────

    @Override
    public ConversationState identity() {
        return new ConversationState(Map.of(), List.of(), List.of(), Map.of());
    }

    @Override
    public ConversationState apply(ConversationState state, MessageView message) {
        try {
            return doApply(state, message);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "apply() caught unexpected exception — state unchanged", e);
            return state;
        }
    }

    // ── dispatch logic ──────────────────────────────────────────────────────

    private ConversationState doApply(ConversationState state, MessageView message) {
        // 1. Parse meta — no sentinel → plain content, return unchanged
        Map<String, String> meta = ChannelMessageMeta.parseMeta(sentinel(), message.content());
        if (meta.isEmpty()) return state;

        // 2. Extract entryType — absent → return unchanged
        String entryType = meta.get(ConversationProtocol.ENTRY_TYPE);
        if (entryType == null) return state;

        // 3. RESTART_CONTEXT → transparent (protocol PP-20260610-073663)
        if (ConversationProtocol.RESTART_CONTEXT.equals(entryType)) return state;

        // 4. Infrastructure dispatch
        return switch (entryType) {
            case ConversationProtocol.MEMO -> handleMemo(state, message, meta);
            case ConversationProtocol.SUB_TASK_REQUEST -> handleSubTaskRequest(state, message, meta);
            case ConversationProtocol.SUB_TASK_FINDING -> handleSubTaskFinding(state, message, meta);
            case ConversationProtocol.SUB_TASK_ERROR -> handleSubTaskError(state, message, meta);
            case ConversationProtocol.FLAG_HUMAN -> handleFlagHuman(state, message, meta);
            default -> handleDomain(state, message, meta, entryType);
        };
    }

    // ── domain dispatch ─────────────────────────────────────────────────────

    private ConversationState handleDomain(ConversationState state, MessageView message,
                                           Map<String, String> meta, String entryType) {
        String role = meta.get(ConversationProtocol.ROLE);
        if (role == null) {
            LOG.log(Level.WARNING, "Message missing role — discarded (entryType={0})", entryType);
            return state;
        }

        if (isPointInitiator(entryType)) {
            return handlePointInitiation(state, message, meta, role, entryType);
        } else {
            return handlePointResponse(state, message, meta, role, entryType);
        }
    }

    private ConversationState handlePointInitiation(ConversationState state, MessageView message,
                                                     Map<String, String> meta, String role,
                                                     String entryType) {
        String pointId = message.correlationId();
        if (pointId == null) {
            LOG.log(Level.WARNING, "Point initiation without correlationId — discarded");
            return state;
        }

        Priority priority = parsePriority(meta.get(ConversationProtocol.PRIORITY));
        String scope = meta.get(ConversationProtocol.SCOPE);
        String location = meta.get(ConversationProtocol.LOCATION);
        if (location != null && location.isBlank()) location = null;
        var classification = new PointClassification(priority, scope, location);

        int round = ChannelMessageMeta.parseInt(meta, ConversationProtocol.ROUND);
        String body = ChannelMessageMeta.bodyContent(sentinel(), message.content());

        var thread = new ArrayList<ThreadEntry>();
        thread.add(new ThreadEntry(pointId, role, round, entryType, body));
        var point = new ConversationPoint(pointId, classification, thread, ConversationProtocol.STATUS_OPEN);

        var points = new LinkedHashMap<>(state.points());
        points.put(pointId, point);

        return new ConversationState(points, new ArrayList<>(state.humanFlags()),
                new ArrayList<>(state.memos()), new LinkedHashMap<>(state.subTaskFindings()));
    }

    private ConversationState handlePointResponse(ConversationState state, MessageView message,
                                                    Map<String, String> meta, String role,
                                                    String entryType) {
        String targetId = message.correlationId();
        if (targetId == null) return state;
        if (!state.points().containsKey(targetId)) {
            LOG.log(Level.WARNING,
                    "Response ({0}) references unknown point: {1} — discarded", entryType, targetId);
            return state;
        }

        ConversationPoint existing = state.points().get(targetId);
        int round = ChannelMessageMeta.parseInt(meta, ConversationProtocol.ROUND);
        String body = ChannelMessageMeta.bodyContent(sentinel(), message.content());

        var thread = new ArrayList<>(existing.thread());
        thread.add(new ThreadEntry(null, role, round, entryType, body));

        String newStatus = statusAfter(entryType);
        String resolvedStatus = newStatus != null ? newStatus : existing.status();
        var updated = new ConversationPoint(existing.id(), existing.classification(), thread, resolvedStatus);

        var points = new LinkedHashMap<>(state.points());
        points.put(targetId, updated);

        return new ConversationState(points, new ArrayList<>(state.humanFlags()),
                new ArrayList<>(state.memos()), new LinkedHashMap<>(state.subTaskFindings()));
    }

    // ── infrastructure handlers ─────────────────────────────────────────────

    private ConversationState handleMemo(ConversationState state, MessageView message,
                                          Map<String, String> meta) {
        String role = meta.getOrDefault(ConversationProtocol.ROLE, "UNKNOWN");
        int round = ChannelMessageMeta.parseInt(meta, ConversationProtocol.ROUND);
        String content = ChannelMessageMeta.bodyContent(sentinel(), message.content());
        var memos = new ArrayList<>(state.memos());
        memos.add(new RoundMemo(role, round, content));
        return new ConversationState(state.points(), new ArrayList<>(state.humanFlags()),
                memos, new LinkedHashMap<>(state.subTaskFindings()));
    }

    private ConversationState handleSubTaskRequest(ConversationState state, MessageView message,
                                                     Map<String, String> meta) {
        String subTaskId = meta.getOrDefault(ConversationProtocol.SUB_TASK_ID,
                message.correlationId() != null ? message.correlationId() : "unknown");
        String taskType = meta.getOrDefault(ConversationProtocol.TASK_TYPE, "CUSTOM");
        String requestingRole = meta.getOrDefault(ConversationProtocol.ROLE, "UNKNOWN");
        String pointId = meta.get(ConversationProtocol.POINT_ID);

        var findings = new LinkedHashMap<>(state.subTaskFindings());
        findings.put(subTaskId, new SubTaskFinding(subTaskId, taskType, requestingRole,
                pointId, null, null, SubTaskStatus.PENDING));

        return new ConversationState(state.points(), new ArrayList<>(state.humanFlags()),
                new ArrayList<>(state.memos()), findings);
    }

    private ConversationState handleSubTaskFinding(ConversationState state, MessageView message,
                                                     Map<String, String> meta) {
        String subTaskId = meta.getOrDefault(ConversationProtocol.SUB_TASK_ID,
                message.correlationId() != null ? message.correlationId() : "unknown");
        String taskType = meta.getOrDefault(ConversationProtocol.TASK_TYPE, "CUSTOM");
        String role = meta.getOrDefault(ConversationProtocol.ROLE, "UNKNOWN");
        String pointId = meta.get(ConversationProtocol.POINT_ID);
        String finding = ChannelMessageMeta.bodyContent(sentinel(), message.content());

        var findings = new LinkedHashMap<>(state.subTaskFindings());
        SubTaskFinding existing = findings.get(subTaskId);

        // Bug fix R2-03: preserve requestedBy from the original REQUEST
        String requestedBy = existing != null ? existing.requestedBy() : role;
        String resolvedPointId = existing != null && existing.pointId() != null ? existing.pointId() : pointId;

        findings.put(subTaskId, new SubTaskFinding(subTaskId, taskType, requestedBy,
                resolvedPointId, finding, null, SubTaskStatus.COMPLETE));

        return new ConversationState(state.points(), new ArrayList<>(state.humanFlags()),
                new ArrayList<>(state.memos()), findings);
    }

    private ConversationState handleSubTaskError(ConversationState state, MessageView message,
                                                   Map<String, String> meta) {
        String subTaskId = meta.getOrDefault(ConversationProtocol.SUB_TASK_ID,
                message.correlationId() != null ? message.correlationId() : "unknown");
        String taskType = meta.getOrDefault(ConversationProtocol.TASK_TYPE, "CUSTOM");
        String role = meta.getOrDefault(ConversationProtocol.ROLE, "UNKNOWN");
        String reason = ChannelMessageMeta.bodyContent(sentinel(), message.content());

        var findings = new LinkedHashMap<>(state.subTaskFindings());
        SubTaskFinding existing = findings.get(subTaskId);

        // Bug fix R2-03: preserve requestedBy from the original REQUEST
        String requestedBy = existing != null ? existing.requestedBy() : role;
        String resolvedPointId = existing != null ? existing.pointId() : null;

        findings.put(subTaskId, new SubTaskFinding(subTaskId, taskType, requestedBy,
                resolvedPointId, null, reason, SubTaskStatus.ERROR));

        return new ConversationState(state.points(), new ArrayList<>(state.humanFlags()),
                new ArrayList<>(state.memos()), findings);
    }

    private ConversationState handleFlagHuman(ConversationState state, MessageView message,
                                                Map<String, String> meta) {
        String content = ChannelMessageMeta.bodyContent(sentinel(),
                Objects.requireNonNullElse(message.content(), ""));
        int round = ChannelMessageMeta.parseInt(meta, ConversationProtocol.ROUND);
        String role = meta.get(ConversationProtocol.ROLE);
        if (role == null) {
            LOG.log(Level.WARNING, "FLAG_HUMAN missing role — discarded");
            return state;
        }

        var points = new LinkedHashMap<>(state.points());
        String targetId = message.correlationId();
        if (targetId != null && points.containsKey(targetId)) {
            ConversationPoint p = points.get(targetId);
            var thread = new ArrayList<>(p.thread());
            thread.add(new ThreadEntry(null, role, round, ConversationProtocol.FLAG_HUMAN, content));
            points.put(targetId, new ConversationPoint(p.id(), p.classification(),
                    thread, ConversationProtocol.STATUS_ESCALATED));
        }

        var flags = new ArrayList<>(state.humanFlags());
        flags.add(new FlagEntry(null, round, role, content));

        return new ConversationState(points, flags,
                new ArrayList<>(state.memos()), new LinkedHashMap<>(state.subTaskFindings()));
    }

    // ── utility ─────────────────────────────────────────────────────────────

    private Priority parsePriority(String s) {
        if (s == null) return Priority.LOW;
        try {
            return Priority.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Priority.LOW;
        }
    }
}
