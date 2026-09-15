package io.casehub.blocks.summarisation.narrative;

import java.time.Instant;

public sealed interface DecisionSignal
        permits RoutingDecision, CbrRetrieval, TrustAssessment,
                DeliberationOutcome, StepOutcome, ModelSelection {
    String caseId();
    String stepName();
    Instant timestamp();
}
