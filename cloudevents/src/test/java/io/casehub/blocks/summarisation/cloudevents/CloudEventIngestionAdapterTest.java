package io.casehub.blocks.summarisation.cloudevents;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CloudEventIngestionAdapterTest {

    static final EventLevel LEVEL = new EventLevel("input", 0);

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(byte[] data) {
        var json = new String(data, StandardCharsets.UTF_8);
        var map = new java.util.LinkedHashMap<String, Object>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        for (var entry : json.split(",")) {
            var parts = entry.split(":", 2);
            var key = parts[0].trim().replace("\"", "");
            var val = parts[1].trim().replace("\"", "");
            try {
                map.put(key, Double.parseDouble(val));
            } catch (NumberFormatException e) {
                map.put(key, val);
            }
        }
        return map;
    }

    @Test
    void ingests_matchingCloudEvent_publishesLevelEvent() {
        var bus = new EventStreamBus<Map<String, Object>>();
        var captured = new ArrayList<LevelEvent<Map<String, Object>>>();
        bus.subscribe(e -> true, captured::add);

        var adapter = new CloudEventIngestionAdapter<>(
                bus, LEVEL, "io.casehub.test.",
                CloudEventIngestionAdapterTest::parseJson);

        var ce = CloudEventBuilder.v1()
                .withId("id-1")
                .withType("io.casehub.test.scan")
                .withSource(URI.create("/test"))
                .withData("application/json",
                        "{\"packageId\":\"PKG-1\"}".getBytes(StandardCharsets.UTF_8))
                .withExtension("tenancyid", "tenant-1")
                .withTime(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .build();

        adapter.accept(ce);

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).tenancyId()).isEqualTo("tenant-1");
        assertThat(captured.get(0).payload()).containsEntry("packageId", "PKG-1");
        assertThat(captured.get(0).level()).isEqualTo(LEVEL);
    }

    @Test
    void ignores_nonMatchingCloudEventType() {
        var bus = new EventStreamBus<Map<String, Object>>();
        var captured = new ArrayList<LevelEvent<Map<String, Object>>>();
        bus.subscribe(e -> true, captured::add);

        var adapter = new CloudEventIngestionAdapter<>(
                bus, LEVEL, "io.casehub.test.",
                CloudEventIngestionAdapterTest::parseJson);

        var ce = CloudEventBuilder.v1()
                .withId("id-2")
                .withType("io.other.event.v1")
                .withSource(URI.create("/test"))
                .withData("application/json",
                        "{\"key\":\"val\"}".getBytes(StandardCharsets.UTF_8))
                .build();

        adapter.accept(ce);

        assertThat(captured).isEmpty();
    }

    @Test
    void extracts_timestampFromCloudEvent() {
        var bus = new EventStreamBus<Map<String, Object>>();
        var captured = new ArrayList<LevelEvent<Map<String, Object>>>();
        bus.subscribe(e -> true, captured::add);

        var adapter = new CloudEventIngestionAdapter<>(
                bus, LEVEL, "io.casehub.test.",
                CloudEventIngestionAdapterTest::parseJson);

        var time = OffsetDateTime.parse("2026-06-15T10:30:00Z");
        var ce = CloudEventBuilder.v1()
                .withId("id-3")
                .withType("io.casehub.test.scan")
                .withSource(URI.create("/test"))
                .withData("application/json",
                        "{\"x\":\"y\"}".getBytes(StandardCharsets.UTF_8))
                .withTime(time)
                .build();

        adapter.accept(ce);

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).timestamp())
                .isEqualTo(time.toInstant().toEpochMilli());
    }

    @Test
    void tenancyId_nullWhenExtensionMissing() {
        var bus = new EventStreamBus<Map<String, Object>>();
        var captured = new ArrayList<LevelEvent<Map<String, Object>>>();
        bus.subscribe(e -> true, captured::add);

        var adapter = new CloudEventIngestionAdapter<>(
                bus, LEVEL, "io.casehub.test.",
                CloudEventIngestionAdapterTest::parseJson);

        var ce = CloudEventBuilder.v1()
                .withId("id-4")
                .withType("io.casehub.test.event")
                .withSource(URI.create("/test"))
                .withData("application/json",
                        "{\"a\":\"b\"}".getBytes(StandardCharsets.UTF_8))
                .build();

        adapter.accept(ce);

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).tenancyId()).isNull();
    }

    @Test
    void ignores_cloudEventWithNoData() {
        var bus = new EventStreamBus<Map<String, Object>>();
        var captured = new ArrayList<LevelEvent<Map<String, Object>>>();
        bus.subscribe(e -> true, captured::add);

        var adapter = new CloudEventIngestionAdapter<>(
                bus, LEVEL, "io.casehub.test.",
                CloudEventIngestionAdapterTest::parseJson);

        var ce = CloudEventBuilder.v1()
                .withId("id-5")
                .withType("io.casehub.test.event")
                .withSource(URI.create("/test"))
                .build();

        adapter.accept(ce);

        assertThat(captured).isEmpty();
    }

    @Test
    void normalizer_appliedBeforePublishing() {
        var bus = new EventStreamBus<Map<String, Object>>();
        var captured = new ArrayList<LevelEvent<Map<String, Object>>>();
        bus.subscribe(e -> true, captured::add);

        var adapter = new CloudEventIngestionAdapter<>(
                bus, LEVEL, "io.casehub.test.",
                CloudEventIngestionAdapterTest::parseJson,
                payload -> {
                    payload.putIfAbsent("faultType", "");
                    payload.putIfAbsent("reason", "");
                    return payload;
                });

        var ce = CloudEventBuilder.v1()
                .withId("id-6")
                .withType("io.casehub.test.node.recovered")
                .withSource(URI.create("/test"))
                .withData("application/json",
                        "{\"nodeId\":\"N1\"}".getBytes(StandardCharsets.UTF_8))
                .build();

        adapter.accept(ce);

        assertThat(captured).hasSize(1);
        var payload = captured.get(0).payload();
        assertThat(payload).containsEntry("nodeId", "N1");
        assertThat(payload).containsEntry("faultType", "");
        assertThat(payload).containsEntry("reason", "");
    }

    @Test
    void noNormalizer_payloadUnchanged() {
        var bus = new EventStreamBus<Map<String, Object>>();
        var captured = new ArrayList<LevelEvent<Map<String, Object>>>();
        bus.subscribe(e -> true, captured::add);

        var adapter = new CloudEventIngestionAdapter<>(
                bus, LEVEL, "io.casehub.test.",
                CloudEventIngestionAdapterTest::parseJson);

        var ce = CloudEventBuilder.v1()
                .withId("id-7")
                .withType("io.casehub.test.node.faulted")
                .withSource(URI.create("/test"))
                .withData("application/json",
                        "{\"faultType\":\"CPU_OVERLOAD\"}".getBytes(StandardCharsets.UTF_8))
                .build();

        adapter.accept(ce);

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).payload()).containsEntry("faultType", "CPU_OVERLOAD");
        assertThat(captured.get(0).payload()).doesNotContainKey("reason");
    }
}
