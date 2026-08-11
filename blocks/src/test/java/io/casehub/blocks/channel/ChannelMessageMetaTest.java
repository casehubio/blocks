package io.casehub.blocks.channel;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelMessageMetaTest {

    private static final String SENTINEL = "DHMETA:";

    @Test
    void parseMeta_extractsKeyValuePairs() {
        String content = SENTINEL + "entryType=RAISE|round=1|pointId=P1\n\nbody text";
        Map<String, String> meta = ChannelMessageMeta.parseMeta(SENTINEL, content);
        assertThat(meta).containsEntry("entryType", "RAISE");
        assertThat(meta).containsEntry("round", "1");
        assertThat(meta).containsEntry("pointId", "P1");
    }

    @Test
    void parseMeta_returnsEmptyMapWhenSentinelAbsent() {
        assertThat(ChannelMessageMeta.parseMeta(SENTINEL, "plain text")).isEmpty();
    }

    @Test
    void parseMeta_returnsEmptyMapForNull() {
        assertThat(ChannelMessageMeta.parseMeta(SENTINEL, null)).isEmpty();
    }

    @Test
    void parseMeta_returnsEmptyMapForBlank() {
        assertThat(ChannelMessageMeta.parseMeta(SENTINEL, "  ")).isEmpty();
    }

    @Test
    void parseMeta_headerOnlyNoBody() {
        String content = SENTINEL + "entryType=MEMO|round=3";
        Map<String, String> meta = ChannelMessageMeta.parseMeta(SENTINEL, content);
        assertThat(meta).containsEntry("entryType", "MEMO");
        assertThat(meta).containsEntry("round", "3");
    }

    @Test
    void bodyContent_stripsHeader() {
        String content = SENTINEL + "entryType=RAISE\n\nThe actual body";
        assertThat(ChannelMessageMeta.bodyContent(SENTINEL, content)).isEqualTo("The actual body");
    }

    @Test
    void bodyContent_returnsContentUnchangedWhenNoSentinel() {
        assertThat(ChannelMessageMeta.bodyContent(SENTINEL, "plain text")).isEqualTo("plain text");
    }

    @Test
    void bodyContent_returnsNullForNull() {
        assertThat(ChannelMessageMeta.bodyContent(SENTINEL, null)).isNull();
    }

    @Test
    void bodyContent_returnsEmptyWhenHeaderOnlyNoBody() {
        assertThat(ChannelMessageMeta.bodyContent(SENTINEL, SENTINEL + "key=val")).isEmpty();
    }

    @Test
    void parseInt_returnsValueForValidInt() {
        assertThat(ChannelMessageMeta.parseInt(Map.of("round", "3"), "round")).isEqualTo(3);
    }

    @Test
    void parseInt_returnsZeroForMissingKey() {
        assertThat(ChannelMessageMeta.parseInt(Map.of(), "round")).isZero();
    }

    @Test
    void parseInt_returnsZeroForNonNumeric() {
        assertThat(ChannelMessageMeta.parseInt(Map.of("round", "abc"), "round")).isZero();
    }

    @Test
    void encode_producesCorrectFormat() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("entryType", "RAISE");
        meta.put("round", "1");
        String result = ChannelMessageMeta.encode(SENTINEL, meta, "body text");
        assertThat(result).startsWith(SENTINEL);
        assertThat(result).contains("entryType=RAISE");
        assertThat(result).contains("round=1");
        assertThat(result).endsWith("\n\nbody text");
    }

    @Test
    void encode_noBody_omitsDoubleNewline() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("key", "val");
        String result = ChannelMessageMeta.encode(SENTINEL, meta, null);
        assertThat(result).doesNotContain("\n\n");
    }

    @Test
    void roundTrip_parseMatchesEncode() {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("entryType", "RAISE");
        original.put("round", "2");
        String encoded = ChannelMessageMeta.encode(SENTINEL, original, "hello");
        Map<String, String> parsed = ChannelMessageMeta.parseMeta(SENTINEL, encoded);
        assertThat(parsed).containsAllEntriesOf(original);
        assertThat(ChannelMessageMeta.bodyContent(SENTINEL, encoded)).isEqualTo("hello");
    }
}
