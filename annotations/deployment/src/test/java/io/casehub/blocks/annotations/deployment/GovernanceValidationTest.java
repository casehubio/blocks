package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.annotations.Attestation;
import io.casehub.blocks.annotations.CbrRouted;
import io.casehub.blocks.annotations.Debate;
import io.casehub.blocks.annotations.Debater;
import io.casehub.blocks.annotations.Judge;
import io.casehub.blocks.annotations.OversightGate;
import io.casehub.blocks.annotations.TrustRouted;
import io.casehub.blocks.annotations.runtime.GovernanceDescriptor;
import io.casehub.blocks.attestation.LifecycleAttestationObserver;
import io.casehub.engine.annotations.Worker;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernanceValidationTest {

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

    // --- Fixtures ---

    interface OrphanGovernanceBad {
        @OversightGate(TestClassifier.class)
        String doWork(String input);
    }

    interface GovernedDebate {
        @Debate(maxRounds = 5)
        @OversightGate(TestClassifier.class)
        @TrustRouted(threshold = 0.8)
        String review(
                @Debater(role = "critic", systemPrompt = "Challenge") AgentRef critic,
                @Judge(systemPrompt = "Judge") AgentRef judge,
                String document);
    }

    interface GovernedWorker {
        @Worker(capability = "process")
        @OversightGate(TestClassifier.class)
        String process(String input);
    }

    interface CbrGovernedWorker {
        @Worker(capability = "route")
        @CbrRouted(successWeight = 0.9, failureWeight = 0.1)
        String route(String input);
    }

    // --- Rejection tests ---

    @Test
    void rejects_governance_without_worker_or_pattern() throws IOException {
        Index index = indexClasses(OrphanGovernanceBad.class);
        var step = new GovernanceAnnotationStep();

        assertThatThrownBy(() -> step.scan(index))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no @Worker or pattern annotation");
    }

    // --- Acceptance tests ---

    @Test
    void accepts_governance_on_pattern_method() throws IOException {
        Index index = indexClasses(GovernedDebate.class);
        var step = new GovernanceAnnotationStep();
        var descriptors = step.scan(index);

        assertThat(descriptors).hasSize(2);
        assertThat(descriptors).anySatisfy(d ->
                assertThat(d.governanceType()).isEqualTo("OversightGate"));
        assertThat(descriptors).anySatisfy(d ->
                assertThat(d.governanceType()).isEqualTo("TrustRouted"));
    }

    @Test
    void accepts_governance_on_worker_method() throws IOException {
        Index index = indexClasses(GovernedWorker.class);
        var step = new GovernanceAnnotationStep();
        var descriptors = step.scan(index);

        assertThat(descriptors).hasSize(1);
        assertThat(descriptors.get(0).governanceType()).isEqualTo("OversightGate");
    }

    @Test
    void oversightGate_captures_classifier_and_config() throws IOException {
        Index index = indexClasses(GovernedDebate.class);
        var step = new GovernanceAnnotationStep();
        var descriptors = step.scan(index);

        var gate = descriptors.stream()
                .filter(d -> d instanceof GovernanceDescriptor.OversightGateDescriptor)
                .map(d -> (GovernanceDescriptor.OversightGateDescriptor) d)
                .findFirst().orElseThrow();
        assertThat(gate.config().get("classifierClass"))
                .isEqualTo(TestClassifier.class.getName());
        assertThat(gate.config().get("reversible")).isEqualTo(true);
    }

    @Test
    void trustRouted_captures_annotation_values() throws IOException {
        Index index = indexClasses(GovernedDebate.class);
        var step = new GovernanceAnnotationStep();
        var descriptors = step.scan(index);

        var trust = descriptors.stream()
                .filter(d -> d instanceof GovernanceDescriptor.TrustRoutedDescriptor)
                .map(d -> (GovernanceDescriptor.TrustRoutedDescriptor) d)
                .findFirst().orElseThrow();
        assertThat(trust.config().get("threshold")).isEqualTo(0.8);
    }

    @Test
    void cbrRouted_captures_outcome_weights() throws IOException {
        Index index = indexClasses(CbrGovernedWorker.class);
        var step = new GovernanceAnnotationStep();
        var descriptors = step.scan(index);

        assertThat(descriptors).hasSize(1);
        var cbr = (GovernanceDescriptor.CbrRoutedDescriptor) descriptors.get(0);
        assertThat(cbr.config().get("successWeight")).isEqualTo(0.9);
        assertThat(cbr.config().get("failureWeight")).isEqualTo(0.1);
    }
}
