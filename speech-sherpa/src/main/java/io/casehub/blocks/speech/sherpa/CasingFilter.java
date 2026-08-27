package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.TextFilter;

public final class CasingFilter implements TextFilter {
    @Override
    public String apply(String text) {
        if (text == null || text.isEmpty()) {return text;}
        String        lower          = text.toLowerCase();
        StringBuilder sb             = new StringBuilder(lower);
        boolean       capitalizeNext = true;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (capitalizeNext && Character.isLetter(c)) {
                sb.setCharAt(i, Character.toUpperCase(c));
                capitalizeNext = false;
            } else if (c == '.' || c == '!' || c == '?') {
                capitalizeNext = true;
            }
        }
        // Capitalize standalone "i"
        return sb.toString()
                 .replaceAll("\\bi\\b", "I")
                 .replaceAll("\\bi'm\\b", "I'm")
                 .replaceAll("\\bi've\\b", "I've")
                 .replaceAll("\\bi'll\\b", "I'll")
                 .replaceAll("\\bi'd\\b", "I'd");
    }

    @Override
    public String name() {
        return "casing";
    }

    @Override
    public int destructiveness() {
        return 0;
    }
}
