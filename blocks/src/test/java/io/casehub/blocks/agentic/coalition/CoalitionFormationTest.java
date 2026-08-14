package io.casehub.blocks.agentic.coalition;

import io.casehub.blocks.agentic.AgentRef;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CoalitionFormationTest {

    private AgentRef agent(String name) {
        return new AgentRef.ExternalAgent(name, null);
    }

    private CoalitionContext contextWithCapabilities() {
        return new CoalitionContext(
                List.of(agent("doctor"), agent("nurse"), agent("admin")),
                Map.of(
                        "doctor", Set.of("diagnosis", "prescription"),
                        "nurse", Set.of("vitals", "administration"),
                        "admin", Set.of("scheduling", "records")
                )
        );
    }

    @Nested
    class CapabilityCoverage {
        private final CoalitionEvaluator evaluator = new CapabilityCoverageEvaluator();

        @Test
        void fullCoverageScoresOne() {
            var proposal = new CoalitionProposal("task-1",
                    Set.of("diagnosis", "vitals"),
                    List.of(agent("doctor"), agent("nurse")));
            var score = evaluator.evaluate(proposal, contextWithCapabilities());
            assertThat(score.score()).isEqualTo(1.0);
            assertThat(score.capabilityCoverage()).isEqualTo(1.0);
            assertThat(score.missingCapabilities()).isEmpty();
            assertThat(score.isViable()).isTrue();
        }

        @Test
        void partialCoverageScoresBelow() {
            var proposal = new CoalitionProposal("task-1",
                    Set.of("diagnosis", "vitals", "surgery"),
                    List.of(agent("doctor"), agent("nurse")));
            var score = evaluator.evaluate(proposal, contextWithCapabilities());
            assertThat(score.score()).isLessThan(1.0);
            assertThat(score.missingCapabilities()).containsExactly("surgery");
            assertThat(score.isViable()).isFalse();
        }

        @Test
        void noRequiredCapabilitiesScoresOne() {
            var proposal = new CoalitionProposal("task-1",
                    Set.of(), List.of(agent("doctor")));
            var score = evaluator.evaluate(proposal, contextWithCapabilities());
            assertThat(score.score()).isEqualTo(1.0);
            assertThat(score.isViable()).isTrue();
        }

        @Test
        void unknownAgentHasNoCapabilities() {
            var proposal = new CoalitionProposal("task-1",
                    Set.of("diagnosis"),
                    List.of(agent("unknown")));
            var score = evaluator.evaluate(proposal, contextWithCapabilities());
            assertThat(score.missingCapabilities()).containsExactly("diagnosis");
            assertThat(score.isViable()).isFalse();
        }

        @Test
        void singleAgentCoversMultipleCapabilities() {
            var proposal = new CoalitionProposal("task-1",
                    Set.of("diagnosis", "prescription"),
                    List.of(agent("doctor")));
            var score = evaluator.evaluate(proposal, contextWithCapabilities());
            assertThat(score.score()).isEqualTo(1.0);
            assertThat(score.isViable()).isTrue();
        }
    }

    @Nested
    class ProposalRecord {
        @Test
        void defensiveCopies() {
            var members = new java.util.ArrayList<>(List.of(agent("a")));
            var proposal = new CoalitionProposal("t1", Set.of("x"), members);
            members.clear();
            assertThat(proposal.proposedMembers()).hasSize(1);
        }
    }

    @Nested
    class ScoreRecord {
        @Test
        void viableWhenNoMissing() {
            var score = new CoalitionScore(1.0, 1.0, Set.of(), "ok");
            assertThat(score.isViable()).isTrue();
        }

        @Test
        void notViableWhenMissing() {
            var score = new CoalitionScore(0.5, 0.5, Set.of("x"), "partial");
            assertThat(score.isViable()).isFalse();
        }
    }
}
