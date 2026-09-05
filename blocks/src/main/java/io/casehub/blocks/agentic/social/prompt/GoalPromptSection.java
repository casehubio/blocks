package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.goal.DriveGoalProposal;
import io.casehub.blocks.agentic.social.goal.GoalProposalOrchestrator;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class GoalPromptSection implements PromptSection {

    private final GoalProposalOrchestrator goals;

    public GoalPromptSection(GoalProposalOrchestrator goals) {
        this.goals = goals;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        return goals.currentProposals(context.agentId(), context.tenantId())
                .filter(proposals -> !proposals.isEmpty())
                .map(GoalPromptSection::render)
                .orElse(null);
    }

    private static String render(List<DriveGoalProposal> proposals) {
        var sb = new StringBuilder("Your current goals:");
        for (var proposal : proposals) {
            sb.append("\n- ");
            if (proposal.suggestedPriority() != null) {
                sb.append("[").append(proposal.suggestedPriority().name()).append("] ");
            }
            sb.append(proposal.goalDescription());
            sb.append(" (drive: ").append(proposal.axis().name().toLowerCase());
            sb.append(", intensity: ").append(String.format("%.1f", proposal.driveIntensity()));
            sb.append(")");
        }
        return sb.toString();
    }
}
