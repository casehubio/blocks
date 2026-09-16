package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.CognitionConfig;
import io.casehub.eidos.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveSystemPromptRendererTest {

    private static AgentDescriptor descriptor(String name, String briefing,
                                               List<AgentConstraint> constraints) {
        return AgentDescriptor.builder()
                .agentId("test-agent")
                .name(name)
                .slot("test-slot")
                .tenancyId("test-tenant")
                .briefing(briefing)
                .constraints(constraints)
                .build();
    }

    @Test
    void rendersIdentityAndBriefing() {
        var desc = descriptor("Penelope Pitstop",
                "A glamorous Southern belle. You speak with a Southern drawl.",
                List.of());
        var renderer = new CognitiveSystemPromptRenderer(CognitionConfig.all());
        var result = renderer.render(desc, AgentPromptContext.forFormat(
                SystemPromptRenderer.RenderFormat.MARKDOWN));
        assertTrue(result.content().contains("Penelope Pitstop"));
        assertTrue(result.content().contains("Southern belle"));
        assertEquals(SystemPromptRenderer.RenderFormat.MARKDOWN, result.format());
        assertFalse(result.enriched());
    }

    @Test
    void rendersOnlyHardConstraints() {
        var hard = new AgentConstraint("no-break", "Never break cover",
                Visibility.PUBLIC, ConstraintSeverity.HARD);
        var soft = new AgentConstraint("be-polite", "Always be polite",
                Visibility.PUBLIC, ConstraintSeverity.SOFT);
        var desc = descriptor("Agent", "Identity.", List.of(hard, soft));
        var renderer = new CognitiveSystemPromptRenderer(CognitionConfig.all());
        var result = renderer.render(desc, AgentPromptContext.forFormat(
                SystemPromptRenderer.RenderFormat.MARKDOWN));
        assertTrue(result.content().contains("Never break cover"));
        assertFalse(result.content().contains("Always be polite"));
    }

    @Test
    void primeDirectivesHeading() {
        var hard = new AgentConstraint("no-break", "Never break cover",
                Visibility.PUBLIC, ConstraintSeverity.HARD);
        var desc = descriptor("Agent", "Identity.", List.of(hard));
        var renderer = new CognitiveSystemPromptRenderer(CognitionConfig.all());
        var result = renderer.render(desc, AgentPromptContext.forFormat(
                SystemPromptRenderer.RenderFormat.MARKDOWN));
        assertTrue(result.content().contains("Prime Directives"));
    }

    @Test
    void includesCognitivePreamble() {
        var desc = descriptor("Agent", "Identity.", List.of());
        var renderer = new CognitiveSystemPromptRenderer(CognitionConfig.all());
        var result = renderer.render(desc, AgentPromptContext.forFormat(
                SystemPromptRenderer.RenderFormat.MARKDOWN));
        assertTrue(result.content().contains("inner life"));
        assertTrue(result.content().contains("emotional state"));
    }

    @Test
    void noHardConstraints_noPrimeDirectivesSection() {
        var soft = new AgentConstraint("be-polite", "Always be polite",
                Visibility.PUBLIC, ConstraintSeverity.SOFT);
        var desc = descriptor("Agent", "Identity.", List.of(soft));
        var renderer = new CognitiveSystemPromptRenderer(CognitionConfig.all());
        var result = renderer.render(desc, AgentPromptContext.forFormat(
                SystemPromptRenderer.RenderFormat.MARKDOWN));
        assertFalse(result.content().contains("Prime Directives"));
    }
}
