package io.casehub.blocks.annotations;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceAnnotationTest {

    @Test
    void oversightGate_has_expected_attributes() {
        assertThat(attributeNames(OversightGate.class)).containsExactlyInAnyOrder(
                "value", "reversible", "candidateGroups");
    }

    @Test
    void trustRouted_attributes_match_policy_keys() {
        assertThat(attributeNames(TrustRouted.class)).containsExactlyInAnyOrder(
                "threshold", "minimumObservations", "borderlineMargin",
                "blendFactor", "cbrWeight");
    }

    @Test
    void cbrRouted_has_outcome_weight_attributes() {
        assertThat(attributeNames(CbrRouted.class)).containsExactlyInAnyOrder(
                "successWeight", "gateExpiredWeight", "gateRejectedWeight", "failureWeight");
    }

    @Test
    void attestation_has_observer_and_capabilityTag() {
        assertThat(attributeNames(Attestation.class)).containsExactlyInAnyOrder(
                "observer", "capabilityTag");
    }

    @Test
    void governance_annotations_are_runtime_retained_method_level() {
        for (var ann : new Class<?>[]{ OversightGate.class, TrustRouted.class,
                CbrRouted.class, Attestation.class }) {
            assertThat(ann.getAnnotation(Retention.class).value())
                    .as(ann.getSimpleName() + " retention")
                    .isEqualTo(RetentionPolicy.RUNTIME);
            assertThat(ann.getAnnotation(Target.class).value())
                    .as(ann.getSimpleName() + " target")
                    .containsExactly(ElementType.METHOD);
        }
    }

    @Test
    void trustRouted_defaults_match_TrustRoutingPolicy_DEFAULT() throws Exception {
        var threshold = TrustRouted.class.getDeclaredMethod("threshold");
        var minimumObservations = TrustRouted.class.getDeclaredMethod("minimumObservations");
        var borderlineMargin = TrustRouted.class.getDeclaredMethod("borderlineMargin");
        var blendFactor = TrustRouted.class.getDeclaredMethod("blendFactor");
        var cbrWeight = TrustRouted.class.getDeclaredMethod("cbrWeight");

        assertThat(threshold.getDefaultValue()).isEqualTo(0.7);
        assertThat(minimumObservations.getDefaultValue()).isEqualTo(10);
        assertThat(borderlineMargin.getDefaultValue()).isEqualTo(0.1);
        assertThat(blendFactor.getDefaultValue()).isEqualTo(0.6);
        assertThat(cbrWeight.getDefaultValue()).isEqualTo(0.0);
    }

    @Test
    void cbrRouted_defaults_match_DefaultCbrOutcomeWeights() throws Exception {
        var success = CbrRouted.class.getDeclaredMethod("successWeight");
        var gateExpired = CbrRouted.class.getDeclaredMethod("gateExpiredWeight");
        var gateRejected = CbrRouted.class.getDeclaredMethod("gateRejectedWeight");
        var failure = CbrRouted.class.getDeclaredMethod("failureWeight");

        assertThat(success.getDefaultValue()).isEqualTo(1.0);
        assertThat(gateExpired.getDefaultValue()).isEqualTo(0.5);
        assertThat(gateRejected.getDefaultValue()).isEqualTo(0.25);
        assertThat(failure.getDefaultValue()).isEqualTo(0.0);
    }

    private static Set<String> attributeNames(Class<?> annotation) {
        return Arrays.stream(annotation.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());
    }
}
