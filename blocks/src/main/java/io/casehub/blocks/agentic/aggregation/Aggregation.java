package io.casehub.blocks.agentic.aggregation;

public final class Aggregation {
    private Aggregation() {}

    public static <T> PassThrough<T> passThrough() { return new PassThrough<>(); }
    public static <T> CollectAll<T> collectAll() { return new CollectAll<>(); }
    public static <T> MajorityVote<T> majorityVote() { return new MajorityVote<>(); }
}
