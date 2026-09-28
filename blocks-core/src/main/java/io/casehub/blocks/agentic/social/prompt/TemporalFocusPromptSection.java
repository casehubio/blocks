package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.speech.PromptContext;
import io.casehub.blocks.speech.PromptSection;
import io.casehub.neocortex.cognitive.index.AttentionItem;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class TemporalFocusPromptSection implements PromptSection {

    private static final int MAX_ITEMS = 5;
    private final List<AttentionItem> items;

    public TemporalFocusPromptSection(List<AttentionItem> items) {
        this.items = items;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        if (items.isEmpty()) return null;
        var sb = new StringBuilder("Temporal awareness:");
        var limit = Math.min(items.size(), MAX_ITEMS);
        for (int i = 0; i < limit; i++) {
            var item = items.get(i);
            sb.append("\n- ").append(item.reason())
              .append(" (salience: ").append(String.format("%.2f", item.salience())).append(")");
        }
        return sb.toString();
    }
}
