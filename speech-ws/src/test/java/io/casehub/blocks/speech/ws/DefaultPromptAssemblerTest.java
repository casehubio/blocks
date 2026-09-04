package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.ws.protocol.ConversationTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPromptAssemblerTest {

    @Test
    void assemblesWithSystemPromptAndNoHistory() {
        var             assembler = new DefaultPromptAssembler("You are a helpful assistant.");
        AssembledPrompt result    = assembler.assemble("Hello", List.of());
        assertThat(result.systemPrompt()).isEqualTo("You are a helpful assistant.");
        assertThat(result.userPrompt()).contains("Hello");
    }

    @Test
    void includesConversationHistory() {
        var assembler = new DefaultPromptAssembler("Be concise.");
        var history = List.of(
                new ConversationTurn("user", "Hi"),
                new ConversationTurn("assistant", "Hello!"));
        AssembledPrompt result = assembler.assemble("How are you?", history);
        assertThat(result.userPrompt()).contains("Hi");
        assertThat(result.userPrompt()).contains("Hello!");
        assertThat(result.userPrompt()).contains("How are you?");
    }

    @Test
    void usesDefaultSystemPromptWhenNull() {
        var             assembler = new DefaultPromptAssembler(null);
        AssembledPrompt result    = assembler.assemble("Test", List.of());
        assertThat(result.systemPrompt()).isNotBlank();
        assertThat(result.userPrompt()).contains("Test");
    }

    @Test
    void separatesSystemPromptFromUserPrompt() {
        var             assembler = new DefaultPromptAssembler("System instructions");
        AssembledPrompt result    = assembler.assemble("User question", List.of());
        assertThat(result.systemPrompt()).isEqualTo("System instructions");
        assertThat(result.userPrompt()).doesNotContain("System instructions");
    }

    @Test
    void historyOrderIsPreserved() {
        var assembler = new DefaultPromptAssembler("sys");
        var history = List.of(
                new ConversationTurn("user", "first"),
                new ConversationTurn("assistant", "second"),
                new ConversationTurn("user", "third"));
        AssembledPrompt result     = assembler.assemble("fourth", history);
        String          userPrompt = result.userPrompt();
        int             firstIdx   = userPrompt.indexOf("first");
        int             secondIdx  = userPrompt.indexOf("second");
        int             thirdIdx   = userPrompt.indexOf("third");
        int             fourthIdx  = userPrompt.indexOf("fourth");
        assertThat(firstIdx).isLessThan(secondIdx);
        assertThat(secondIdx).isLessThan(thirdIdx);
        assertThat(thirdIdx).isLessThan(fourthIdx);
    }

    @Test
    void speakerLabelAppearsInHistory() {
        var assembler = new DefaultPromptAssembler("sys");
        var history = List.of(
                new ConversationTurn("user", "Hi", "Mark"),
                new ConversationTurn("assistant", "Hello!"));
        AssembledPrompt result = assembler.assemble("How are you?", history);
        assertThat(result.userPrompt()).contains("Mark (User): Hi");
        assertThat(result.userPrompt()).contains("Assistant: Hello!");
    }

    @Test
    void speakerNamesAppendedToSystemPrompt() {
        var assembler = new DefaultPromptAssembler("Be helpful.");
        var history = List.of(
                new ConversationTurn("user", "Hi", "Mark"),
                new ConversationTurn("user", "Hey", "Sarah"));
        AssembledPrompt result = assembler.assemble("Test", history);
        assertThat(result.systemPrompt()).contains("Speaking with: Mark, Sarah.");
    }

    @Test
    void noSpeakersDoesNotModifySystemPrompt() {
        var             assembler = new DefaultPromptAssembler("Be helpful.");
        var             history   = List.of(new ConversationTurn("user", "Hi"));
        AssembledPrompt result    = assembler.assemble("Test", history);
        assertThat(result.systemPrompt()).isEqualTo("Be helpful.");
    }

    @Test
    void conversationTurnWithSpeaker() {
        var turn = new ConversationTurn("user", "hello", "Mark");
        assertThat(turn.speaker()).isEqualTo("Mark");
    }

    @Test
    void conversationTurnWithoutSpeaker() {
        var turn = new ConversationTurn("user", "hello");
        assertThat(turn.speaker()).isNull();
    }
}
