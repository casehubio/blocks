package io.casehub.blocks.summarisation.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineDefinitionTest {

    static final ObjectMapper MAPPER = YamlMappers.create();

    @Test
    void parsesMinimalPipeline() throws Exception {
        String yaml = """
                pipeline:
                  name: test-pipeline
                  source:
                    cloud-event-type: io.test.event.v1
                  levels:
                    - name: output
                      grouping:
                        type: windowed
                        count: 10
                      summariser:
                        type: pass-through
                """;

        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        assertThat(def.name()).isEqualTo("test-pipeline");
        assertThat(def.source().cloudEventType()).isEqualTo("io.test.event.v1");
        assertThat(def.source().type()).isNull();
        assertThat(def.levels()).hasSize(1);
        assertThat(def.levels().get(0).name()).isEqualTo("output");
        assertThat(def.levels().get(0).grouping()).isInstanceOf(GroupingDefinition.Windowed.class);
        var windowed = (GroupingDefinition.Windowed) def.levels().get(0).grouping();
        assertThat(windowed.count()).isEqualTo(10);
        assertThat(windowed.age()).isNull();
    }

    @Test
    void parsesWindowedWithAge() throws Exception {
        String yaml = """
                pipeline:
                  name: aged
                  source:
                    cloud-event-type: io.test.v1
                  levels:
                    - name: l1
                      grouping:
                        type: windowed
                        age: 300000
                      summariser:
                        type: pass-through
                """;

        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        var windowed = (GroupingDefinition.Windowed) def.levels().get(0).grouping();
        assertThat(windowed.age()).isEqualTo(300_000L);
        assertThat(windowed.count()).isNull();
    }

    @Test
    void parsesWindowedWithBoth() throws Exception {
        String yaml = """
                pipeline:
                  name: both
                  source:
                    cloud-event-type: io.test.v1
                  levels:
                    - name: l1
                      grouping:
                        type: windowed
                        count: 5
                        age: 60000
                      summariser:
                        type: pass-through
                """;

        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        var windowed = (GroupingDefinition.Windowed) def.levels().get(0).grouping();
        assertThat(windowed.count()).isEqualTo(5);
        assertThat(windowed.age()).isEqualTo(60_000L);
    }

    @Test
    void parsesKeyedGrouping() throws Exception {
        String yaml = """
                pipeline:
                  name: keyed-test
                  source:
                    cloud-event-type: io.test.v1
                  levels:
                    - name: per-hub
                      grouping:
                        type: keyed
                        key: "warehouseId"
                        completion: "size >= 10"
                        stale-timeout: 90000
                      summariser:
                        type: pass-through
                """;

        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        var grouping = def.levels().get(0).grouping();
        assertThat(grouping).isInstanceOf(GroupingDefinition.Keyed.class);
        var keyed = (GroupingDefinition.Keyed) grouping;
        assertThat(keyed.keyExpression()).isEqualTo("warehouseId");
        assertThat(keyed.completionExpression()).isEqualTo("size >= 10");
        assertThat(keyed.staleTimeout()).isEqualTo(90_000L);
    }

    @Test
    void parsesSummariserWithConfig() throws Exception {
        String yaml = """
                pipeline:
                  name: classified
                  source:
                    cloud-event-type: io.test.v1
                  levels:
                    - name: anomalies
                      grouping:
                        type: windowed
                        count: 10
                      summariser:
                        type: threshold-classify
                        rules:
                          - name: weight-mismatch
                            when: "weight > 50.0"
                            category: WEIGHT_MISMATCH
                            severity: HIGH
                """;

        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        var summariser = def.levels().get(0).summariser();
        assertThat(summariser.type()).isEqualTo("threshold-classify");
        assertThat(summariser.config()).containsKey("rules");
        @SuppressWarnings("unchecked")
        var rules = (List<Map<String, Object>>) summariser.config().get("rules");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0)).containsEntry("name", "weight-mismatch");
        assertThat(rules.get(0)).containsEntry("category", "WEIGHT_MISMATCH");
    }

    @Test
    void parsesEmitBlock() throws Exception {
        String yaml = """
                pipeline:
                  name: emitting
                  source:
                    cloud-event-type: io.test.v1
                  levels:
                    - name: l1
                      grouping:
                        type: windowed
                        count: 5
                      summariser:
                        type: pass-through
                      emit:
                        cloud-event-type: io.casehub.test.output.v1
                """;

        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        var emit = def.levels().get(0).emit();
        assertThat(emit).isNotNull();
        assertThat(emit.cloudEventType()).isEqualTo("io.casehub.test.output.v1");
    }

    @Test
    void emitIsNullWhenAbsent() throws Exception {
        String yaml = """
                pipeline:
                  name: no-emit
                  source:
                    cloud-event-type: io.test.v1
                  levels:
                    - name: l1
                      grouping:
                        type: windowed
                        count: 5
                      summariser:
                        type: pass-through
                """;

        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        assertThat(def.levels().get(0).emit()).isNull();
    }

    @Test
    void parsesAggregateFields() throws Exception {
        String yaml = """
                pipeline:
                  name: aggregated
                  source:
                    cloud-event-type: io.test.v1
                  levels:
                    - name: phases
                      grouping:
                        type: windowed
                        age: 300000
                      aggregate-fields:
                        - severity
                        - weight
                      summariser:
                        type: phase-detect
                        initial: NORMAL
                        states:
                          - NORMAL
                          - CONGESTION
                        transitions: []
                """;

        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        assertThat(def.levels().get(0).aggregateFields())
                .containsExactly("severity", "weight");
    }

    @Test
    void aggregateFieldsDefaultsToEmpty() throws Exception {
        String yaml = """
                pipeline:
                  name: no-agg
                  source:
                    cloud-event-type: io.test.v1
                  levels:
                    - name: l1
                      grouping:
                        type: windowed
                        count: 5
                      summariser:
                        type: pass-through
                """;

        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        assertThat(def.levels().get(0).aggregateFields()).isEmpty();
    }

    @Test
    void parsesSourceType() throws Exception {
        String yaml = """
                pipeline:
                  name: typed
                  source:
                    type: io.example.PackageScan
                    cloud-event-type: io.casehub.logistics.scan.v1
                  levels:
                    - name: l1
                      grouping:
                        type: windowed
                        count: 10
                      summariser:
                        type: pass-through
                """;

        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        assertThat(def.source().type()).isEqualTo("io.example.PackageScan");
        assertThat(def.source().cloudEventType()).isEqualTo("io.casehub.logistics.scan.v1");
    }

    @Test
    void parsesMultiLevelPipeline() throws Exception {
        String yaml = """
                pipeline:
                  name: multi-level
                  source:
                    cloud-event-type: io.test.v1
                  levels:
                    - name: anomalies
                      grouping:
                        type: windowed
                        count: 10
                      summariser:
                        type: threshold-classify
                        rules:
                          - name: heavy
                            when: "weight > 50"
                            category: HEAVY
                      emit:
                        cloud-event-type: io.test.anomaly.v1
                    - name: phases
                      grouping:
                        type: windowed
                        age: 300000
                      aggregate-fields:
                        - severity
                      summariser:
                        type: phase-detect
                        initial: NORMAL
                        states:
                          - NORMAL
                          - CONGESTION
                        transitions:
                          - from: NORMAL
                            to: CONGESTION
                            when: "counts.severity.HIGH >= 3"
                      emit:
                        cloud-event-type: io.test.phase.v1
                """;

        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        assertThat(def.levels()).hasSize(2);
        assertThat(def.levels().get(0).name()).isEqualTo("anomalies");
        assertThat(def.levels().get(1).name()).isEqualTo("phases");
    }

    @Test
    void summariserDefinition_deserialises_mode() throws Exception {
        String yaml = """
                      pipeline:
                        name: mode-test
                        source:
                          cloud-event-type: io.test.v1
                        levels:
                          - name: l1
                            grouping:
                              type: windowed
                              count: 5
                            summariser:
                              type: llm
                              mode: EDIT
                      """;
        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        assertThat(def.levels().get(0).summariser().mode()).isEqualTo("EDIT");
    }

    @Test
    void summariserDefinition_mode_defaultsToNull() throws Exception {
        String yaml = """
                      pipeline:
                        name: no-mode
                        source:
                          cloud-event-type: io.test.v1
                        levels:
                          - name: l1
                            grouping:
                              type: windowed
                              count: 5
                            summariser:
                              type: count
                      """;
        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        assertThat(def.levels().get(0).summariser().mode()).isNull();
    }

    @Test
    void sourceDefinition_deserialises_typePrefix() throws Exception {
        String yaml = """
                      pipeline:
                        name: prefix-test
                        source:
                          type: cloud-event
                          type-prefix: io.casehub.sensor
                          cloud-event-type: io.casehub.output
                        levels:
                          - name: l1
                            grouping:
                              type: windowed
                              count: 5
                            summariser:
                              type: pass-through
                      """;
        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        assertThat(def.source().typePrefix()).isEqualTo("io.casehub.sensor");
    }

    @Test
    void pipelineDefinition_deserialises_tickInterval() throws Exception {
        String yaml = """
                      pipeline:
                        name: tick-test
                        tick-interval: 1000
                        source:
                          cloud-event-type: io.test.v1
                        levels:
                          - name: l1
                            grouping:
                              type: windowed
                              count: 5
                            summariser:
                              type: pass-through
                      """;
        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        assertThat(def.tickInterval()).isEqualTo(1000L);
    }

    @Test
    void pipelineDefinition_tickInterval_defaultsToNull() throws Exception {
        String yaml = """
                      pipeline:
                        name: no-tick
                        source:
                          cloud-event-type: io.test.v1
                        levels:
                          - name: l1
                            grouping:
                              type: windowed
                              count: 5
                            summariser:
                              type: pass-through
                      """;
        var def = MAPPER.readValue(yaml, PipelineWrapper.class).pipeline();
        assertThat(def.tickInterval()).isNull();
    }
}
