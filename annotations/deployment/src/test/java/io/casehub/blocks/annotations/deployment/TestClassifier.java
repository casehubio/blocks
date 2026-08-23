package io.casehub.blocks.annotations.deployment;

import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.api.spi.ClassificationContext;
import io.casehub.api.spi.RiskDecision;
import io.casehub.worker.api.PlannedAction;

class TestClassifier implements ActionRiskClassifier {
    @Override
    public RiskDecision classify(PlannedAction action, ClassificationContext context) {
        return new RiskDecision.Autonomous();
    }
}
