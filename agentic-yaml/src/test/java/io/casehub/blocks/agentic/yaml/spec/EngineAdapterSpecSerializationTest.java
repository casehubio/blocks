package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EngineAdapterSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
    }

    @Nested
    class CallerConfigSpecs {
        @Test
        void human_minimal() throws Exception {
            var spec = mapper.readValue("type: human", CallerConfigSpec.class);
            assertThat(spec).isInstanceOf(CallerConfigSpec.Human.class);
        }

        @Test
        void human_full() throws Exception {
            var yaml = """
                    type: human
                    candidateGroups:
                      type: static
                      groups:
                        - reviewers
                    title: Review needed
                    titleExpression: "'Review ' + caseId"
                    outcomes:
                      - APPROVED
                      - REJECTED
                    claimDeadlineHours: 48
                    scope: department
                    scopeExpression: "org.department"
                    priority: HIGH
                    templateRef: review-template
                    payloadType: com.example.ReviewPayload
                    quorum:
                      instances: 3
                      required: 2
                      onThresholdReached: KEEP
                      allowSameAssignee: false
                    """;
            var spec = mapper.readValue(yaml, CallerConfigSpec.class);
            var human = (CallerConfigSpec.Human) spec;
            assertThat(human.candidateGroups()).isInstanceOf(CandidateSetStrategySpec.Static.class);
            assertThat(human.title()).isEqualTo("Review needed");
            assertThat(human.titleExpression()).isEqualTo("'Review ' + caseId");
            assertThat(human.outcomes()).containsExactlyInAnyOrder("APPROVED", "REJECTED");
            assertThat(human.claimDeadlineHours()).isEqualTo(48);
            assertThat(human.scope()).isEqualTo("department");
            assertThat(human.scopeExpression()).isEqualTo("org.department");
            assertThat(human.priority()).isEqualTo("HIGH");
            assertThat(human.templateRef()).isEqualTo("review-template");
            assertThat(human.payloadType()).isEqualTo("com.example.ReviewPayload");
            assertThat(human.quorum()).isNotNull();
            assertThat(human.quorum().instances()).isEqualTo(3);
            assertThat(human.quorum().required()).isEqualTo(2);
            assertThat(human.quorum().onThresholdReached())
                    .isEqualTo(io.casehub.api.model.OnThresholdReached.KEEP);
            assertThat(human.quorum().allowSameAssignee()).isFalse();
        }

        @Test
        void llm() throws Exception {
            var yaml = """
                    type: llm
                    modelId: gpt-4
                    systemPrompt: You are a reviewer
                    """;
            var spec = mapper.readValue(yaml, CallerConfigSpec.class);
            var llm = (CallerConfigSpec.Llm) spec;
            assertThat(llm.modelId()).isEqualTo("gpt-4");
            assertThat(llm.systemPrompt()).isEqualTo("You are a reviewer");
        }

        @Test
        void a2a() throws Exception {
            var yaml = """
                    type: a2a
                    endpoint: https://agent.example.com
                    skill: review
                    streaming: true
                    """;
            var spec = mapper.readValue(yaml, CallerConfigSpec.class);
            var a2a = (CallerConfigSpec.A2A) spec;
            assertThat(a2a.endpoint()).isEqualTo("https://agent.example.com");
            assertThat(a2a.skill()).isEqualTo("review");
            assertThat(a2a.streaming()).isTrue();
        }

        @Test
        void any() throws Exception {
            var spec = mapper.readValue("type: any", CallerConfigSpec.class);
            assertThat(spec).isInstanceOf(CallerConfigSpec.Any.class);
        }
    }

    @Nested
    class QuorumConfigSpecs {
        @Test
        void fullSpec() throws Exception {
            var yaml = """
                    instances: 5
                    required: 3
                    onThresholdReached: CANCEL
                    allowSameAssignee: true
                    """;
            var spec = mapper.readValue(yaml, QuorumConfigSpec.class);
            assertThat(spec.instances()).isEqualTo(5);
            assertThat(spec.required()).isEqualTo(3);
            assertThat(spec.onThresholdReached())
                    .isEqualTo(io.casehub.api.model.OnThresholdReached.CANCEL);
            assertThat(spec.allowSameAssignee()).isTrue();
        }
    }

    @Nested
    class PatternJudgmentConfigSpecs {
        @Test
        void fullConfig() throws Exception {
            var yaml = """
                    prompt: Evaluate the case evidence
                    callerConfig:
                      type: human
                      title: Case review
                      outcomes:
                        - APPROVED
                        - REJECTED
                    verifier:
                      type: llm-evaluation
                    evidenceRequirements:
                      - key: case-notes
                        type: DOCUMENT
                        required: true
                      - key: risk-score
                        type: METRIC
                        required: false
                    mode: INTEGRATED
                    afterStep: true
                    """;
            var spec = mapper.readValue(yaml, PatternJudgmentConfigSpec.class);
            assertThat(spec.prompt()).isEqualTo("Evaluate the case evidence");
            assertThat(spec.callerConfig()).isInstanceOf(CallerConfigSpec.Human.class);
            assertThat(spec.verifier()).isInstanceOf(VerifierStrategySpec.LlmEvaluation.class);
            assertThat(spec.evidenceRequirements()).hasSize(2);
            assertThat(spec.evidenceRequirements().get(0).key()).isEqualTo("case-notes");
            assertThat(spec.evidenceRequirements().get(0).type())
                    .isEqualTo(io.casehub.api.spi.judgment.EvidenceType.DOCUMENT);
            assertThat(spec.mode()).isEqualTo("INTEGRATED");
            assertThat(spec.afterStep()).isTrue();
        }

        @Test
        void minimalConfig() throws Exception {
            var yaml = """
                    prompt: Quick check
                    callerConfig:
                      type: llm
                    evidenceRequirements: []
                    afterStep: false
                    """;
            var spec = mapper.readValue(yaml, PatternJudgmentConfigSpec.class);
            assertThat(spec.prompt()).isEqualTo("Quick check");
            assertThat(spec.callerConfig()).isInstanceOf(CallerConfigSpec.Llm.class);
            assertThat(spec.verifier()).isNull();
            assertThat(spec.mode()).isNull();
        }
    }

}
