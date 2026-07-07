package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.eidos.api.MatchDegree;
import jakarta.annotation.Priority;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingPromptAssemblerTest {

    private AgentRoutingContext context() {
        return new AgentRoutingContext(UUID.randomUUID(), "analysis", NullNode.instance, "t", List.of());
    }

    private List<AgentCandidate> candidates() {
        return List.of(new AgentCandidate("a", Set.of("analysis"), 0,
                AgentHealth.READY, null, new MatchDegree.None()));
    }

    @Test
    void noSections_returnsNull() {
        var assembler = new RoutingPromptAssembler(List.of());
        assertThat(assembler.assemble(context(), candidates())).isNull();
    }

    @Test
    void singleSection_returnsRenderedContent() {
        RoutingPromptSection section = (ctx, eligible) -> "Historical context: 3 cases";
        var assembler = new RoutingPromptAssembler(List.of(section));
        assertThat(assembler.assemble(context(), candidates()))
                .isEqualTo("Historical context: 3 cases");
    }

    @Test
    void sectionReturnsNull_skipped() {
        RoutingPromptSection nullSection = (ctx, eligible) -> null;
        RoutingPromptSection realSection = (ctx, eligible) -> "data";
        var assembler = new RoutingPromptAssembler(List.of(nullSection, realSection));
        assertThat(assembler.assemble(context(), candidates())).isEqualTo("data");
    }

    @Test
    void sectionReturnsBlank_skipped() {
        RoutingPromptSection blankSection = (ctx, eligible) -> "   ";
        RoutingPromptSection realSection = (ctx, eligible) -> "data";
        var assembler = new RoutingPromptAssembler(List.of(blankSection, realSection));
        assertThat(assembler.assemble(context(), candidates())).isEqualTo("data");
    }

    @Test
    void multipleSections_joinedWithDoubleNewline() {
        RoutingPromptSection s1 = (ctx, eligible) -> "section-1";
        RoutingPromptSection s2 = (ctx, eligible) -> "section-2";
        var assembler = new RoutingPromptAssembler(List.of(s1, s2));
        assertThat(assembler.assemble(context(), candidates()))
                .isEqualTo("section-1\n\nsection-2");
    }

    @Test
    void throwingSection_loggedAndSkipped_otherSectionsSurvive() {
        RoutingPromptSection boom = (ctx, eligible) -> { throw new RuntimeException("fail"); };
        RoutingPromptSection ok = (ctx, eligible) -> "survived";
        var assembler = new RoutingPromptAssembler(List.of(boom, ok));
        assertThat(assembler.assemble(context(), candidates())).isEqualTo("survived");
    }

    @Test
    void allSectionsReturnNull_returnsNull() {
        RoutingPromptSection n1 = (ctx, eligible) -> null;
        RoutingPromptSection n2 = (ctx, eligible) -> null;
        var assembler = new RoutingPromptAssembler(List.of(n1, n2));
        assertThat(assembler.assemble(context(), candidates())).isNull();
    }

    @Test
    void priorityOrdering_lowerValueRendersFirst() {
        @Priority(100)
        class HighPriority implements RoutingPromptSection {
            @Override public String render(AgentRoutingContext c, List<AgentCandidate> e) {
                return "high-priority";
            }
        }
        @Priority(200)
        class LowPriority implements RoutingPromptSection {
            @Override public String render(AgentRoutingContext c, List<AgentCandidate> e) {
                return "low-priority";
            }
        }
        class NoPriority implements RoutingPromptSection {
            @Override public String render(AgentRoutingContext c, List<AgentCandidate> e) {
                return "no-priority";
            }
        }
        // Pass in reverse order — assembler should sort by @Priority
        var assembler = new RoutingPromptAssembler(
                List.of(new NoPriority(), new LowPriority(), new HighPriority()));
        assertThat(assembler.assemble(context(), candidates()))
                .isEqualTo("high-priority\n\nlow-priority\n\nno-priority");
    }
}
