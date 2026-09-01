package io.casehub.blocks.agentic.social.goal;

import io.casehub.blocks.agentic.social.drive.DriveAxis;
import io.casehub.blocks.agentic.social.drive.DriveIntensity;
import io.casehub.blocks.agentic.social.drive.DriveProfile;
import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.NarrativeFragment;
import io.casehub.blocks.agentic.social.narrative.NarrativeScope;
import io.casehub.blocks.agentic.social.narrative.NarrativeState;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentGoal;
import io.casehub.eidos.api.GoalPriority;
import io.casehub.eidos.api.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NarrativeGoalEscalationPolicyTest {

    private NarrativeGoalEscalationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new NarrativeGoalEscalationPolicy(GoalEscalationConfig.defaults());
    }

    @Test
    void escalates_whenThemeAligns() {
        var theme = theme("explorer-identity", 0.8, Map.of(DriveAxis.CURIOSITY, 0.5));
        var proposal = proposal(DriveAxis.CURIOSITY, 0.7);
        var context = context(narrativeWith(theme), descriptorWithGoals());

        var result = policy.evaluate(proposal, context);

        assertThat(result).isNotNull();
        assertThat(result.priority()).isEqualTo(GoalPriority.PRIMARY);
        assertThat(result.themeLabel()).isEqualTo("explorer-identity");
        assertThat(result.reason()).contains("explorer-identity");
    }

    @Test
    void noEscalation_whenSalienceBelowThreshold() {
        var theme = theme("weak-theme", 0.3, Map.of(DriveAxis.CURIOSITY, 0.5));
        var context = context(narrativeWith(theme), descriptorWithGoals());

        assertThat(policy.evaluate(proposal(DriveAxis.CURIOSITY, 0.7), context)).isNull();
    }

    @Test
    void noEscalation_whenAxisWeightNegative() {
        var theme = theme("suppressor", 0.9, Map.of(DriveAxis.CURIOSITY, -0.5));
        var context = context(narrativeWith(theme), descriptorWithGoals());

        assertThat(policy.evaluate(proposal(DriveAxis.CURIOSITY, 0.7), context)).isNull();
    }

    @Test
    void noEscalation_whenAxisWeightBelowThreshold() {
        var theme = theme("weak-axis", 0.9, Map.of(DriveAxis.CURIOSITY, 0.1));
        var context = context(narrativeWith(theme), descriptorWithGoals());

        assertThat(policy.evaluate(proposal(DriveAxis.CURIOSITY, 0.7), context)).isNull();
    }

    @Test
    void noEscalation_whenAxisAbsent() {
        var theme = theme("other-axis", 0.9, Map.of(DriveAxis.AFFILIATION, 0.8));
        var context = context(narrativeWith(theme), descriptorWithGoals());

        assertThat(policy.evaluate(proposal(DriveAxis.CURIOSITY, 0.7), context)).isNull();
    }

    @Test
    void noEscalation_whenPrimaryCapReached() {
        var theme = theme("strong", 0.9, Map.of(DriveAxis.CURIOSITY, 0.8));
        var existingPrimary = new AgentGoal("existing", "desc", GoalPriority.PRIMARY,
                Visibility.PUBLIC, List.of(),
                Map.of("source", "drive", "driveAxis", "COMPETENCE"));
        var context = context(narrativeWith(theme), descriptorWithGoals(existingPrimary));

        assertThat(policy.evaluate(proposal(DriveAxis.CURIOSITY, 0.7), context)).isNull();
    }

    @Test
    void nonDrivePrimary_doesNotCountTowardCap() {
        var theme = theme("strong", 0.9, Map.of(DriveAxis.CURIOSITY, 0.8));
        var casePrimary = new AgentGoal("case-goal", "desc", GoalPriority.PRIMARY,
                Visibility.PUBLIC, List.of(), null);
        var context = context(narrativeWith(theme), descriptorWithGoals(casePrimary));

        assertThat(policy.evaluate(proposal(DriveAxis.CURIOSITY, 0.7), context)).isNotNull();
    }

    @Test
    void selectsStrongestTheme_whenMultipleQualify() {
        var weakTheme = theme("marginal", 0.65, Map.of(DriveAxis.CURIOSITY, 0.35));
        var strongTheme = theme("dominant", 0.95, Map.of(DriveAxis.CURIOSITY, 0.8));
        var context = context(narrativeWith(weakTheme, strongTheme), descriptorWithGoals());

        var result = policy.evaluate(proposal(DriveAxis.CURIOSITY, 0.7), context);

        assertThat(result).isNotNull();
        assertThat(result.themeLabel()).isEqualTo("dominant");
    }

    @Test
    void noEscalation_whenNoThemes() {
        var context = context(narrativeWith(), descriptorWithGoals());

        assertThat(policy.evaluate(proposal(DriveAxis.CURIOSITY, 0.7), context)).isNull();
    }

    @Test
    void noEscalation_whenMaxPrimaryDriveGoalsIsZero() {
        var zeroCapConfig = new GoalEscalationConfig(0.6, 0.3, 0.3, 2, 2, 2, 0);
        var zeroCap = new NarrativeGoalEscalationPolicy(zeroCapConfig);
        var theme = theme("strong", 0.9, Map.of(DriveAxis.CURIOSITY, 0.8));
        var context = context(narrativeWith(theme), descriptorWithGoals());

        assertThat(zeroCap.evaluate(proposal(DriveAxis.CURIOSITY, 0.7), context)).isNull();
    }

    private DerivedTheme theme(String label, double salience,
                                Map<DriveAxis, Double> weights) {
        return new DerivedTheme("t-" + label, Instant.now(), null, List.of(),
                label, salience, weights, List.of());
    }

    private DriveGoalProposal proposal(DriveAxis axis, double intensity) {
        return new DriveGoalProposal(axis, "goal-" + axis.name().toLowerCase(),
                "desc", "reason", intensity);
    }

    private NarrativeState narrativeWith(DerivedTheme... themes) {
        List<NarrativeFragment> fragments = new ArrayList<>(List.of(themes));
        return new NarrativeState("a1", "t1", NarrativeScope.INDIVIDUAL,
                fragments, Instant.now(), 5);
    }

    private GoalEscalationContext context(NarrativeState narrative,
                                           AgentDescriptor descriptor) {
        var profile = new DriveProfile("a1", "t1",
                Map.of(DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.7, "test")),
                0.7, DriveAxis.CURIOSITY, Instant.now());
        return new GoalEscalationContext(narrative, profile, descriptor);
    }

    private AgentDescriptor descriptorWithGoals(AgentGoal... goals) {
        return AgentDescriptor.builder()
                .agentId("a1").name("Agent").slot("default").tenancyId("t1")
                .goals(List.of(goals)).build();
    }
}
