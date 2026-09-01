package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface CrossAxisGoalEnricher {
    @Nullable DriveGoalProposal enrich(DriveGoalProposal heuristicProposal,
                                        NarrativeState narrative,
                                        DerivedTheme sourceTheme);
}
