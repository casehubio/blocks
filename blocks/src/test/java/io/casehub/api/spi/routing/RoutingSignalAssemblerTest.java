package io.casehub.api.spi.routing;

import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingSignalAssemblerTest {

  private static final AgentRoutingContext CONTEXT =
      new AgentRoutingContext(UUID.randomUUID(), "cap", NullNode.instance, "t", List.of(), null, null);
  private static final List<AgentCandidate> EMPTY_ELIGIBLE = List.of();

  @Nested
  class Assembly {

    @Test
    void emptyProvidersReturnsEmptyMap() {
      var assembler = new RoutingSignalAssembler(List.of());
      assertThat(assembler.assemble(CONTEXT, EMPTY_ELIGIBLE)).isEmpty();
    }

    @Test
    void collectsNonNullSignalsKeyedById() {
      var p1 = provider("sig-a", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal.Score(0.8, "good"))));
      var p2 = provider("sig-b", new RoutingSignal(Map.of("w2",
          new RoutingSignal.CandidateSignal.Score(0.6, "ok"))));

      var assembler = new RoutingSignalAssembler(List.of(p1, p2));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result).containsOnlyKeys("sig-a", "sig-b");
      assertThat(result.get("sig-a").candidates().get("w1")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).isEqualTo(0.8);
      assertThat(result.get("sig-b").candidates().get("w2")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).isEqualTo(0.6);
    }

    @Test
    void skipsNullSignals() {
      var p1 = provider("sig-a", null);
      var p2 = provider("sig-b", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal.Score(0.5, null))));

      var assembler = new RoutingSignalAssembler(List.of(p1, p2));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result).containsOnlyKeys("sig-b");
    }
  }

  @Nested
  class Resilience {

    @Test
    void throwingProviderIsSkipped() {
      var good = provider("good", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal.Score(0.9, null))));
      var bad = new RoutingSignalProvider() {
        @Override public String id() { return "bad"; }
        @Override public RoutingSignal evaluate(AgentRoutingContext ctx, List<AgentCandidate> e) {
          throw new RuntimeException("boom");
        }
      };

      var assembler = new RoutingSignalAssembler(List.of(bad, good));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result).containsOnlyKeys("good");
    }
  }

  @Nested
  class ScoreClamping {

    @Test
    void scoresAboveOneAreClampedToOne() {
      var p = provider("over", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal.Score(1.5, "over"))));

      var assembler = new RoutingSignalAssembler(List.of(p));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result.get("over").candidates().get("w1")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).isEqualTo(1.0);
    }

    @Test
    void scoresBelowZeroAreClampedToZero() {
      var p = provider("under", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal.Score(-0.3, "under"))));

      var assembler = new RoutingSignalAssembler(List.of(p));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result.get("under").candidates().get("w1")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).isEqualTo(0.0);
    }

    @Test
    void scoresInRangeAreUnchanged() {
      var p = provider("ok", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal.Score(0.75, null))));

      var assembler = new RoutingSignalAssembler(List.of(p));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result.get("ok").candidates().get("w1")).isInstanceOf(RoutingSignal.CandidateSignal.Score.class).extracting(s -> ((RoutingSignal.CandidateSignal.Score) s).value()).isEqualTo(0.75);
    }
  }

  private static RoutingSignalProvider provider(String id, RoutingSignal signal) {
    return new RoutingSignalProvider() {
      @Override public String id() { return id; }
      @Override public RoutingSignal evaluate(AgentRoutingContext ctx, List<AgentCandidate> e) {
        return signal;
      }
    };
  }
}
