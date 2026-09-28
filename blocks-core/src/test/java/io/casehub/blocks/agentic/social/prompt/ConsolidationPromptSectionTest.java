package io.casehub.blocks.agentic.social.prompt;

import io.casehub.neocortex.mindmap.ConsolidationArtifact;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConsolidationPromptSectionTest {

    @Test
    void emptyArtifactsReturnsNull() {
        var section = new ConsolidationPromptSection(List.of());
        assertThat(section.contribute(null)).isNull();
    }

    @Test
    void rendersGraduatedExperience() {
        var artifact = new ConsolidationArtifact.GraduatedExperience(
            "t1", "n1", "learned negotiation", "episodic", 0.85, "a1", "event", Instant.now());
        var section = new ConsolidationPromptSection(List.of(artifact));
        var result = section.contribute(null);
        assertThat(result).contains("Consolidated knowledge:");
        assertThat(result).contains("learned negotiation");
        assertThat(result).contains("0.85");
    }

    @Test
    void rendersMergePerformed() {
        var artifact = new ConsolidationArtifact.MergePerformed(
            "t1", "keep-1", "removed-2", 0.95, "name+neighbors", Instant.now());
        var section = new ConsolidationPromptSection(List.of(artifact));
        var result = section.contribute(null);
        assertThat(result).contains("Unified concepts:");
    }

    @Test
    void rendersCuriosityQuestion() {
        var artifact = new ConsolidationArtifact.CuriosityQuestion(
            "t1", "n1", "sg1", "Why does X relate to Y?", "They share neighbors", 0.7, Instant.now());
        var section = new ConsolidationPromptSection(List.of(artifact));
        var result = section.contribute(null);
        assertThat(result).contains("Question to explore:");
        assertThat(result).contains("Why does X relate to Y?");
    }

    @Test
    void rendersCommunitySummary() {
        var artifact = new ConsolidationArtifact.CommunitySummaryCreated(
            "t1", "s1", "sg1", "Summary: A, B, C", 5, Instant.now());
        var section = new ConsolidationPromptSection(List.of(artifact));
        var result = section.contribute(null);
        assertThat(result).contains("Community theme:");
        assertThat(result).contains("5 concepts");
    }

    @Test
    void capsAtMaxItemsPerType() {
        var artifacts = java.util.stream.IntStream.range(0, 10)
            .mapToObj(i -> (ConsolidationArtifact) new ConsolidationArtifact.CuriosityQuestion(
                "t1", "n" + i, "sg1", "Q" + i, "D" + i, 1.0 - i * 0.1, Instant.now()))
            .toList();
        var section = new ConsolidationPromptSection(artifacts);
        var result = section.contribute(null);
        long count = result.lines().filter(l -> l.contains("Question to explore:")).count();
        assertThat(count).isEqualTo(5);
    }

    @Test
    void sortsByDescendingScore() {
        var low = new ConsolidationArtifact.CuriosityQuestion(
            "t1", "n1", "sg1", "Low priority", "desc", 0.2, Instant.now());
        var high = new ConsolidationArtifact.CuriosityQuestion(
            "t1", "n2", "sg1", "High priority", "desc", 0.9, Instant.now());
        var section = new ConsolidationPromptSection(List.of(low, high));
        var result = section.contribute(null);
        assertThat(result.indexOf("High priority")).isLessThan(result.indexOf("Low priority"));
    }
}
