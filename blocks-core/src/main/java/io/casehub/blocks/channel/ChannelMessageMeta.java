package io.casehub.blocks.channel;

import java.util.HashMap;
import java.util.Map;

/**
 * Encodes and decodes structured key=value metadata headers in channel message bodies.
 *
 * <p>Format: {@code SENTINEL + "key=value|key=value\n\nbody"}. Each application chooses
 * its own sentinel prefix (e.g. {@code "DHMETA:"}) — the utility handles the format.
 * The SOH byte (U+0001) as the first character of the sentinel is recommended: LLM output
 * never begins with SOH, eliminating ambiguity between structured headers and plain text.
 */
public final class ChannelMessageMeta {

    private ChannelMessageMeta() {}

    /**
     * Parse a sentinel-prefixed header from message content.
     * Returns empty map if the sentinel is absent (plain content — not an error).
     */
    public static Map<String, String> parseMeta(String sentinel, String content) {
        Map<String, String> map = new HashMap<>();
        if (content == null || content.isBlank() || !content.startsWith(sentinel)) return map;
        int headerEnd = content.indexOf("\n\n");
        String headerLine = headerEnd > 0
                ? content.substring(sentinel.length(), headerEnd)
                : content.substring(sentinel.length());
        for (String part : headerLine.split("\\|")) {
            int eq = part.indexOf('=');
            if (eq > 0) map.put(part.substring(0, eq).strip(), part.substring(eq + 1).strip());
        }
        return map;
    }

    /**
     * Strip the sentinel header and return only the body text.
     * Returns content unchanged if sentinel is absent. Returns null if input is null.
     */
    public static String bodyContent(String sentinel, String content) {
        if (content == null || !content.startsWith(sentinel)) return content;
        int headerEnd = content.indexOf("\n\n");
        return headerEnd > 0 ? content.substring(headerEnd + 2) : "";
    }

    /**
     * Encode a sentinel-prefixed message from metadata and body.
     */
    public static String encode(String sentinel, Map<String, String> meta, String body) {
        StringBuilder sb = new StringBuilder(sentinel);
        boolean first = true;
        for (Map.Entry<String, String> e : meta.entrySet()) {
            if (!first) sb.append('|');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        if (body != null && !body.isEmpty()) {
            sb.append("\n\n").append(body);
        }
        return sb.toString();
    }

    /**
     * Parse an integer field from a pre-parsed meta map.
     * Returns 0 if the field is absent or not a valid integer.
     */
    public static int parseInt(Map<String, String> meta, String key) {
        String v = meta.get(key);
        if (v == null) return 0;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return 0; }
    }
}
