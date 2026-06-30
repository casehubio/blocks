package io.casehub.blocks.agentic.pattern;

import io.casehub.platform.agent.AgentProvider;

public final class Patterns {
    private Patterns() {}

    public static <T> SupervisorBuilder<T> supervisor() { return new SupervisorBuilder<>(); }
    public static <T> SupervisorBuilder<T> supervisor(AgentProvider agentProvider) { return new SupervisorBuilder<>(agentProvider); }
    public static <T> SequenceBuilder<T> sequence() { return new SequenceBuilder<>(); }
    public static <T> LoopBuilder<T> loop() { return new LoopBuilder<>(); }
    public static <T> ParallelBuilder<T> parallel() { return new ParallelBuilder<>(); }
    public static <T> VotingBuilder<T> voting() { return new VotingBuilder<>(); }
    public static <T> DebateBuilder<T> debate() { return new DebateBuilder<>(); }
    public static <T> ConditionalBuilder<T> conditional() { return new ConditionalBuilder<>(); }
    public static <T> HtnBuilder<T> htn() { return new HtnBuilder<>(); }
}
