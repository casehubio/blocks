package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.DiversityStrategy;
import io.casehub.blocks.prompt.ExampleCandidate;

import java.util.List;

public class TopNDiversityStrategy implements DiversityStrategy {

    @Override
    public List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples) {
        return shortlist.stream().limit(maxExamples).toList();
    }
}
