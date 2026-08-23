package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.annotations.Agent;
import io.casehub.blocks.annotations.CbrRouted;
import io.casehub.blocks.annotations.Debate;
import io.casehub.blocks.annotations.Debater;
import io.casehub.blocks.annotations.Htn;
import io.casehub.blocks.annotations.Judge;
import io.casehub.blocks.annotations.OversightGate;
import io.casehub.blocks.annotations.Supervisor;
import io.casehub.blocks.annotations.TrustRouted;
import io.casehub.blocks.annotations.Voter;
import io.casehub.blocks.annotations.Voting;
import io.casehub.blocks.annotations.runtime.PatternDescriptor;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExamplePatternsTest {

    private Index indexClasses(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> clazz : classes) {
            indexClass(indexer, clazz);
        }
        return indexer.complete();
    }

    private void indexClass(Indexer indexer, Class<?> clazz) throws IOException {
        String resourceName = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream stream = clazz.getResourceAsStream(resourceName)) {
            if (stream != null) {
                indexer.index(stream);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Example 1: Incident Response — governed supervisor + debate
    // -----------------------------------------------------------------------

    interface IncidentTriage {
        @Supervisor(maxIterations = 15)
        @OversightGate(TestClassifier.class)
        @TrustRouted(threshold = 0.8)
        String triage(
                @Agent(name = "triage", systemPrompt = "Triage and categorise the incident") AgentRef triageAgent,
                @Agent(name = "containment", systemPrompt = "Recommend containment actions") AgentRef containmentAgent,
                @Agent(name = "forensics", systemPrompt = "Analyse evidence and identify root cause") AgentRef forensicsAgent,
                String incidentReport);
    }

    interface ContainmentDebate {
        @Debate(maxRounds = 5)
        String review(
                @Debater(role = "risk-analyst", systemPrompt = "Assess risk of each containment option") AgentRef riskAnalyst,
                @Debater(role = "ops-lead", systemPrompt = "Evaluate operational feasibility") AgentRef opsLead,
                @Judge(systemPrompt = "Evaluate arguments and recommend the best containment strategy") AgentRef judge,
                String containmentOptions);
    }

    // -----------------------------------------------------------------------
    // Example 2: Aircraft Maintenance — debate + oversight + CBR routing
    // -----------------------------------------------------------------------

    interface RepairStrategyReview {
        @Debate(maxRounds = 3)
        @OversightGate(TestClassifier.class)
        @CbrRouted(successWeight = 0.9, gateExpiredWeight = 0.4, gateRejectedWeight = 0.2, failureWeight = 0.0)
        String review(
                @Debater(role = "engineer", systemPrompt = "Propose repair approach based on technical analysis") AgentRef engineer,
                @Debater(role = "safety-officer", systemPrompt = "Evaluate safety implications of repair options") AgentRef safetyOfficer,
                @Judge(systemPrompt = "Select the repair strategy balancing safety and cost") AgentRef judge,
                String defectReport);
    }

    // -----------------------------------------------------------------------
    // Example 3: Wildfire Response — voting consensus + HTN decomposition
    // -----------------------------------------------------------------------

    interface WildfireResourceConsensus {
        @Voting
        @OversightGate(TestClassifier.class)
        String allocate(
                @Voter(role = "fire-chief", systemPrompt = "Prioritise containment and fire lines") AgentRef fireChief,
                @Voter(role = "medic-lead", systemPrompt = "Prioritise evacuation and medical staging") AgentRef medicLead,
                @Voter(role = "logistics", systemPrompt = "Assess resource feasibility and transport") AgentRef logistics,
                String situationReport);
    }

    interface WildfireDeployment {
        @Htn
        String deploy(
                @Agent(name = "planner", systemPrompt = "Decompose deployment into phased tasks") AgentRef planner,
                String allocationPlan);
    }
// -----------------------------------------------------------------------
    // Example 4: Incident Response — sequence + parallel + loop + conditional
    // -----------------------------------------------------------------------

    interface IncidentSequence {
        @io.casehub.blocks.annotations.Sequence(name = "incident-pipeline")
        String pipeline(
                @Agent(name = "detect", systemPrompt = "Detect and classify the threat") AgentRef detect,
                @Agent(name = "triage", systemPrompt = "Assess severity and assign priority") AgentRef triage,
                @Agent(name = "contain", systemPrompt = "Execute containment procedures") AgentRef contain);
    }

    interface WildfireParallelAssessment {
        @io.casehub.blocks.annotations.Parallel(name = "parallel-assessment")
        String assess(
                @Agent(name = "fire-assessment", systemPrompt = "Assess fire spread and intensity") AgentRef fire,
                @Agent(name = "infrastructure-assessment", systemPrompt = "Assess structural damage") AgentRef infra,
                @Agent(name = "environmental-assessment", systemPrompt = "Assess ecological impact") AgentRef env);
    }

    interface AircraftInspectionLoop {
        @io.casehub.blocks.annotations.Loop(name = "inspection-loop", maxIterations = 5)
        String inspect(
                @Agent(name = "inspector", systemPrompt = "Run next inspection checklist item") AgentRef inspector,
                @Agent(name = "verifier", systemPrompt = "Verify inspection findings meet airworthiness") AgentRef verifier);
    }

    interface IncidentConditionalRouting {
        @io.casehub.blocks.annotations.Conditional(name = "severity-router")
        String route(
                @Agent(name = "critical-handler", systemPrompt = "Handle critical severity incidents") AgentRef critical,
                @Agent(name = "standard-handler", systemPrompt = "Handle standard severity incidents") AgentRef standard);
    }

    // -----------------------------------------------------------------------
    // Example 5: Attestation governance
    // -----------------------------------------------------------------------

    interface AttestedIncidentTriage {
        @Supervisor(maxIterations = 15)
        @io.casehub.blocks.annotations.Attestation(observer = TestAttestationObserver.class, capabilityTag = "incident-triage")
        String triage(
                @Agent(name = "triage", systemPrompt = "Triage the incident") AgentRef triageAgent,
                String incidentReport);
    }


    // -----------------------------------------------------------------------
    // Tests — full pipeline: scan patterns + governance + verify recorder
    // -----------------------------------------------------------------------

    @Test
    void incident_triage_produces_supervisor_with_3_agents() throws IOException {
        Index index = indexClasses(IncidentTriage.class);
        var patterns = new PatternAnnotationStep().scan(index);

        assertThat(patterns).hasSize(1);
        PatternDescriptor desc = patterns.get(0);
        assertThat(desc.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(desc.beanName()).isEqualTo("triage");
        assertThat(desc.attributes().get("maxIterations")).isEqualTo(15);
        assertThat(desc.participants()).hasSize(3);
        assertThat(desc.participants()).extracting(PatternDescriptor.AgentParticipant::label)
                .containsExactly("triage", "containment", "forensics");
    }

    @Test
    void incident_triage_produces_oversight_and_trust_governance() throws IOException {
        Index index = indexClasses(IncidentTriage.class);
        var governance = new GovernanceAnnotationStep().scan(index);

        assertThat(governance).hasSize(2);
        assertThat(governance).anySatisfy(d -> {
            assertThat(d.governanceType()).isEqualTo("OversightGate");
            assertThat(d.config().get("classifierClass")).isEqualTo(TestClassifier.class.getName());
        });
        assertThat(governance).anySatisfy(d -> {
            assertThat(d.governanceType()).isEqualTo("TrustRouted");
            assertThat(d.config().get("threshold")).isEqualTo(0.8);
        });
    }

    @Test
    void incident_triage_recorder_builds_valid_model() throws IOException {
        Index index = indexClasses(IncidentTriage.class);
        var desc = new PatternAnnotationStep().scan(index).get(0);
        var model = new io.casehub.blocks.annotations.runtime.BlocksAnnotationsRecorder()
                .createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.candidateSupplier().get()).hasSize(3);
        assertThat(model.routing()).isInstanceOf(io.casehub.blocks.agentic.routing.FirstMatchRouting.class);
    }

    @Test
    void containment_debate_produces_debate_with_judge() throws IOException {
        Index index = indexClasses(ContainmentDebate.class);
        var patterns = new PatternAnnotationStep().scan(index);

        assertThat(patterns).hasSize(1);
        PatternDescriptor desc = patterns.get(0);
        assertThat(desc.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(desc.participants()).hasSize(3);
        assertThat(desc.participants().stream().filter(PatternDescriptor.AgentParticipant::isJudge).count())
                .isEqualTo(1);
    }

    @Test
    void containment_debate_recorder_builds_model_with_judge_convergence() throws IOException {
        Index index = indexClasses(ContainmentDebate.class);
        var desc = new PatternAnnotationStep().scan(index).get(0);
        var model = new io.casehub.blocks.annotations.runtime.BlocksAnnotationsRecorder()
                .createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(model.termination()).isInstanceOf(io.casehub.blocks.agentic.termination.JudgeConvergence.class);
    }

    @Test
    void repair_strategy_produces_debate_with_oversight_and_cbr() throws IOException {
        Index index = indexClasses(RepairStrategyReview.class);
        var patterns = new PatternAnnotationStep().scan(index);
        var governance = new GovernanceAnnotationStep().scan(index);

        assertThat(patterns).hasSize(1);
        assertThat(patterns.get(0).patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(patterns.get(0).participants()).hasSize(3);

        assertThat(governance).hasSize(2);
        assertThat(governance).anySatisfy(d ->
                assertThat(d.governanceType()).isEqualTo("OversightGate"));
        assertThat(governance).anySatisfy(d -> {
            assertThat(d.governanceType()).isEqualTo("CbrRouted");
            assertThat(d.config().get("successWeight")).isEqualTo(0.9);
        });
    }

    @Test
    void wildfire_consensus_produces_voting_with_oversight() throws IOException {
        Index index = indexClasses(WildfireResourceConsensus.class);
        var patterns = new PatternAnnotationStep().scan(index);
        var governance = new GovernanceAnnotationStep().scan(index);

        assertThat(patterns).hasSize(1);
        assertThat(patterns.get(0).patternType()).isEqualTo(PatternType.VOTING);
        assertThat(patterns.get(0).participants()).hasSize(3);

        assertThat(governance).hasSize(1);
        assertThat(governance.get(0).governanceType()).isEqualTo("OversightGate");
    }

    @Test
    void wildfire_deployment_produces_htn() throws IOException {
        Index index = indexClasses(WildfireDeployment.class);
        var patterns = new PatternAnnotationStep().scan(index);

        assertThat(patterns).hasSize(1);
        assertThat(patterns.get(0).patternType()).isEqualTo(PatternType.HTN);
        assertThat(patterns.get(0).participants()).hasSize(1);
    }

    @Test
    void wildfire_deployment_recorder_builds_valid_htn_model() throws IOException {
        Index index = indexClasses(WildfireDeployment.class);
        var desc = new PatternAnnotationStep().scan(index).get(0);
        var model = new io.casehub.blocks.annotations.runtime.BlocksAnnotationsRecorder()
                .createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.HTN);
        assertThat(model.candidateSupplier().get()).hasSize(1);
    }

    @Test
    void full_pipeline_incident_scan_to_model() throws IOException {
        Index index = indexClasses(IncidentTriage.class, ContainmentDebate.class);
        var patternStep = new PatternAnnotationStep();
        var governanceStep = new GovernanceAnnotationStep();
        var recorder = new io.casehub.blocks.annotations.runtime.BlocksAnnotationsRecorder();

        var patterns = patternStep.scan(index);
        var governance = governanceStep.scan(index);

        assertThat(patterns).hasSize(2);
        assertThat(governance).hasSize(2);

        for (var desc : patterns) {
            var model = recorder.createExecutionModel(desc).get();
            assertThat(model).isNotNull();
            assertThat(model.patternType()).isEqualTo(desc.patternType());
            assertThat(model.candidateSupplier().get()).isNotEmpty();
        }
    }

    @Test
    void sequence_produces_ordered_pipeline() throws IOException {
        Index index    = indexClasses(IncidentSequence.class);
        var   patterns = new PatternAnnotationStep().scan(index);

        assertThat(patterns).hasSize(1);
        var desc = patterns.get(0);
        assertThat(desc.patternType()).isEqualTo(PatternType.SEQUENCE);
        assertThat(desc.beanName()).isEqualTo("incident-pipeline");
        assertThat(desc.participants()).hasSize(3);
        assertThat(desc.participants()).extracting(PatternDescriptor.AgentParticipant::label)
                                       .containsExactly("detect", "triage", "contain");
    }

    @Test
    void sequence_recorder_builds_model_with_sequential_routing() throws IOException {
        Index index = indexClasses(IncidentSequence.class);
        var   desc  = new PatternAnnotationStep().scan(index).get(0);
        var model = new io.casehub.blocks.annotations.runtime.BlocksAnnotationsRecorder()
                            .createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.SEQUENCE);
        assertThat(model.routing()).isInstanceOf(io.casehub.blocks.agentic.routing.SequentialRouting.class);
        assertThat(model.candidateSupplier().get()).hasSize(3);
    }

    @Test
    void parallel_produces_concurrent_assessment() throws IOException {
        Index index    = indexClasses(WildfireParallelAssessment.class);
        var   patterns = new PatternAnnotationStep().scan(index);

        assertThat(patterns).hasSize(1);
        var desc = patterns.get(0);
        assertThat(desc.patternType()).isEqualTo(PatternType.PARALLEL);
        assertThat(desc.beanName()).isEqualTo("parallel-assessment");
        assertThat(desc.participants()).hasSize(3);
    }

    @Test
    void parallel_recorder_builds_valid_model() throws IOException {
        Index index = indexClasses(WildfireParallelAssessment.class);
        var   desc  = new PatternAnnotationStep().scan(index).get(0);
        var model = new io.casehub.blocks.annotations.runtime.BlocksAnnotationsRecorder()
                            .createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.PARALLEL);
        assertThat(model.candidateSupplier().get()).hasSize(3);
    }

    @Test
    void loop_produces_iterative_inspection() throws IOException {
        Index index    = indexClasses(AircraftInspectionLoop.class);
        var   patterns = new PatternAnnotationStep().scan(index);

        assertThat(patterns).hasSize(1);
        var desc = patterns.get(0);
        assertThat(desc.patternType()).isEqualTo(PatternType.LOOP);
        assertThat(desc.beanName()).isEqualTo("inspection-loop");
        assertThat(desc.attributes().get("maxIterations")).isEqualTo(5);
        assertThat(desc.participants()).hasSize(2);
    }

    @Test
    void loop_recorder_builds_model_with_max_iterations() throws IOException {
        Index index = indexClasses(AircraftInspectionLoop.class);
        var   desc  = new PatternAnnotationStep().scan(index).get(0);
        var model = new io.casehub.blocks.annotations.runtime.BlocksAnnotationsRecorder()
                            .createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.LOOP);
        assertThat(model.termination()).isInstanceOf(io.casehub.blocks.agentic.termination.MaxIterationsTermination.class);
    }

    @Test
    void conditional_produces_severity_router() throws IOException {
        Index index    = indexClasses(IncidentConditionalRouting.class);
        var   patterns = new PatternAnnotationStep().scan(index);

        assertThat(patterns).hasSize(1);
        var desc = patterns.get(0);
        assertThat(desc.patternType()).isEqualTo(PatternType.CONDITIONAL);
        assertThat(desc.beanName()).isEqualTo("severity-router");
        assertThat(desc.participants()).hasSize(2);
    }

    @Test
    void conditional_recorder_builds_valid_model() throws IOException {
        Index index = indexClasses(IncidentConditionalRouting.class);
        var   desc  = new PatternAnnotationStep().scan(index).get(0);
        var model = new io.casehub.blocks.annotations.runtime.BlocksAnnotationsRecorder()
                            .createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.CONDITIONAL);
        assertThat(model.routing()).isInstanceOf(io.casehub.blocks.agentic.routing.FirstMatchRouting.class);
        assertThat(model.candidateSupplier().get()).hasSize(2);
    }

    @Test
    void attestation_governance_captures_observer_and_capability_tag() throws IOException {
        Index index      = indexClasses(AttestedIncidentTriage.class);
        var   governance = new GovernanceAnnotationStep().scan(index);

        assertThat(governance).hasSize(1);
        assertThat(governance.get(0).governanceType()).isEqualTo("Attestation");
        assertThat(governance.get(0).config().get("observerClass"))
                .isEqualTo(TestAttestationObserver.class.getName());
        assertThat(governance.get(0).config().get("capabilityTag")).isEqualTo("incident-triage");
    }

}
