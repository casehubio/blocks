package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.annotations.Agent;
import io.casehub.blocks.annotations.Debate;
import io.casehub.blocks.annotations.Debater;
import io.casehub.blocks.annotations.Supervisor;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DuplicateBeanNameTest {

    private Index indexClasses(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> clazz : classes) {
            String resourceName = "/" + clazz.getName().replace('.', '/') + ".class";
            try (InputStream stream = clazz.getResourceAsStream(resourceName)) {
                if (stream != null) indexer.index(stream);
            }
        }
        return indexer.complete();
    }

    interface ReviewDebate {
        @Debate(name = "review")
        String review(
                @Debater(role = "a", systemPrompt = "p") AgentRef a);
    }

    interface ReviewSupervisor {
        @Supervisor(name = "review")
        String supervise(
                @Agent(name = "a", systemPrompt = "p") AgentRef a);
    }

    @Test
    void rejects_duplicate_bean_names_across_patterns() throws IOException {
        Index index = indexClasses(ReviewDebate.class, ReviewSupervisor.class);
        var step = new PatternAnnotationStep();

        assertThatThrownBy(() -> step.scan(index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate bean name")
                .hasMessageContaining("review");
    }
}
