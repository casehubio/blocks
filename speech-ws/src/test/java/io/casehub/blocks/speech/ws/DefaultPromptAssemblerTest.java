package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.ws.protocol.ConversationTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPromptAssemblerTest {

    @Test
    void assemblesWithSystemPromptAndNoHistory() {
        var assembler = new DefaultPromptAssembler("You are a helpful assistant.");
        String result = assembler.assemble("Hello", List.of());
        assertThat(result).contains("You are a helpful assistant.");
        assertThat(result).contains("Hello");
    }

    @Test
    void includesConversationHistory() {
        var assembler = new DefaultPromptAssembler("Be concise.");
        var history = List.of(
                new ConversationTurn("user", "Hi"),
                new ConversationTurn("assistant", "Hello!"));
        String result = assembler.assemble("How are you?", history);
        assertThat(result).contains("Hi");
        assertThat(result).contains("Hello!");
        assertThat(result).contains("How are you?");
    }

    @Test
    void usesDefaultSystemPromptWhenNull() {
        var assembler = new DefaultPromptAssembler(null);
        String result = assembler.assemble("Test", List.of());
        assertThat(result).isNotBlank();
        assertThat(result).contains("Test");
    }

    @Test
    void historyOrderIsPreserved() {
        var assembler = new DefaultPromptAssembler("sys");
        var history = List.of(
                new ConversationTurn("user", "first"),
                new ConversationTurn("assistant", "second"),
                new ConversationTurn("user", "third"));
        String result = assembler.assemble("fourth", history);
        int firstIdx = result.indexOf("first");
        int secondIdx = result.indexOf("second");
        int thirdIdx = result.indexOf("third");
        int fourthIdx = result.indexOf("fourth");
        assertThat(firstIdx).isLessThan(secondIdx);
        assertThat(secondIdx).isLessThan(thirdIdx);
        assertThat(thirdIdx).isLessThan(fourthIdx);
    }
}
