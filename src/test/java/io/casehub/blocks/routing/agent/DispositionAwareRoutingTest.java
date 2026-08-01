package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.DispositionAxis;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DispositionAwareRoutingTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final DispositionAwareRouting routing = new DispositionAwareRouting();

  @Test
  void returnsNullWhenNoProfileInContext() {
    var ctx = routingContext(MAPPER.createObjectNode());
    var result = routing.evaluate(ctx, List.of(candidateWith("a1", disposition("collaborative"))));
    assertThat(result).isNull();
  }

  @Test
  void returnsNullWhenNullContext() {
    var ctx = routingContext(NullNode.getInstance());
    var result = routing.evaluate(ctx, List.of(candidateWith("a1", disposition("collaborative"))));
    assertThat(result).isNull();
  }

  @Test
  void exactMatchScoresOne() {
    var ctx = routingContext(profileContext("socialOrient", "collaborative"));
    var candidate = candidateWith("agent-1", disposition("collaborative"));

    var result = routing.evaluate(ctx, List.of(candidate));

    assertThat(result).isNotNull();
    assertThat(result.candidates()).containsKey("agent-1");
    assertThat(result.candidates().get("agent-1")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE).isCloseTo(1.0, within(0.001));
  }

  @Test
  void mismatchScoresZero() {
    var ctx = routingContext(profileContext("socialOrient", "collaborative"));
    var candidate = candidateWith("agent-1", disposition("independent"));

    var result = routing.evaluate(ctx, List.of(candidate));

    assertThat(result).isNotNull();
    assertThat(result.candidates().get("agent-1")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE).isCloseTo(0.0, within(0.001));
  }

  @Test
  void missingAxisScoresHalf() {
    var ctx = routingContext(profileContext("riskAppetite", "cautious"));
    var disp = AgentDisposition.builder().socialOrient("collaborative").build();
    var candidate = candidateWith("agent-1", disp);

    var result = routing.evaluate(ctx, List.of(candidate));

    assertThat(result).isNotNull();
    assertThat(result.candidates().get("agent-1")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE).isCloseTo(0.5, within(0.001));
  }

  @Test
  void multipleAxesAveraged() {
    var context = multiAxisProfile(Map.of(
        "socialOrient", "collaborative",
        "riskAppetite", "cautious"));
    var ctx = routingContext(context);
    var disp = AgentDisposition.builder()
        .socialOrient("collaborative")
        .riskAppetite("bold")
        .build();
    var candidate = candidateWith("agent-1", disp);

    var result = routing.evaluate(ctx, List.of(candidate));

    assertThat(result).isNotNull();
    assertThat(result.candidates().get("agent-1")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE).isCloseTo(0.5, within(0.001));
  }

  @Test
  void multipleCandidatesScored() {
    var ctx = routingContext(profileContext("socialOrient", "collaborative"));
    var match = candidateWith("matcher", disposition("collaborative"));
    var noMatch = candidateWith("other", disposition("independent"));

    var result = routing.evaluate(ctx, List.of(match, noMatch));

    assertThat(result).isNotNull();
    assertThat(result.candidates()).hasSize(2);
    assertThat(result.candidates().get("matcher")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE).isCloseTo(1.0, within(0.001));
    assertThat(result.candidates().get("other")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE).isCloseTo(0.0, within(0.001));
  }

  @Test
  void candidateWithoutDescriptorSkipped() {
    var ctx = routingContext(profileContext("socialOrient", "collaborative"));
    var noDescriptor = new AgentCandidate("bare", Set.of(), 0, AgentHealth.READY, null, null);
    var withDescriptor = candidateWith("real", disposition("collaborative"));

    var result = routing.evaluate(ctx, List.of(noDescriptor, withDescriptor));

    assertThat(result).isNotNull();
    assertThat(result.candidates()).hasSize(1);
    assertThat(result.candidates()).containsKey("real");
  }

  @Test
  void candidateWithoutDispositionSkipped() {
    var ctx = routingContext(profileContext("socialOrient", "collaborative"));
    var noDisposition = candidateWith("no-disp", null);

    var result = routing.evaluate(ctx, List.of(noDisposition));

    assertThat(result).isNull();
  }

  @Test
  void capabilitySpecificProfileTakesPrecedence() {
    var root = MAPPER.createObjectNode();
    var routingNode = root.putObject("_routing").putObject("disposition");
    routingNode.putObject("default").put("socialOrient", "independent");
    routingNode.putObject("analysis").put("socialOrient", "collaborative");

    var ctx = new AgentRoutingContext(
        UUID.randomUUID(), "analysis", root, "t1", List.of(), null, null);
    var candidate = candidateWith("agent-1", disposition("collaborative"));

    var result = routing.evaluate(ctx, List.of(candidate));

    assertThat(result).isNotNull();
    assertThat(result.candidates().get("agent-1")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE).isCloseTo(1.0, within(0.001));
  }

  @Test
  void fallsBackToDefaultProfile() {
    var root = MAPPER.createObjectNode();
    root.putObject("_routing").putObject("disposition")
        .putObject("default").put("socialOrient", "collaborative");

    var ctx = new AgentRoutingContext(
        UUID.randomUUID(), "unknown-cap", root, "t1", List.of(), null, null);
    var candidate = candidateWith("agent-1", disposition("collaborative"));

    var result = routing.evaluate(ctx, List.of(candidate));

    assertThat(result).isNotNull();
    assertThat(result.candidates().get("agent-1")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE).isCloseTo(1.0, within(0.001));
  }

  @Test
  void weightedAxes() {
    var root = MAPPER.createObjectNode();
    var profile = root.putObject("_routing").putObject("disposition").putObject("default");
    var social = profile.putObject("socialOrient");
    social.put("value", "collaborative");
    social.put("weight", 3.0);
    var risk = profile.putObject("riskAppetite");
    risk.put("value", "cautious");
    risk.put("weight", 1.0);

    var ctx = routingContext(root);
    var disp = AgentDisposition.builder()
        .socialOrient("collaborative")
        .riskAppetite("bold")
        .build();
    var candidate = candidateWith("agent-1", disp);

    var result = routing.evaluate(ctx, List.of(candidate));

    assertThat(result).isNotNull();
    assertThat(result.candidates().get("agent-1")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE).isCloseTo(0.75, within(0.001));
  }

  @Test
  void idIsDisposition() {
    assertThat(routing.id()).isEqualTo("disposition");
  }

  @Test
  void profileScoring_allAxesMatch() {
    var profile = new DispositionProfile(Map.of(
        DispositionAxis.SOCIAL_ORIENTATION, "collaborative",
        DispositionAxis.RULE_FOLLOWING, "strict",
        DispositionAxis.RISK_APPETITE, "cautious"));
    var disp = AgentDisposition.builder()
        .socialOrient("collaborative")
        .ruleFollowing("strict")
        .riskAppetite("cautious")
        .build();

    assertThat(DispositionAwareRouting.score(profile, disp)).isCloseTo(1.0, within(0.001));
  }

  @Test
  void profileScoring_noAxesMatch() {
    var profile = new DispositionProfile(Map.of(
        DispositionAxis.SOCIAL_ORIENTATION, "collaborative",
        DispositionAxis.RULE_FOLLOWING, "strict"));
    var disp = AgentDisposition.builder()
        .socialOrient("independent")
        .ruleFollowing("adaptive")
        .build();

    assertThat(DispositionAwareRouting.score(profile, disp)).isCloseTo(0.0, within(0.001));
  }

  // --- helpers ---

  private static AgentRoutingContext routingContext(JsonNode caseContext) {
    return new AgentRoutingContext(UUID.randomUUID(), "cap", caseContext, "t1", List.of(), null, null);
  }

  private static ObjectNode profileContext(String axisKey, String value) {
    var root = MAPPER.createObjectNode();
    root.putObject("_routing").putObject("disposition")
        .putObject("default").put(axisKey, value);
    return root;
  }

  private static ObjectNode multiAxisProfile(Map<String, String> axes) {
    var root = MAPPER.createObjectNode();
    var profile = root.putObject("_routing").putObject("disposition").putObject("default");
    axes.forEach(profile::put);
    return root;
  }

  private static AgentDisposition disposition(String socialOrient) {
    return AgentDisposition.builder().socialOrient(socialOrient).build();
  }

  private static AgentCandidate candidateWith(String id, AgentDisposition disposition) {
    var builder = AgentDescriptor.builder()
        .agentId(id).name(id).slot("test").tenancyId("t1");
    if (disposition != null) {
      builder.disposition(disposition);
    }
    return new AgentCandidate(id, Set.of(), 0, AgentHealth.READY, builder.build(), null);
  }
}
