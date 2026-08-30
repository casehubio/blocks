package io.casehub.blocks.speech.sherpa;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CosyVoice3Tokenizer implements TtsTokenizer {

    private static final char[] BYTE_ENCODER = buildByteToUnicode();
    private static final Map<Character, Integer> BYTE_DECODER = buildUnicodeToByte();

    private static final Pattern QWEN2_PATTERN = Pattern.compile(
            "(?i:'s|'t|'re|'ve|'m|'ll|'d)"
                    + "|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+"
                    + "|\\p{N}{1,3}"
                    + "| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*"
                    + "|\\s*[\\r\\n]+"
                    + "|\\s+(?!\\S)"
                    + "|\\s+"
    );

    private final Map<String, Integer> vocab;
    private final String[] reverseVocab;
    private final Map<String, Integer> mergeRanks;

    private CosyVoice3Tokenizer(Map<String, Integer> vocab, String[] reverseVocab,
                                Map<String, Integer> mergeRanks) {
        this.vocab = vocab;
        this.reverseVocab = reverseVocab;
        this.mergeRanks = mergeRanks;
    }

    static CosyVoice3Tokenizer load(Path tokenizerDir) throws IOException {
        Path vocabPath = tokenizerDir.resolve("vocab.json");
        Path mergesPath = tokenizerDir.resolve("merges.txt");

        String vocabJson = Files.readString(vocabPath);
        Map<String, Double> rawVocab = new Gson().fromJson(vocabJson,
                new TypeToken<Map<String, Double>>() {}.getType());

        var vocab = HashMap.<String, Integer>newHashMap(rawVocab.size());
        int maxId = 0;
        for (var entry : rawVocab.entrySet()) {
            int id = entry.getValue().intValue();
            vocab.put(entry.getKey(), id);
            maxId = Math.max(maxId, id);
        }

        var reverseVocab = new String[maxId + 1];
        for (var entry : vocab.entrySet()) {
            int id = entry.getValue();
            if (id >= 0 && id < reverseVocab.length) {
                reverseVocab[id] = entry.getKey();
            }
        }

        var mergeRanks = new HashMap<String, Integer>();
        List<String> lines = Files.readAllLines(mergesPath);
        int rank = 0;
        for (String line : lines) {
            if (line.startsWith("#") || line.isBlank()) continue;
            mergeRanks.put(line.trim(), rank++);
        }

        return new CosyVoice3Tokenizer(vocab, reverseVocab, mergeRanks);
    }

    @Override
    public int[] encode(String text) {
        if (text == null || text.isEmpty()) return new int[0];

        var ids = new ArrayList<Integer>();
        for (String piece : preTokenize(text)) {
            String encoded = toByteLevelChars(piece);
            for (String token : bpe(encoded)) {
                Integer id = vocab.get(token);
                if (id != null) ids.add(id);
            }
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    String decode(int[] ids) {
        if (ids == null || ids.length == 0) return "";
        var sb = new StringBuilder();
        for (int id : ids) {
            if (id >= 0 && id < reverseVocab.length && reverseVocab[id] != null) {
                sb.append(reverseVocab[id]);
            }
        }
        return fromByteLevelChars(sb.toString());
    }

    private List<String> preTokenize(String text) {
        var pieces = new ArrayList<String>();
        Matcher m = QWEN2_PATTERN.matcher(text);
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
}
