package io.casehub.blocks.agentic.belief;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BeliefRevisionTest {

    private final ConsistencyChecker<String> noContradictions = beliefs -> {
        var all = beliefs.all();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                if (all.get(i).value().startsWith("NOT_") && all.get(i).value().substring(4).equals(all.get(j).value())) return false;
                if (all.get(j).value().startsWith("NOT_") && all.get(j).value().substring(4).equals(all.get(i).value())) return false;
            }
        }
        return true;
    };

    @Nested
    class Expansion {
        @Test
        void addsBelief() {
            var bs = new BeliefSet<String>().expand(Belief.of("sky", "blue"));
            assertThat(bs.contains("sky")).isTrue();
            assertThat(bs.get("sky").value()).isEqualTo("blue");
        }

        @Test
        void replacesExisting() {
            var bs = new BeliefSet<String>()
                    .expand(Belief.of("sky", "blue"))
                    .expand(Belief.of("sky", "grey"));
            assertThat(bs.get("sky").value()).isEqualTo("grey");
            assertThat(bs.size()).isEqualTo(1);
        }
    }

    @Nested
    class Contraction {
        @Test
        void removesBelief() {
            var bs = new BeliefSet<String>()
                    .expand(Belief.of("sky", "blue"))
                    .contract("sky");
            assertThat(bs.contains("sky")).isFalse();
            assertThat(bs.isEmpty()).isTrue();
        }

        @Test
        void contractNonexistentIsNoop() {
            var bs = new BeliefSet<String>().expand(Belief.of("sky", "blue"));
            var contracted = bs.contract("missing");
            assertThat(contracted.size()).isEqualTo(1);
        }
    }

    @Nested
    class Revision {
        @Test
        void consistentRevisionJustAdds() {
            var bs = new BeliefSet<String>()
                    .expand(Belief.of("sky", "blue"))
                    .revise(Belief.of("grass", "green"), noContradictions);
            assertThat(bs.size()).isEqualTo(2);
            assertThat(bs.get("grass").value()).isEqualTo("green");
        }

        @Test
        void inconsistentRevisionRemovesLeastEntrenched() {
            var bs = new BeliefSet<String>()
                    .expand(Belief.of("weather", "sunny", 1))
                    .expand(Belief.of("fact", "NOT_raining", 5));

            var revised = bs.revise(Belief.of("observation", "raining", 3), noContradictions);

            assertThat(revised.contains("observation")).isTrue();
            assertThat(revised.get("observation").value()).isEqualTo("raining");
            assertThat(revised.contains("fact")).isFalse();
            assertThat(revised.contains("weather")).isTrue();
        }

        @Test
        void revisionPreservesHighlyEntrenched() {
            var bs = new BeliefSet<String>()
                    .expand(Belief.of("axiom", "true", 100))
                    .expand(Belief.of("derived", "NOT_false", 1));

            var revised = bs.revise(Belief.of("new", "false", 50), noContradictions);

            assertThat(revised.contains("new")).isTrue();
            assertThat(revised.contains("axiom")).isTrue();
            assertThat(revised.contains("derived")).isFalse();
        }

        @Test
        void alwaysConsistentCheckerKeepsAll() {
            ConsistencyChecker<String> alwaysTrue = beliefs -> true;
            var bs = new BeliefSet<String>()
                    .expand(Belief.of("a", "x"))
                    .expand(Belief.of("b", "y"))
                    .revise(Belief.of("c", "z"), alwaysTrue);
            assertThat(bs.size()).isEqualTo(3);
        }
    }

    @Nested
    class BeliefRecord {
        @Test
        void factoryMethods() {
            var b1 = Belief.of("key", "value");
            assertThat(b1.entrenchment()).isEqualTo(0);

            var b2 = Belief.of("key", "value", 5);
            assertThat(b2.entrenchment()).isEqualTo(5);
        }
    }
}
