package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.memory.ReflectionEntry;
import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class ReflectionPromptSection implements PromptSection {

    private static final int MAX_ITEMS = 5;
    private final List<ReflectionEntry> entries;

    public ReflectionPromptSection(List<ReflectionEntry> entries) {
        this.entries = entries;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        if (entries.isEmpty()) return null;
        var sb = new StringBuilder("Learned heuristics:");
        var limit = Math.min(entries.size(), MAX_ITEMS);
        for (int i = 0; i < limit; i++) {
            sb.append("\n- ").append(entries.get(i).insight());
        }
        return sb.toString();
    }
}
