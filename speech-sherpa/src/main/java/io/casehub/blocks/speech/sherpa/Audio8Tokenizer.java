package io.casehub.blocks.speech.sherpa;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Audio8Tokenizer {

    private static final char[] BYTE_ENCODER = buildByteToUnicode();
    private static final Map<Character, Integer> BYTE_DECODER = buildUnicodeToByte();

    private static final Pattern GPT2_PATTERN = Pattern.compile(
            "[^\\r\\n\\p{L}\\p{N}]?[\\p{Lu}\\p{Lt}\\p{Lm}\\p{Lo}\\p{M}]*[\\p{Ll}\\p{Lm}\\p{Lo}\\p{M}]+"
                    + "|[^\\r\\n\\p{L}\\p{N}]?[\\p{Lu}\\p{Lt}\\p{Lm}\\p{Lo}\\p{M}]+[\\p{Ll}\\p{Lm}\\p{Lo}\\p{M}]*"
                    + "|\\p{N}"
                    + "| ?[^\\s\\p{L}\\p{N}]+[\\r\\n/]*"
                    + "|\\s*[\\r\\n]+"
                    + "|\\s+(?!\\S)"
                    + "|\\s+"
    );

    private final Map<String, Integer> vocab;
    private final String[] reverseVocab;
    private final Map<String, Integer> mergeRanks;
    private final List<String> addedTokensSorted;
    private final Set<String> addedTokenSet;
    private final boolean usePreTokenizerRegex;

    private Audio8Tokenizer(Map<String, Integer> vocab, String[] reverseVocab,
                            Map<String, Integer> mergeRanks, List<String> addedTokensSorted,
                            boolean usePreTokenizerRegex) {
        this.vocab = vocab;
        this.reverseVocab = reverseVocab;
        this.mergeRanks = mergeRanks;
        this.addedTokensSorted = addedTokensSorted;
        this.addedTokenSet = new HashSet<>(addedTokensSorted);
        this.usePreTokenizerRegex = usePreTokenizerRegex;
    }

    static Audio8Tokenizer load(Path tokenizerJson) throws IOException {
        var root = new Gson().fromJson(Files.readString(tokenizerJson), JsonObject.class);
        var model = root.getAsJsonObject("model");

        var vocabObj = model.getAsJsonObject("vocab");
        var vocab = HashMap.<String, Integer>newHashMap(vocabObj.size());
        int maxId = 0;
        for (var entry : vocabObj.entrySet()) {
            int id = entry.getValue().getAsInt();
            vocab.put(entry.getKey(), id);
            maxId = Math.max(maxId, id);
        }

        var addedTokens = new ArrayList<String>();
        var addedArr = root.getAsJsonArray("added_tokens");
        if (addedArr != null) {
            for (JsonElement elem : addedArr) {
                var obj = elem.getAsJsonObject();
                int id = obj.get("id").getAsInt();
                String content = obj.get("content").getAsString();
                boolean special = obj.has("special") && obj.get("special").getAsBoolean();
                vocab.put(content, id);
                maxId = Math.max(maxId, id);
                if (special) addedTokens.add(content);
            }
        }
        addedTokens.sort((a, b) -> b.length() - a.length());

        var reverseVocab = new String[maxId + 1];
        for (var entry : vocab.entrySet()) {
            int id = entry.getValue();
            if (id >= 0 && id < reverseVocab.length) {
                reverseVocab[id] = entry.getKey();
            }
        }

        var mergeRanks = new HashMap<String, Integer>();
        var mergesArr = model.getAsJsonArray("merges");
        if (mergesArr != null) {
            for (int i = 0; i < mergesArr.size(); i++) {
                var mergeEntry = mergesArr.get(i);
                String key;
                if (mergeEntry.isJsonArray()) {
                    var pair = mergeEntry.getAsJsonArray();
                    key = pair.get(0).getAsString() + " " + pair.get(1).getAsString();
                } else {
                    key = mergeEntry.getAsString();
                }
                mergeRanks.put(key, i);
            }
        }

        boolean hasRegex = hasPreTokenizerRegex(root.get("pre_tokenizer"));

        return new Audio8Tokenizer(vocab, reverseVocab, mergeRanks, addedTokens, hasRegex);
    }

    int[] encode(String text) {
        if (text == null || text.isEmpty()) return new int[0];

        var ids = new ArrayList<Integer>();
        for (var segment : splitAtAddedTokens(text)) {
            if (segment.added) {
                Integer id = vocab.get(segment.text);
                if (id != null) ids.add(id);
            } else {
                encodeText(segment.text, ids);
            }
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    String decode(int[] ids) {
        if (ids == null || ids.length == 0) return "";
        var sb = new StringBuilder();
        for (int id : ids) {
            if (id >= 0 && id < reverseVocab.length && reverseVocab[id] != null) {
                String token = reverseVocab[id];
                if (addedTokenSet.contains(token)) {
                    sb.append(token);
                } else {
                    sb.append(token);
                }
            }
        }
        return fromByteLevelChars(sb.toString());
    }

    private void encodeText(String text, List<Integer> ids) {
        var pieces = usePreTokenizerRegex ? regexSplit(text) : List.of(text);
        for (String piece : pieces) {
            String encoded = toByteLevelChars(piece);
            for (String token : bpe(encoded)) {
                Integer id = vocab.get(token);
                if (id != null) ids.add(id);
            }
        }
    }

    private List<Segment> splitAtAddedTokens(String text) {
        if (addedTokensSorted.isEmpty()) return List.of(new Segment(text, false));

        var segments = new ArrayList<Segment>();
        int pos = 0;
        int len = text.length();
        int start = 0;

        while (pos < len) {
            String match = null;
            for (String token : addedTokensSorted) {
                if (pos + token.length() <= len && text.startsWith(token, pos)) {
                    match = token;
                    break;
                }
            }
            if (match != null) {
                if (pos > start) segments.add(new Segment(text.substring(start, pos), false));
                segments.add(new Segment(match, true));
                pos += match.length();
                start = pos;
            } else {
                pos++;
            }
        }
        if (start < len) segments.add(new Segment(text.substring(start), false));
        return segments;
    }

    private List<String> regexSplit(String text) {
        var pieces = new ArrayList<String>();
        Matcher m = GPT2_PATTERN.matcher(text);
        while (m.find()) pieces.add(m.group());
        return pieces;
    }

    private String toByteLevelChars(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var sb = new StringBuilder(bytes.length);
        for (byte b : bytes) sb.append(BYTE_ENCODER[b & 0xFF]);
        return sb.toString();
    }

    private String fromByteLevelChars(String text) {
        var bytes = new byte[text.length()];
        int idx = 0;
        for (int i = 0; i < text.length(); i++) {
            Integer b = BYTE_DECODER.get(text.charAt(i));
            if (b != null) bytes[idx++] = b.byteValue();
        }
        return new String(bytes, 0, idx, StandardCharsets.UTF_8);
    }

    private List<String> bpe(String token) {
        if (token.isEmpty()) return List.of();
        if (token.length() == 1) {
            return vocab.containsKey(token) ? List.of(token) : List.of();
        }

        var symbols = new ArrayList<String>();
        for (int i = 0; i < token.length(); ) {
            int cp = token.codePointAt(i);
            symbols.add(new String(Character.toChars(cp)));
            i += Character.charCount(cp);
        }

        while (symbols.size() > 1) {
            int bestRank = Integer.MAX_VALUE;
            int bestIdx = -1;
            for (int i = 0; i < symbols.size() - 1; i++) {
                String pair = symbols.get(i) + " " + symbols.get(i + 1);
                Integer rank = mergeRanks.get(pair);
                if (rank != null && rank < bestRank) {
                    bestRank = rank;
                    bestIdx = i;
                }
            }
            if (bestIdx == -1) break;

            String left = symbols.get(bestIdx);
            String right = symbols.get(bestIdx + 1);
            String merged = left + right;
            var next = new ArrayList<String>();
            int i = 0;
            while (i < symbols.size()) {
                if (i < symbols.size() - 1
                        && symbols.get(i).equals(left)
                        && symbols.get(i + 1).equals(right)) {
                    next.add(merged);
                    i += 2;
                } else {
                    next.add(symbols.get(i));
                    i++;
                }
            }
            symbols = next;
        }
        return symbols;
    }

    private static char[] buildByteToUnicode() {
        var table = new char[256];
        int n = 0;
        for (int b = 0; b < 256; b++) {
            if ((b >= 0x21 && b <= 0x7E) || (b >= 0xA1 && b <= 0xAC) || (b >= 0xAE && b <= 0xFF)) {
                table[b] = (char) b;
            } else {
                table[b] = (char) (256 + n);
                n++;
            }
        }
        return table;
    }

    private static Map<Character, Integer> buildUnicodeToByte() {
        var map = HashMap.<Character, Integer>newHashMap(256);
        char[] table = buildByteToUnicode();
        for (int b = 0; b < 256; b++) map.put(table[b], b);
        return map;
    }

    private static boolean hasPreTokenizerRegex(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        var obj = element.getAsJsonObject();
        String type = obj.get("type").getAsString();
        if ("Sequence".equals(type)) {
            var arr = obj.getAsJsonArray("pretokenizers");
            if (arr != null) {
                for (JsonElement e : arr) {
                    if (hasPreTokenizerRegex(e)) return true;
                }
            }
            return false;
        }
        return "Split".equals(type) && obj.has("pattern");
    }

    private record Segment(String text, boolean added) {}
}
