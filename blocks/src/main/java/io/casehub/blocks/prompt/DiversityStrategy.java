package io.casehub.blocks.prompt;

import java.util.List;

@FunctionalInterface
public interface DiversityStrategy {
    List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples);
}
