package io.casehub.blocks.conversation;

public final class ConversationProtocol {
    private ConversationProtocol() {}

    // Meta key constants
    public static final String ENTRY_TYPE  = "entryType";
    public static final String ROLE        = "role";
    public static final String ROUND       = "round";
    public static final String PRIORITY    = "priority";
    public static final String SCOPE       = "scope";
    public static final String LOCATION    = "location";
    public static final String POINT_ID    = "pointId";
    public static final String SUB_TASK_ID = "subTaskId";
    public static final String TASK_TYPE   = "taskType";

    // Infrastructure entry types
    public static final String SUB_TASK_REQUEST  = "SUB_TASK_REQUEST";
    public static final String SUB_TASK_FINDING  = "SUB_TASK_FINDING";
    public static final String SUB_TASK_ERROR    = "SUB_TASK_ERROR";
    public static final String MEMO              = "MEMO";
    public static final String FLAG_HUMAN        = "FLAG_HUMAN";
    public static final String RESTART_CONTEXT   = "RESTART_CONTEXT";

    // Protocol-level statuses
    public static final String STATUS_OPEN      = "OPEN";
    public static final String STATUS_ESCALATED = "ESCALATED";
}
