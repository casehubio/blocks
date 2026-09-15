package io.casehub.blocks.agentic.judgment;

import java.util.List;

@FunctionalInterface
public interface AgreementPolicy {

    AgreementResult evaluate(List<JudgmentResponse> responses);
}
