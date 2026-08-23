package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.annotations.runtime.PatternDescriptor;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PatternAnnotationStep {

    static final DotName SUPERVISOR = DotName.createSimple("io.casehub.blocks.annotations.Supervisor");
    static final DotName SEQUENCE = DotName.createSimple("io.casehub.blocks.annotations.Sequence");
    static final DotName PARALLEL = DotName.createSimple("io.casehub.blocks.annotations.Parallel");
    static final DotName LOOP = DotName.createSimple("io.casehub.blocks.annotations.Loop");
    static final DotName CONDITIONAL = DotName.createSimple("io.casehub.blocks.annotations.Conditional");
    static final DotName DEBATE = DotName.createSimple("io.casehub.blocks.annotations.Debate");
    static final DotName VOTING = DotName.createSimple("io.casehub.blocks.annotations.Voting");
    static final DotName HTN = DotName.createSimple("io.casehub.blocks.annotations.Htn");

    static final DotName AGENT = DotName.createSimple("io.casehub.blocks.annotations.Agent");
    static final DotName DEBATER = DotName.createSimple("io.casehub.blocks.annotations.Debater");
    static final DotName VOTER = DotName.createSimple("io.casehub.blocks.annotations.Voter");
    static final DotName JUDGE = DotName.createSimple("io.casehub.blocks.annotations.Judge");

    static final DotName WORKER = DotName.createSimple("io.casehub.engine.annotations.Worker");
    static final DotName AGENT_REF = DotName.createSimple("io.casehub.blocks.agentic.AgentRef");

    static final Map<DotName, PatternType> PATTERN_ANNOTATIONS = Map.of(
            SUPERVISOR, PatternType.SUPERVISOR,
            SEQUENCE, PatternType.SEQUENCE,
            PARALLEL, PatternType.PARALLEL,
            LOOP, PatternType.LOOP,
            CONDITIONAL, PatternType.CONDITIONAL,
            DEBATE, PatternType.DEBATE,
            VOTING, PatternType.VOTING,
            HTN, PatternType.HTN
    );

    static final Set<DotName> ROLE_ANNOTATIONS = Set.of(AGENT, DEBATER, VOTER, JUDGE);

    List<PatternDescriptor> scan(IndexView index) {
        Map<MethodInfo, List<AnnotationInstance>> patternMethods = collectPatternMethods(index);
        List<PatternDescriptor>                   descriptors    = new ArrayList<>();
        Set<String>                               seenBeanNames  = new java.util.HashSet<>();

        for (var entry : patternMethods.entrySet()) {
            MethodInfo               method      = entry.getKey();
            List<AnnotationInstance> patternAnns = entry.getValue();

            validateSinglePattern(method, patternAnns);
            validateNoWorker(method);

            AnnotationInstance patternAnn  = patternAnns.get(0);
            PatternType        patternType = PATTERN_ANNOTATIONS.get(patternAnn.name());

            List<PatternDescriptor.AgentParticipant> participants = extractParticipants(method);
            Map<String, Object>                      attributes   = extractAttributes(patternAnn, patternType);
            String                                   beanName     = resolveBeanName(patternAnn, method);

            if (!seenBeanNames.add(beanName)) {
                throw new IllegalStateException(
                        "Method " + method.declaringClass().name() + "." + method.name()
                        + " produces duplicate bean name '" + beanName
                        + "' — each pattern must have a unique name");
            }

            descriptors.add(new PatternDescriptor(patternType, attributes, participants, beanName));
        }

        return descriptors;
    }

    private Map<MethodInfo, List<AnnotationInstance>> collectPatternMethods(IndexView index) {
        Map<MethodInfo, List<AnnotationInstance>> result = new LinkedHashMap<>();
        for (DotName annName : PATTERN_ANNOTATIONS.keySet()) {
            for (AnnotationInstance ann : index.getAnnotations(annName)) {
                if (ann.target().kind() == AnnotationTarget.Kind.METHOD) {
                    result.computeIfAbsent(ann.target().asMethod(), k -> new ArrayList<>()).add(ann);
                }
            }
        }
        return result;
    }

    private void validateSinglePattern(MethodInfo method, List<AnnotationInstance> patternAnns) {
        if (patternAnns.size() > 1) {
            throw new IllegalStateException(
                    "Method " + method.declaringClass().name() + "." + method.name()
                            + " has multiple pattern annotations — only one pattern per method");
        }
    }

    private void validateNoWorker(MethodInfo method) {
        if (method.hasAnnotation(WORKER)) {
            throw new IllegalStateException(
                    "Method " + method.declaringClass().name() + "." + method.name()
                            + " has both @Worker and a pattern annotation — "
                            + "patterns are standalone interfaces, reference via @Worker capability");
        }
    }

    private List<PatternDescriptor.AgentParticipant> extractParticipants(MethodInfo method) {
        List<PatternDescriptor.AgentParticipant> participants = new ArrayList<>();

        for (int i = 0; i < method.parametersCount(); i++) {
            Type paramType = method.parameterType(i);
            if (!paramType.name().equals(AGENT_REF)) {
                continue;
            }

            AnnotationInstance roleAnn = findRoleAnnotation(method, i);
            if (roleAnn == null) {
                throw new IllegalStateException(
                        "AgentRef parameter at position " + i + " on method "
                                + method.declaringClass().name() + "." + method.name()
                                + " has no role annotation — use @Agent, @Debater, @Voter, or @Judge");
            }

            validatePromptOrAgentId(roleAnn, method);

            String label = extractLabel(roleAnn);
            String role = extractRole(roleAnn);
            String systemPrompt = stringValue(roleAnn, "systemPrompt");
            String agentId = stringValue(roleAnn, "agentId");
            boolean isJudge = roleAnn.name().equals(JUDGE);

            participants.add(new PatternDescriptor.AgentParticipant(label, role, systemPrompt, agentId, isJudge));
        }

        return participants;
    }

    private AnnotationInstance findRoleAnnotation(MethodInfo method, int paramIndex) {
        for (AnnotationInstance ann : method.annotations()) {
            if (ann.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER
                    && ann.target().asMethodParameter().position() == paramIndex
                    && ROLE_ANNOTATIONS.contains(ann.name())) {
                return ann;
            }
        }
        return null;
    }

    private void validatePromptOrAgentId(AnnotationInstance roleAnn, MethodInfo method) {
        String systemPrompt = stringValue(roleAnn, "systemPrompt");
        String agentId = stringValue(roleAnn, "agentId");

        boolean hasPrompt = !systemPrompt.isEmpty();
        boolean hasId = !agentId.isEmpty();

        if (hasPrompt && hasId) {
            throw new IllegalStateException(
                    "@" + roleAnn.name().local() + " parameter on method "
                            + method.declaringClass().name() + "." + method.name()
                            + " specifies both systemPrompt and agentId — use one");
        }
        if (!hasPrompt && !hasId) {
            throw new IllegalStateException(
                    "@" + roleAnn.name().local() + " parameter on method "
                            + method.declaringClass().name() + "." + method.name()
                            + " must specify systemPrompt or agentId");
        }
    }

    private String extractLabel(AnnotationInstance roleAnn) {
        if (roleAnn.name().equals(AGENT)) {
            String name = stringValue(roleAnn, "name");
            return name.isEmpty() ? "" : name;
        }
        if (roleAnn.name().equals(DEBATER) || roleAnn.name().equals(VOTER)) {
            return stringValue(roleAnn, "role");
        }
        if (roleAnn.name().equals(JUDGE)) {
            return "judge";
        }
        return "";
    }

    private String extractRole(AnnotationInstance roleAnn) {
        if (roleAnn.name().equals(DEBATER) || roleAnn.name().equals(VOTER)) {
            return stringValue(roleAnn, "role");
        }
        if (roleAnn.name().equals(AGENT)) {
            return stringValue(roleAnn, "name");
        }
        if (roleAnn.name().equals(JUDGE)) {
            return "judge";
        }
        return "";
    }

    private Map<String, Object> extractAttributes(AnnotationInstance ann, PatternType patternType) {
        Map<String, Object> attrs = new HashMap<>();
        for (AnnotationValue value : ann.values()) {
            String name = value.name();
            if ("name".equals(name)) continue;
            switch (value.kind()) {
                case INTEGER -> attrs.put(name, value.asInt());
                case LONG -> attrs.put(name, value.asLong());
                case DOUBLE -> attrs.put(name, value.asDouble());
                case FLOAT -> attrs.put(name, value.asFloat());
                case BOOLEAN -> attrs.put(name, value.asBoolean());
                case STRING -> attrs.put(name, value.asString());
                case CLASS -> attrs.put(name, value.asClass().name().toString());
                default -> attrs.put(name, value.toString());
            }
        }
        return attrs;
    }

    private String resolveBeanName(AnnotationInstance ann, MethodInfo method) {
        String name = stringValue(ann, "name");
        return name.isEmpty() ? method.name() : name;
    }

    private String stringValue(AnnotationInstance ann, String name) {
        AnnotationValue value = ann.value(name);
        return value != null ? value.asString() : "";
    }
}
