package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.StrategyLearningOrchestrator;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import org.jspecify.annotations.Nullable;

public class StrategyPromptSection implements PromptSection {

    private final StrategyLearningOrchestrator strategy;

    public StrategyPromptSection(StrategyLearningOrchestrator strategy) {
        this.strategy = strategy;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        return strategy.currentStrategy(context.agentId(), context.tenantId())
                .map(profile -> {
                    String section = profile.toPromptSection();
                    return section.isEmpty() ? null : section.strip();
                })
                .orElse(null);
    }
}
