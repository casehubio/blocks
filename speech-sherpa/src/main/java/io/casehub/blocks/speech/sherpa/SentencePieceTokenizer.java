package io.casehub.blocks.speech.sherpa;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure Java SentencePiece tokenizer — Viterbi unigram segmentation.
 * Ported from Vespa's SentencePiece implementation (Apache 2.0).
 * Reads .model protobuf files directly via CodedInputStream.
 */
public final class SentencePieceTokenizer {

    static final char SPACE_SYMBOL = '▁';

    static final int TYPE_NORMAL = 1;
    static final int TYPE_UNKNOWN = 2;
    static final int TYPE_CONTROL = 3;
    static final int TYPE_USER_DEFINED = 4;
    static final int TYPE_UNUSED = 5;

    private final String[] pieceTexts;
    private final float[] pieceScores;
    private final int[] pieceTypes;
    private final Map<String, Integer> textToId;
    private final Node trieRoot;
    private final float minScore;
    private final float maxScore;

    public SentencePieceTokenizer(Path modelPath) throws IOException {
        var texts = new ArrayList<String>();
        var scores = new ArrayList<Float>();
        var types = new ArrayList<Integer>();

        var stream = CodedInputStream.newInstance(Files.readAllBytes(modelPath));
        while (!stream.isAtEnd()) {
            int tag = stream.readTag();
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                parsePiece(stream, texts, scores, types);
            } else {
                stream.skipField(tag);
            }
        }

        int size = texts.size();
        this.pieceTexts = texts.toArray(new String[0]);
        this.pieceScores = new float[size];
        this.pieceTypes = new int[size];
        this.textToId = HashMap.newHashMap(size);
        this.trieRoot = new Node();
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        for (int i = 0; i < size; i++) {
            this.pieceScores[i] = scores.get(i);
            this.pieceTypes[i] = types.get(i);
            this.textToId.put(this.pieceTexts[i], i);
            if (this.pieceTypes[i] != TYPE_UNUSED) {
                addToTrie(this.pieceTexts[i], i, this.pieceScores[i], this.pieceTypes[i]);
                min = Math.min(this.pieceScores[i], min);
                max = Math.max(this.pieceScores[i], max);
            }
        }
        this.minScore = min;
        this.maxScore = max;
    }

    public int[] encode(String text) {
        if (text.isEmpty()) return new int[0];
        return segment(normalize(text));
    }

    public String decode(int[] ids) {
        var sb = new StringBuilder();
        for (int id : ids) {
            if (id >= 0 && id < pieceTexts.length && pieceTypes[id] != TYPE_CONTROL) {
                sb.append(pieceTexts[id]);
            }
        }
        return denormalize(sb.toString());
    }

    public int vocabSize() {
        return pieceTexts.length;
    }

    public String idToPiece(int id) {
        return pieceTexts[id];
    }

    public int pieceToId(String piece) {
        return textToId.getOrDefault(piece, 0);
    }

    String normalize(String text) {
        var b = new StringBuilder(text.length() + 1);
        boolean queuedSpace = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                queuedSpace = true;
            } else {
                if (queuedSpace) {
                    b.append(SPACE_SYMBOL);
                    queuedSpace = false;
                }
                b.append(c);
            }
        }
        return b.toString();
    }

    String denormalize(String text) {
        String result = text.replace(SPACE_SYMBOL, ' ');
        return !result.isEmpty() && result.charAt(0) == ' ' ? result.substring(1) : result;
    }

    // --- Viterbi segmentation (highest-score path) ---

    private int[] segment(String input) {
        int len = input.length();
        var ends = new SegmentEnd[len + 1];
        ends[0] = new SegmentEnd(TYPE_UNKNOWN, 0, 0f, 0);

        for (int start = 0; start < len; start++) {
            Node node = trieRoot;
            int pos = start;
            while (node != null && pos < len) {
                node = node.children.get(input.charAt(pos));
                pos++;
                int segLen = pos - start;
                if (node != null && node.id >= 0 && node.type != TYPE_UNUSED) {
                    float score = node.type == TYPE_USER_DEFINED
                            ? (segLen * maxScore - 0.1f) : node.score;
                    tryAdd(TYPE_NORMAL, node.id, start, pos, score, ends);
                } else if (segLen == 1) {
                    tryAdd(TYPE_UNKNOWN, 0, start, start + 1, minScore - 10f, ends);
                }
            }
        }

        var ids = new ArrayList<Integer>();
        int pos = len;
        boolean prevUnknown = false;
        while (pos > 0) {
            var end = ends[pos];
            if (end == null) break;
            if (end.type == TYPE_UNKNOWN) {
                if (!prevUnknown) ids.add(end.id);
                prevUnknown = true;
            } else {
                ids.add(end.id);
                prevUnknown = false;
            }
            pos = end.segmentStart;
        }
        Collections.reverse(ids);
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void tryAdd(int type, int id, int start, int end,
                               float score, SegmentEnd[] ends) {
        float newScore = ends[start].pathScore + score;
        if (ends[end] == null || newScore > ends[end].pathScore) {
            ends[end] = new SegmentEnd(type, id, newScore, start);
        }
    }

    // --- Protobuf model parsing ---

    private static void parsePiece(CodedInputStream stream,
                                   ArrayList<String> texts,
                                   ArrayList<Float> scores,
                                   ArrayList<Integer> types) throws IOException {
        int length = stream.readRawVarint32();
        int limit = stream.pushLimit(length);
        String text = "";
        float score = 0;
        int type = TYPE_NORMAL;
        while (!stream.isAtEnd()) {
            int tag = stream.readTag();
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1 -> text = stream.readString();
                case 2 -> score = stream.readFloat();
                case 3 -> type = stream.readEnum();
                default -> stream.skipField(tag);
            }
        }
        stream.popLimit(limit);
        texts.add(text);
        scores.add(score);
        types.add(type);
    }

    // --- Trie ---

    private void addToTrie(String word, int id, float score, int type) {
        Node current = trieRoot;
        for (int i = 0; i < word.length(); i++) {
            current = current.children.computeIfAbsent(word.charAt(i), k -> new Node());
        }
        current.id = id;
        current.score = score;
        current.type = type;
    }

    private static final class Node {
        int id = -1;
        float score;
        int type;
        final Map<Character, Node> children = new HashMap<>();
    }

    private record SegmentEnd(int type, int id, float pathScore, int segmentStart) {
    }
}
