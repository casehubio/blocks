package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.CognitionConfig;
import io.casehub.eidos.api.AgentConstraint;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentPromptContext;
import io.casehub.eidos.api.AgentVoiceProfile;
import io.casehub.eidos.api.ConstraintSeverity;
import io.casehub.eidos.api.SystemPromptRenderer;
import io.casehub.eidos.api.Visibility;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void rendersVoiceProfileWhenPresent() {
        var voice = new AgentVoiceProfile(
                "A glamorous, resourceful Southern belle",
                "southern-belle", "southern-drawl",
                List.of("Why, how delightful!", "Bless your heart!"),
                List.of("warm and effusive"),
                List.of("simply"), List.of(),
                List.of("exclaims when discovering"), null);
        var desc = AgentDescriptor.builder()
                                  .agentId("test").name("Penelope Pitstop").slot("s").tenancyId("t")
                                  .briefing("A glamorous Southern belle.")
                                  .voice(voice)
                                  .build();
        var renderer = new CognitiveSystemPromptRenderer(CognitionConfig.all());
        var result = renderer.render(desc, AgentPromptContext.forFormat(
                SystemPromptRenderer.RenderFormat.MARKDOWN));
        assertTrue(result.content().contains("## Voice"));
        assertTrue(result.content().contains("southern-belle"));
        assertTrue(result.content().contains("Why, how delightful!"));
        assertTrue(result.content().contains("warm and effusive"));
        assertTrue(result.content().contains("exclaims when discovering"));
    }

    @Test
    void rendersPersonasWithBaseVoice() {
        var sneekly = new AgentVoiceProfile(
                null, "obsequious", null,
                List.of("Oh, my DEAR Miss Pitstop!"), List.of("overly helpful"),
                null, null, null, null);
        var claw = new AgentVoiceProfile(
                null, "grandiose", null,
                List.of("Nyah-ha-ha-HA!"), List.of("dramatic monologues"),
                null, null, null, null);
        var voice = new AgentVoiceProfile(
                null, null, null, null, null, null, null,
                List.of("explains schemes"),
                java.util.Map.of("sneekly", sneekly, "claw", claw));
        var desc = AgentDescriptor.builder()
                                  .agentId("test").name("Hooded Claw").slot("s").tenancyId("t")
                                  .briefing("Secret nemesis.")
                                  .voice(voice)
                                  .build();
        var renderer = new CognitiveSystemPromptRenderer(CognitionConfig.all());
        var result = renderer.render(desc, AgentPromptContext.forFormat(
                SystemPromptRenderer.RenderFormat.MARKDOWN));
        assertTrue(result.content().contains("Voice: sneekly") || result.content().contains("Voice: claw"),
                   "Should contain persona voice headings, got:\n" + result.content());
        assertTrue(result.content().contains("obsequious"));
        assertTrue(result.content().contains("grandiose"));
    }

    @Test
    void fallsBackToBriefingWhenNoVoice() {
        var desc = AgentDescriptor.builder()
                                  .agentId("test").name("Agent").slot("s").tenancyId("t")
                                  .briefing("You are a helpful assistant.")
                                  .build();
        var renderer = new CognitiveSystemPromptRenderer(CognitionConfig.all());
        var result = renderer.render(desc, AgentPromptContext.forFormat(
                SystemPromptRenderer.RenderFormat.MARKDOWN));
        assertTrue(result.content().contains("helpful assistant"));
        assertFalse(result.content().contains("## Voice"));
    }


}
