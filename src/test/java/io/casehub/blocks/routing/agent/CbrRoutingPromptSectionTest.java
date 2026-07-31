package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.eidos.api.MatchDegree;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CbrRoutingPromptSectionTest {

  private final CbrRoutingPromptSection section = new CbrRoutingPromptSection();

  private AgentRoutingContext context(String cap, List<RetrievedExperience> experiences) {
    return new AgentRoutingContext(
        UUID.randomUUID(), cap, NullNode.instance, "test-tenant", experiences, null, null);
  }

  private AgentCandidate candidate(String id) {
    return new AgentCandidate(
        id, Set.of("analysis"), 0, AgentHealth.READY, null, new MatchDegree.None());
  }

  @Test
  void emptyExperiencesReturnsNull() {
    var result = section.render(context("cap", List.of()), List.of(candidate("a")));
    assertThat(result).isNull();
  }

  @Test
  void noMatchingCapabilityReturnsNull() {
    var exp =
        new RetrievedExperience(
            "problem",
            "solution",
            "COMPLETED",
            0.9,
            0.85,
            Map.of(),
            List.of(
                new ExperiencePlanStep(
                    "binding", "other-cap", "agent-a", "SUCCESS", 0, Map.of())),
            Map.of());

    var result =
        section.render(
            context("analysis", List.of(exp)), List.of(candidate("agent-a")));
    assertThat(result).isNotNull();
    assertThat(result).contains("similar past cases");
  }

  @Test
  void formatsMatchingExperiences() {
    var exp =
        new RetrievedExperience(
            "AML investigation",
            "Assigned to agent-a",
            "COMPLETED",
            0.9,
            0.85,
            Map.of(),
            List.of(
                new ExperiencePlanStep(
                    "assess-risk", "analysis", "agent-a", "SUCCESS", 0, Map.of())),
            Map.of());

    var result =
        section.render(
            context("analysis", List.of(exp)), List.of(candidate("agent-a")));

    assertThat(result).isNotNull();
    assertThat(result).contains("Historical context");
    assertThat(result).contains("\"agent-a\"");
    assertThat(result).contains("100% success");
    assertThat(result).contains("AML investigation");
  }

  @Test
  void filtersToEligibleCandidatesOnly() {
      var exp =
              new RetrievedExperience(
                      "problem",
                      "solution",
                      "COMPLETED",
                      0.9,
                      0.85,
                      Map.of(),
                      List.of(
                              new ExperiencePlanStep(
                                      "binding", "analysis", "not-eligible", "SUCCESS", 0, Map.of())),
                      Map.of());

      var result =
              section.render(
                      context("analysis", List.of(exp)), List.of(candidate("agent-a")));
      assertThat(result).isNull();
  }

  @Test
  void multipleOutcomesShownPerAgent() {
    var exp =
        new RetrievedExperience(
            "problem",
            "solution",
            "COMPLETED",
            0.9,
            0.85,
            Map.of(),
            List.of(
                new ExperiencePlanStep(
                    "b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
                new ExperiencePlanStep(
                    "b2", "analysis", "agent-a", "FAILURE", 0, Map.of())),
            Map.of());

    var result =
        section.render(
            context("analysis", List.of(exp)), List.of(candidate("agent-a")));

    assertThat(result).isNotNull();
    assertThat(result).contains("\"agent-a\": 2 cases");
    assertThat(result).contains("50% success");
  }

    @Test
    void adaptedStepShowsAnnotation() {
        var exp =
                new RetrievedExperience(
                        "AML investigation",
                        "Assigned to agent-a",
                        "COMPLETED",
                        0.9,
                        0.85,
                        Map.of(),
                        List.of(
                                new ExperiencePlanStep(
                                        "assess-risk", "analysis", "agent-a", "SUCCESS", 0, Map.of(),
                                        "BOOSTED", "high relevance")),
                        Map.of());

        var result =
                section.render(
                        context("analysis", List.of(exp)), List.of(candidate("agent-a")));

        assertThat(result).isNotNull();
        assertThat(result).contains("[BOOSTED: high relevance]");
    }

  @Test
  void retainedStepNotAnnotated() {
    var exp =
            new RetrievedExperience(
                    "AML investigation",
                    "Assigned to agent-a",
                    "COMPLETED",
                    0.9,
                    0.85,
                    Map.of(),
                    List.of(
                            new ExperiencePlanStep(
                                    "assess-risk", "analysis", "agent-a", "SUCCESS", 0, Map.of(),
                                    "RETAINED", null)),
                    Map.of());

    var result =
            section.render(
                    context("analysis", List.of(exp)), List.of(candidate("agent-a")));

    assertThat(result).isNotNull();
    assertThat(result).doesNotContain("[RETAINED");
    assertThat(result).contains("SUCCESS");
  }

  @Test
  void addedStepExcludedFromOutcomeCount() {
    var exp =
            new RetrievedExperience(
                    "AML investigation",
                    "solution",
                    "COMPLETED",
                    0.9,
                    0.85,
                    Map.of(),
                    List.of(
                            new ExperiencePlanStep(
                                    "b1", "analysis", "agent-a", "SUCCESS", 0, Map.of(),
                                    "ADDED", "adapter recommendation"),
                            new ExperiencePlanStep(
                                    "b2", "analysis", "agent-b", "SUCCESS", 0, Map.of(),
                                    "RETAINED", null)),
                    Map.of());

    var result =
            section.render(
                    context("analysis", List.of(exp)),
                    List.of(candidate("agent-a"), candidate("agent-b")));

    assertThat(result).isNotNull();
    assertThat(result).contains("\"agent-b\": 1 cases");
    assertThat(result).doesNotContain("\"agent-a\": 1 cases");
  }

  @Test
  void substitutedStepExcludedFromOutcomeCount() {
    var exp =
            new RetrievedExperience(
                    "AML investigation",
                    "solution",
                    "COMPLETED",
                    0.9,
                    0.85,
                    Map.of(),
                    List.of(
                            new ExperiencePlanStep(
                                    "b1", "analysis", "agent-a", "SUCCESS", 0, Map.of(),
                                    "SUBSTITUTED", "replaced original-worker"),
                            new ExperiencePlanStep(
                                    "b2", "analysis", "agent-b", "FAILURE", 0, Map.of(),
                                    "RETAINED", null)),
                    Map.of());

    var result =
            section.render(
                    context("analysis", List.of(exp)),
                    List.of(candidate("agent-a"), candidate("agent-b")));

    assertThat(result).isNotNull();
    assertThat(result).contains("\"agent-b\": 1 cases");
    assertThat(result).doesNotContain("\"agent-a\": 1 cases");
  }


}
