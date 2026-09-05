package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.AssembledPrompt;
import io.casehub.blocks.speech.ConversationTurn;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.blocks.speech.SpeechPromptAssembler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SocialPromptAssemblerTest {

    private static final SpeechPromptAssembler BASE =
            (msg, hist) -> new AssembledPrompt("base system", "user: " + msg);

    @Test
    void appendsSectionsToBasePrompt() {
        var section1 = (PromptSection) ctx -> "Section 1";
        var section2 = (PromptSection) ctx -> "Section 2";
        var assembler = new SocialPromptAssembler(BASE, List.of(section1, section2),
                "agent1", "tenant1", () -> "subject1");
        var result = assembler.assemble("hello", List.of());
        assertThat(result.systemPrompt()).isEqualTo("base system\n\nSection 1\n\nSection 2");
        assertThat(result.userPrompt()).isEqualTo("user: hello");
    }

    @Test
    void skipsNullSections() {
        var section = (PromptSection) ctx -> null;
        var assembler = new SocialPromptAssembler(BASE, List.of(section),
                "a", "t", () -> null);
        var result = assembler.assemble("hi", List.of());
        assertThat(result.systemPrompt()).isEqualTo("base system");
    }

    @Test
    void isolatesFailingSections() {
        var failing = (PromptSection) ctx -> { throw new RuntimeException("boom"); };
        var ok = (PromptSection) ctx -> "OK";
        var assembler = new SocialPromptAssembler(BASE, List.of(failing, ok),
                "a", "t", () -> null);
        var result = assembler.assemble("hi", List.of());
        assertThat(result.systemPrompt()).isEqualTo("base system\n\nOK");
    }

    @Test
    void passesContextToSections() {
        var section = (PromptSection) ctx ->
                ctx.agentId() + ":" + ctx.tenantId() + ":" + ctx.subjectId();
        var assembler = new SocialPromptAssembler(BASE, List.of(section),
                "a1", "t1", () -> "s1");
        var result = assembler.assemble("test", List.of());
        assertThat(result.systemPrompt()).contains("a1:t1:s1");
    }

    @Test
    void preservesModelFromDelegate() {
        SpeechPromptAssembler delegate =
                (msg, hist) -> new AssembledPrompt("sys", "usr", "gpt-4");
        var assembler = new SocialPromptAssembler(delegate, List.of(),
                "a", "t", () -> null);
        var result = assembler.assemble("hi", List.of());
        assertThat(result.model()).isEqualTo("gpt-4");
    }

    @Test
    void passesHistoryToDelegate() {
        SpeechPromptAssembler delegate = (msg, hist) ->
                new AssembledPrompt("sys", msg + " turns=" + hist.size());
        var history = List.of(
                new ConversationTurn("user", "hello"),
                new ConversationTurn("assistant", "hi"));
        var assembler = new SocialPromptAssembler(delegate, List.of(),
                "a", "t", () -> null);
        var result = assembler.assemble("test", history);
        assertThat(result.userPrompt()).isEqualTo("test turns=2");
    }
}
