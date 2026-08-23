package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.annotations.runtime.GovernanceDescriptor;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class GovernanceAnnotationStep {

    static final DotName OVERSIGHT_GATE = DotName.createSimple("io.casehub.blocks.annotations.OversightGate");
    static final DotName TRUST_ROUTED = DotName.createSimple("io.casehub.blocks.annotations.TrustRouted");
    static final DotName CBR_ROUTED = DotName.createSimple("io.casehub.blocks.annotations.CbrRouted");
    static final DotName ATTESTATION = DotName.createSimple("io.casehub.blocks.annotations.Attestation");

    static final Set<DotName> GOVERNANCE_ANNOTATIONS = Set.of(
            OVERSIGHT_GATE, TRUST_ROUTED, CBR_ROUTED, ATTESTATION
    );

    static final DotName WORKER = DotName.createSimple("io.casehub.engine.annotations.Worker");

    List<GovernanceDescriptor> scan(IndexView index) {
        List<GovernanceDescriptor> descriptors = new ArrayList<>();

        for (DotName annName : GOVERNANCE_ANNOTATIONS) {
            for (AnnotationInstance ann : index.getAnnotations(annName)) {
                if (ann.target().kind() != AnnotationTarget.Kind.METHOD) {
                    continue;
                }
                MethodInfo method = ann.target().asMethod();
                validateHasWorkerOrPattern(method, ann);
                descriptors.add(extractDescriptor(ann));
            }
        }

        return descriptors;
    }

    private void validateHasWorkerOrPattern(MethodInfo method, AnnotationInstance govAnn) {
        boolean hasWorker = method.hasAnnotation(WORKER);
        boolean hasPattern = PatternAnnotationStep.PATTERN_ANNOTATIONS.keySet().stream()
                .anyMatch(method::hasAnnotation);

        if (!hasWorker && !hasPattern) {
            throw new IllegalStateException(
                    "@" + govAnn.name().local() + " on method "
                            + method.declaringClass().name() + "." + method.name()
                            + " has no @Worker or pattern annotation to govern");
        }
    }

    private GovernanceDescriptor extractDescriptor(AnnotationInstance ann) {
        if (ann.name().equals(OVERSIGHT_GATE)) {
            return extractOversightGate(ann);
        }
        if (ann.name().equals(TRUST_ROUTED)) {
            return extractTrustRouted(ann);
        }
        if (ann.name().equals(CBR_ROUTED)) {
            return extractCbrRouted(ann);
        }
        if (ann.name().equals(ATTESTATION)) {
            return extractAttestation(ann);
        }
        throw new IllegalStateException("Unknown governance annotation: " + ann.name());
    }

    private GovernanceDescriptor.OversightGateDescriptor extractOversightGate(AnnotationInstance ann) {
        Map<String, Object> config = new HashMap<>();
        AnnotationValue classValue = ann.value();
        if (classValue != null) {
            config.put("classifierClass", classValue.asClass().name().toString());
        }
        AnnotationValue reversible = ann.value("reversible");
        config.put("reversible", reversible != null ? reversible.asBoolean() : true);
        AnnotationValue groups = ann.value("candidateGroups");
        if (groups != null) {
            config.put("candidateGroups", List.of(groups.asStringArray()));
        } else {
            config.put("candidateGroups", List.of());
        }
        return new GovernanceDescriptor.OversightGateDescriptor(config);
    }

    private GovernanceDescriptor.TrustRoutedDescriptor extractTrustRouted(AnnotationInstance ann) {
        Map<String, Object> config = new HashMap<>();
        config.put("threshold", doubleValue(ann, "threshold", 0.7));
        config.put("minimumObservations", intValue(ann, "minimumObservations", 10));
        config.put("borderlineMargin", doubleValue(ann, "borderlineMargin", 0.1));
        config.put("blendFactor", doubleValue(ann, "blendFactor", 0.6));
        config.put("cbrWeight", doubleValue(ann, "cbrWeight", 0.0));
        return new GovernanceDescriptor.TrustRoutedDescriptor(config);
    }

    private GovernanceDescriptor.CbrRoutedDescriptor extractCbrRouted(AnnotationInstance ann) {
        Map<String, Object> config = new HashMap<>();
        config.put("successWeight", doubleValue(ann, "successWeight", 1.0));
        config.put("gateExpiredWeight", doubleValue(ann, "gateExpiredWeight", 0.5));
        config.put("gateRejectedWeight", doubleValue(ann, "gateRejectedWeight", 0.25));
        config.put("failureWeight", doubleValue(ann, "failureWeight", 0.0));
        return new GovernanceDescriptor.CbrRoutedDescriptor(config);
    }

    private GovernanceDescriptor.AttestationDescriptor extractAttestation(AnnotationInstance ann) {
        Map<String, Object> config = new HashMap<>();
        AnnotationValue observer = ann.value("observer");
        if (observer != null) {
            config.put("observerClass", observer.asClass().name().toString());
        }
        AnnotationValue tag = ann.value("capabilityTag");
        config.put("capabilityTag", tag != null ? tag.asString() : "");
        return new GovernanceDescriptor.AttestationDescriptor(config);
    }

    private double doubleValue(AnnotationInstance ann, String name, double defaultVal) {
        AnnotationValue value = ann.value(name);
        return value != null ? value.asDouble() : defaultVal;
    }

    private int intValue(AnnotationInstance ann, String name, int defaultVal) {
        AnnotationValue value = ann.value(name);
        return value != null ? value.asInt() : defaultVal;
    }
}
