package io.casehub.blocks.conversation;

/**
 * A human-escalation flag raised during a conversation.
 *
 * {@code entryId} and {@code round} are null/0 in v1 file-based paths — both are populated
 * only via the channel backend, where the MessageView carries a stable correlationId and round context.
 */
public record FlagEntry(String entryId, int round, String role, String content) {}
