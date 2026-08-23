package io.casehub.blocks.annotations.deployment;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class CustomizeAnnotationStep {

    static final DotName CUSTOMIZE = DotName.createSimple("io.casehub.engine.annotations.Customize");

    static final DotName ABSTRACT_PATTERN_BUILDER =
            DotName.createSimple("io.casehub.blocks.agentic.pattern.AbstractPatternBuilder");

    static final Map<String, DotName> BUILDER_TO_PATTERN = Map.of(
            "io.casehub.blocks.agentic.pattern.SupervisorBuilder", PatternAnnotationStep.SUPERVISOR,
            "io.casehub.blocks.agentic.pattern.SequenceBuilder", PatternAnnotationStep.SEQUENCE,
            "io.casehub.blocks.agentic.pattern.ParallelBuilder", PatternAnnotationStep.PARALLEL,
            "io.casehub.blocks.agentic.pattern.LoopBuilder", PatternAnnotationStep.LOOP,
            "io.casehub.blocks.agentic.pattern.ConditionalBuilder", PatternAnnotationStep.CONDITIONAL,
            "io.casehub.blocks.agentic.pattern.DebateBuilder", PatternAnnotationStep.DEBATE,
            "io.casehub.blocks.agentic.pattern.VotingBuilder", PatternAnnotationStep.VOTING,
            "io.casehub.blocks.agentic.pattern.HtnBuilder", PatternAnnotationStep.HTN
    );

    static final Set<String> NON_CDI_PACKAGES = Set.of(
            "java.lang", "java.util", "java.io", "java.math", "java.time"
    );

    record CustomizeInfo(
            String declaringClass,
            String methodName,
            String builderType,
            List<String> cdiParameterTypes
    ) {}

    void validate(IndexView index) {
        scan(index);
    }

    List<CustomizeInfo> scan(IndexView index) {
        List<CustomizeInfo> results = new ArrayList<>();

        for (AnnotationInstance ann : index.getAnnotations(CUSTOMIZE)) {
            if (ann.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }
            MethodInfo method = ann.target().asMethod();

            if (method.parametersCount() == 0) {
                continue;
            }

            Type firstParamType = method.parameterType(0);
            String firstParamName = firstParamType.name().toString();

            if (!BUILDER_TO_PATTERN.containsKey(firstParamName)) {
                continue;
            }

            validatePatternMatch(method, firstParamName);
            List<String> cdiParams = validateAndExtractCdiParams(method);

            results.add(new CustomizeInfo(
                    method.declaringClass().name().toString(),
                    method.name(),
                    firstParamName,
                    cdiParams
            ));
        }

        return results;
    }

    private void validatePatternMatch(MethodInfo method, String builderTypeName) {
        DotName expectedPattern = BUILDER_TO_PATTERN.get(builderTypeName);
        if (expectedPattern == null) {
            return;
        }

        ClassInfo declaringClass = method.declaringClass();
        boolean hasMatchingPattern = false;
        for (MethodInfo m : declaringClass.methods()) {
            if (m.hasAnnotation(expectedPattern)) {
                hasMatchingPattern = true;
                break;
            }
        }

        if (!hasMatchingPattern) {
            throw new IllegalStateException(
                    "@Customize method " + declaringClass.name() + "." + method.name()
                            + " uses " + builderTypeName.substring(builderTypeName.lastIndexOf('.') + 1)
                            + " but the declaring class has no matching pattern annotation");
        }
    }

    private List<String> validateAndExtractCdiParams(MethodInfo method) {
        List<String> cdiParams = new ArrayList<>();

        for (int i = 1; i < method.parametersCount(); i++) {
            Type paramType = method.parameterType(i);
            String typeName = paramType.name().toString();

            if (isNonCdiType(typeName)) {
                throw new IllegalStateException(
                        "@Customize parameter '" + typeName + "' of method "
                                + method.declaringClass().name() + "." + method.name()
                                + " is not a CDI bean — use a CDI-managed type");
            }

            cdiParams.add(typeName);
        }

        return cdiParams;
    }

    private boolean isNonCdiType(String typeName) {
        for (String pkg : NON_CDI_PACKAGES) {
            if (typeName.startsWith(pkg + ".")) {
                return true;
            }
        }
        return typeName.indexOf('.') == -1;
    }
}
