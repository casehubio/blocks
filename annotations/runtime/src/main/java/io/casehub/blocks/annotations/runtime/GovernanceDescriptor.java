package io.casehub.blocks.annotations.runtime;

import java.util.Map;

public sealed interface GovernanceDescriptor {

    String governanceType();

    Map<String, Object> config();

    record OversightGateDescriptor(Map<String, Object> config) implements GovernanceDescriptor {
        @Override
        public String governanceType() { return "OversightGate"; }
    }

    record TrustRoutedDescriptor(Map<String, Object> config) implements GovernanceDescriptor {
        @Override
        public String governanceType() { return "TrustRouted"; }
    }

    record CbrRoutedDescriptor(Map<String, Object> config) implements GovernanceDescriptor {
        @Override
        public String governanceType() { return "CbrRouted"; }
    }

    record AttestationDescriptor(Map<String, Object> config) implements GovernanceDescriptor {
        @Override
        public String governanceType() { return "Attestation"; }
    }
}
