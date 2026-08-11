package io.casehub.blocks.conversation;

import io.casehub.api.model.TaskStatus;

public record SubTaskFinding(
        String subTaskId,
        String taskType,       // string-typed task type (e.g., "VERIFY", "ARBITRATE", "CUSTOM")
        String requestedBy,    // role that requested this sub-task (e.g., "REV", "IMP")
        String pointId,        // null for neutral summaries or custom tasks
        String finding,        // null while PENDING or on ERROR
        String errorReason,    // fixed sanitized string — never exception messages
        TaskStatus status
) {}
