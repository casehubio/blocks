package io.casehub.blocks.memory;

import java.util.List;

public sealed interface HygieneTick {
    record Idle(String reason) implements HygieneTick {}
    record Completed(int consolidated, int evicted, int totalScored,
                     List<RetentionScore> scores) implements HygieneTick {
        public Completed { scores = List.copyOf(scores); }
    }
    record Failed(String reason) implements HygieneTick {}
}
