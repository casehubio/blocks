package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record GovernanceSpec(
        @Nullable OversightSpec oversight,
        @Nullable TrustRoutingSpec trustRouting,
        @Nullable CbrRoutingSpec cbrRouting,
        @Nullable AttestationSpec attestation) {

    public record OversightSpec(
            String classifier,
            boolean reversible,
            @Nullable @JsonProperty("candidateGroups") List<String> candidateGroups) {

        public OversightSpec {
            Objects.requireNonNull(classifier, "classifier");
        }
    }

    public record TrustRoutingSpec(
            double threshold,
            int minimumObservations,
            double borderlineMargin,
            double blendFactor) {

        public TrustRoutingSpec() {
            this(0.7, 10, 0.1, 0.6);
        }
    }

    public record CbrRoutingSpec(
            double successWeight,
            double gateExpiredWeight,
            double gateRejectedWeight,
            double failureWeight) {

        public CbrRoutingSpec() {
            this(1.0, 0.5, 0.25, 0.0);
        }
    }

    public record AttestationSpec(
            String observer,
            @Nullable String capabilityTag) {

        public AttestationSpec {
            Objects.requireNonNull(observer, "observer");
        }
    }
}
