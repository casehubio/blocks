# casehub-blocks-annotations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #116 — feat: casehub-blocks-annotations module
**Issue group:** #116

**Goal:** Annotation-driven programming model for CaseHub orchestration patterns and governance — 8 pattern annotations, 4 governance meta-annotations, Quarkus build extension, 3 example modules.

**Architecture:** Quarkus extension (runtime + deployment split). Runtime defines annotations and descriptors. Deployment scans Jandex, validates, and generates `ExecutionModel<Object>` CDI beans via `@Record(RUNTIME_INIT)` recorder. Governance annotations compose onto both engine `@Worker` methods and blocks pattern methods.

**Tech Stack:** Java 21, Quarkus (build extension), Jandex (annotation scanning), JUnit 5, Mockito, AssertJ

## Global Constraints

- Pre-release — breaking changes cost nothing
- No `langchain4j-agentic` dependency (ADR-0004)
- Bare annotation names: `@Supervisor`, `@Debate`, etc. (D5)
- Nested module layout: `annotations/runtime/` + `annotations/deployment/` (D2)
- Annotation defaults match builder no-arg constructor defaults exactly
- `ExecutionModel<Object>` — annotations erase the generic type parameter
- CDI bean qualification via `@Named` (method name or explicit `name` attribute)
- SPI instantiation: if value equals annotation default → builder-equivalent construction; else CDI-first, then no-arg constructor, then build error
- Every `AgentRef` parameter must carry a role annotation (`@Agent`, `@Debater`, `@Voter`, `@Judge`)
- Build extension ordering: engine → eidos → work → blocks (via `@Consume`)
- Test build extension validation with Jandex Indexer API, not `QuarkusUnitTest` (GE-20260819-e4a624)
- `SyntheticBeanBuildItem` requires `.setRuntimeInit()` for `RUNTIME_INIT` recorders (GE-20260817-48caeb)
- Prerequisite: blocks#156 — `SequenceBuilder.agents(RoutingCandidate...)` must set routing/termination (recorder uses RoutingCandidate for eidos identity pairing)
- Governance CDI aggregation: ONE composite `TrustRoutingPolicyProvider` per app (multiple `@TrustRouted` dispatch via `forCapability()`). Multiple `@CbrRouted` with different weights → build error (one set per app)
- Governance only takes effect when the pattern is used as a case worker (via `@Worker` capability reference). Standalone `execute()` invocation bypasses engine dispatch — governance beans exist but are not consumed

---

## Batch 1: Runtime Module — annotations + descriptors

### Task 1: Maven module scaffold

**Files:**
- Create: `annotations/pom.xml` (parent aggregator)
- Create: `annotations/runtime/pom.xml`
- Create: `annotations/deployment/pom.xml`
- Modify: `pom.xml` (root — add `annotations` module)

**Interfaces:**
- Produces: Maven reactor with `casehub-blocks-annotations` (runtime) and `casehub-blocks-annotations-deployment` (deployment) artifacts

- [ ] **Step 1: Create parent aggregator pom**

```xml
<!-- annotations/pom.xml -->
<project>
  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-parent</artifactId>
    <version>0.2-SNAPSHOT</version>
  </parent>
  <artifactId>casehub-blocks-annotations-parent</artifactId>
  <packaging>pom</packaging>
  <modules>
    <module>runtime</module>
    <module>deployment</module>
  </modules>
</project>
```

- [ ] **Step 2: Create runtime pom**

```xml
<!-- annotations/runtime/pom.xml -->
<project>
  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-annotations-parent</artifactId>
    <version>0.2-SNAPSHOT</version>
  </parent>
  <artifactId>casehub-blocks-annotations</artifactId>
  <dependencies>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-blocks</artifactId>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-engine-api</artifactId>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-engine-annotations</artifactId>
    </dependency>
    <!-- test -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 3: Create deployment pom**

```xml
<!-- annotations/deployment/pom.xml -->
<project>
  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-annotations-parent</artifactId>
    <version>0.2-SNAPSHOT</version>
  </parent>
  <artifactId>casehub-blocks-annotations-deployment</artifactId>
  <dependencies>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-blocks-annotations</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-arc-deployment</artifactId>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-engine-annotations-deployment</artifactId>
    </dependency>
    <!-- test -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.jboss</groupId>
      <artifactId>jandex</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 4: Add annotations module to root reactor**

Add `<module>annotations</module>` to root `pom.xml` `<modules>` block, after `engine-adapter`.

- [ ] **Step 5: Verify build compiles**

Run: `mvn --batch-mode compile -pl annotations/runtime,annotations/deployment -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```
feat(#116): scaffold annotations module — runtime + deployment poms
```

### Task 2: Pattern and role annotation definitions

**Files:**
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Supervisor.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Sequence.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Parallel.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Loop.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Conditional.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Debate.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Voting.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Htn.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Agent.java` (role)
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Debater.java` (role)
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Voter.java` (role)
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Judge.java` (role)
- Test: `annotations/runtime/src/test/java/io/casehub/blocks/annotations/PatternAnnotationTest.java`

**Interfaces:**
- Produces: All 12 annotation types (8 pattern + 4 role) with attributes matching spec §Pattern Annotations and §Role Annotations

- [ ] **Step 1: Write annotation reflection test**

```java
package io.casehub.blocks.annotations;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.lang.annotation.*;

class PatternAnnotationTest {

    @Test
    void supervisor_has_expected_attributes() {
        assertThat(Supervisor.class.isAnnotation()).isTrue();
        assertThat(Supervisor.class.getAnnotation(Retention.class).value())
            .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Supervisor.class.getAnnotation(Target.class).value())
            .containsExactly(ElementType.METHOD);

        var methods = Supervisor.class.getDeclaredMethods();
        var names = java.util.Arrays.stream(methods)
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder(
            "name", "maxIterations", "routing", "decomposition", "aggregation");
    }

    @Test
    void debate_has_expected_attributes() {
        var methods = Debate.class.getDeclaredMethods();
        var names = java.util.Arrays.stream(methods)
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("name", "maxRounds");
    }

    @Test
    void voting_has_expected_attributes() {
        var names = java.util.Arrays.stream(Voting.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("name", "strategy");
    }

    @Test
    void htn_has_expected_attributes() {
        var names = java.util.Arrays.stream(Htn.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("name", "decomposition");
    }

    @Test
    void sequence_has_expected_attributes() {
        var names = java.util.Arrays.stream(Sequence.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("name");
    }

    @Test
    void parallel_has_expected_attributes() {
        var names = java.util.Arrays.stream(Parallel.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("name");
    }

    @Test
    void loop_has_expected_attributes() {
        var names = java.util.Arrays.stream(Loop.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("name", "maxIterations");
    }

    @Test
    void conditional_has_expected_attributes() {
        var names = java.util.Arrays.stream(Conditional.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("name");
    }

    @Test
    void all_pattern_annotations_are_method_level_runtime_retained() {
        for (var ann : new Class<?>[]{ Supervisor.class, Debate.class, Voting.class, Htn.class,
                Sequence.class, Parallel.class, Loop.class, Conditional.class }) {
            assertThat(ann.getAnnotation(Retention.class).value())
                .as(ann.getSimpleName() + " retention")
                .isEqualTo(RetentionPolicy.RUNTIME);
            assertThat(ann.getAnnotation(Target.class).value())
                .as(ann.getSimpleName() + " target")
                .containsExactly(ElementType.METHOD);
        }
    }

    @Test
    void role_annotations_are_parameter_level() {
        for (var ann : new Class<?>[]{ Agent.class, Debater.class, Voter.class, Judge.class }) {
            assertThat(ann.getAnnotation(Target.class).value())
                .as(ann.getSimpleName())
                .containsExactly(ElementType.PARAMETER);
        }
    }

    @Test
    void role_annotations_have_systemPrompt_and_agentId() {
        for (var ann : new Class<?>[]{ Agent.class, Debater.class, Voter.class, Judge.class }) {
            var names = java.util.Arrays.stream(ann.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(names).as(ann.getSimpleName())
                .contains("systemPrompt", "agentId");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl annotations/runtime -Dtest=PatternAnnotationTest`
Expected: FAIL — annotation classes don't exist yet

- [ ] **Step 3: Create all 8 pattern annotations**

Create each annotation per spec §Pattern Annotations. Key: attribute defaults must match builder no-arg constructor defaults exactly.

`Supervisor.java`:
```java
package io.casehub.blocks.annotations;

import io.casehub.blocks.agentic.aggregation.PassThrough;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.blocks.agentic.decomposition.IdentityDecomposition;
import io.casehub.blocks.agentic.routing.RoutingStrategy;
import io.casehub.blocks.agentic.aggregation.AggregationStrategy;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Supervisor {
    String name() default "";
    int maxIterations() default 10;
    Class<? extends RoutingStrategy> routing() default FirstMatchRouting.class;
    Class<? extends DecompositionStrategy> decomposition() default IdentityDecomposition.class;
    Class<? extends AggregationStrategy> aggregation() default PassThrough.class;
}
```

`Debate.java`:
```java
package io.casehub.blocks.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Debate {
    String name() default "";
    int maxRounds() default 5;
}
```

`Voting.java`:
```java
package io.casehub.blocks.annotations;

import io.casehub.blocks.agentic.aggregation.AggregationStrategy;
import io.casehub.blocks.agentic.aggregation.MajorityVote;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Voting {
    String name() default "";
    Class<? extends AggregationStrategy> strategy() default MajorityVote.class;
}
```

`Htn.java`:
```java
package io.casehub.blocks.annotations;

import io.casehub.blocks.agentic.decomposition.StaticDecomposition;
import io.casehub.engine.plan.DecompositionStrategy;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Htn {
    String name() default "";
    Class<? extends DecompositionStrategy> decomposition() default StaticDecomposition.class;
}
```

`Sequence.java`, `Parallel.java`, `Loop.java`, `Conditional.java` — per spec (minimal attributes: name only; Loop adds maxIterations).

- [ ] **Step 4: Create 4 role annotations**

`Agent.java`:
```java
package io.casehub.blocks.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Agent {
    String name() default "";
    String systemPrompt() default "";
    String agentId() default "";
}
```

`Debater.java`, `Voter.java`, `Judge.java` — per spec §Role Annotations. All have `systemPrompt` + `agentId`. `Debater` and `Voter` have `role()`. `Judge` does not.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl annotations/runtime -Dtest=PatternAnnotationTest`
Expected: PASS

- [ ] **Step 6: Commit**

```
feat(#116): 8 pattern annotations + 4 role annotations
```

### Task 3: Governance annotations + descriptors

**Files:**
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/OversightGate.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/TrustRouted.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/CbrRouted.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/Attestation.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/runtime/PatternDescriptor.java`
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/runtime/GovernanceDescriptor.java`
- Test: `annotations/runtime/src/test/java/io/casehub/blocks/annotations/GovernanceAnnotationTest.java`
- Test: `annotations/runtime/src/test/java/io/casehub/blocks/annotations/runtime/DescriptorTest.java`

**Interfaces:**
- Produces: 4 governance annotation types, `PatternDescriptor` record, `GovernanceDescriptor` record
- Consumes: `ActionRiskClassifier` (engine-api), `LifecycleAttestationObserver` (blocks)

- [ ] **Step 1: Write governance annotation reflection test**

```java
package io.casehub.blocks.annotations;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.lang.annotation.*;

class GovernanceAnnotationTest {

    @Test
    void oversightGate_has_value_attribute() {
        var methods = OversightGate.class.getDeclaredMethods();
        var names = java.util.Arrays.stream(methods)
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder(
            "value", "reversible", "candidateGroups");
    }

    @Test
    void trustRouted_attributes_match_policy_defaults() {
        var names = java.util.Arrays.stream(TrustRouted.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder(
            "threshold", "minimumObservations", "borderlineMargin",
            "blendFactor", "cbrWeight");
    }

    @Test
    void trustRouted_defaults_match_TrustRoutingPolicy_DEFAULT() throws Exception {
        assertThat(TrustRouted.class.getMethod("threshold").getDefaultValue()).isEqualTo(0.7);
        assertThat(TrustRouted.class.getMethod("minimumObservations").getDefaultValue()).isEqualTo(10);
        assertThat(TrustRouted.class.getMethod("borderlineMargin").getDefaultValue()).isEqualTo(0.1);
        assertThat(TrustRouted.class.getMethod("blendFactor").getDefaultValue()).isEqualTo(0.6);
        assertThat(TrustRouted.class.getMethod("cbrWeight").getDefaultValue()).isEqualTo(0.0);
    }

    @Test
    void governance_annotations_are_method_level() {
        for (var ann : new Class<?>[]{ OversightGate.class, TrustRouted.class,
                                        CbrRouted.class, Attestation.class }) {
            assertThat(ann.getAnnotation(Target.class).value())
                .as(ann.getSimpleName())
                .containsExactly(ElementType.METHOD);
        }
    }
}
```

- [ ] **Step 2: Write descriptor test**

```java
package io.casehub.blocks.annotations.runtime;

import io.casehub.blocks.agentic.model.PatternType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;

class DescriptorTest {

    @Test
    void patternDescriptor_holds_type_and_attributes() {
        var attrs = Map.of("maxRounds", (Object) 5);
        var participant = new PatternDescriptor.AgentParticipant(
            "critic", PatternDescriptor.ParticipantRole.DEBATER, "Challenge", "");
        var desc = new PatternDescriptor(PatternType.DEBATE, attrs, List.of(participant), "review");
        assertThat(desc.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(desc.attributes().get("maxRounds")).isEqualTo(5);
        assertThat(desc.beanName()).isEqualTo("review");
        assertThat(desc.participants().get(0).participantRole())
            .isEqualTo(PatternDescriptor.ParticipantRole.DEBATER);
    }

    @Test
    void participantRole_enum_covers_all_role_annotations() {
        assertThat(PatternDescriptor.ParticipantRole.values())
            .containsExactly(
                PatternDescriptor.ParticipantRole.AGENT,
                PatternDescriptor.ParticipantRole.DEBATER,
                PatternDescriptor.ParticipantRole.VOTER,
                PatternDescriptor.ParticipantRole.JUDGE);
    }

    @Test
    void oversightGateDescriptor_holds_classifier_and_config() {
        var desc = new GovernanceDescriptor.OversightGateDescriptor(
            io.casehub.api.spi.ActionRiskClassifier.class, true, List.of("ops-team"));
        assertThat(desc.reversible()).isTrue();
        assertThat(desc.candidateGroups()).containsExactly("ops-team");
    }

    @Test
    void trustRoutedDescriptor_holds_policy_values() {
        var desc = new GovernanceDescriptor.TrustRoutedDescriptor(0.8, 15, 0.1, 0.6, 0.3);
        assertThat(desc.threshold()).isEqualTo(0.8);
        assertThat(desc.minimumObservations()).isEqualTo(15);
    }

    @Test
    void cbrRoutedDescriptor_holds_outcome_weights() {
        var desc = new GovernanceDescriptor.CbrRoutedDescriptor(1.0, 0.5, 0.25, 0.0);
        assertThat(desc.successWeight()).isEqualTo(1.0);
        assertThat(desc.failureWeight()).isEqualTo(0.0);
    }

    @Test
    void governanceDescriptor_sealed_hierarchy_is_exhaustive() {
        assertThat(GovernanceDescriptor.class.isSealed()).isTrue();
        assertThat(GovernanceDescriptor.class.getPermittedSubclasses())
            .hasSize(4);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl annotations/runtime`
Expected: FAIL — types don't exist

- [ ] **Step 4: Create 4 governance annotations per spec**

`OversightGate.java`, `TrustRouted.java`, `CbrRouted.java`, `Attestation.java` — per spec §Governance Meta-Annotations. Attributes match spec exactly (TrustRouted: threshold, minimumObservations, borderlineMargin, blendFactor, cbrWeight).

- [ ] **Step 5: Create PatternDescriptor and GovernanceDescriptor types**

```java
package io.casehub.blocks.annotations.runtime;

import io.casehub.blocks.agentic.model.PatternType;
import java.util.*;

public record PatternDescriptor(
    PatternType patternType,
    Map<String, Object> attributes,
    List<AgentParticipant> participants,
    String beanName
) {
    public enum ParticipantRole { AGENT, DEBATER, VOTER, JUDGE }

    public record AgentParticipant(
        String label,
        ParticipantRole participantRole,
        String systemPrompt,
        String agentId
    ) {}
}
```

`label` is the display name for routing and logging — sourced from `@Agent.name()`, `@Debater.role()`, `@Voter.role()`, or the Java parameter name as fallback. `participantRole` determines recorder wiring (e.g., `JUDGE` → `DebateBuilder.judge()`).

```java
package io.casehub.blocks.annotations.runtime;

import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.blocks.attestation.LifecycleAttestationObserver;
import java.util.List;

public sealed interface GovernanceDescriptor {

    record OversightGateDescriptor(
        Class<? extends ActionRiskClassifier> classifierClass,
        boolean reversible,
        List<String> candidateGroups
    ) implements GovernanceDescriptor {}

    record TrustRoutedDescriptor(
        double threshold,
        int minimumObservations,
        double borderlineMargin,
        double blendFactor,
        double cbrWeight
    ) implements GovernanceDescriptor {}

    record CbrRoutedDescriptor(
        double successWeight,
        double gateExpiredWeight,
        double gateRejectedWeight,
        double failureWeight
    ) implements GovernanceDescriptor {}

    record AttestationDescriptor(
        Class<? extends LifecycleAttestationObserver> observerClass,
        String capabilityTag
    ) implements GovernanceDescriptor {}
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl annotations/runtime`
Expected: PASS

- [ ] **Step 7: Commit**

```
feat(#116): 4 governance annotations + PatternDescriptor + GovernanceDescriptor
```

---

## Batch 2: Deployment Module — pattern processing

### Task 4: Pattern annotation scanning + validation

**Files:**
- Create: `annotations/deployment/src/main/java/io/casehub/blocks/annotations/deployment/BlocksAnnotationsProcessor.java`
- Create: `annotations/deployment/src/main/java/io/casehub/blocks/annotations/deployment/PatternAnnotationStep.java`
- Test: `annotations/deployment/src/test/java/io/casehub/blocks/annotations/deployment/PatternValidationTest.java`
- Test fixture: `annotations/deployment/src/test/java/io/casehub/blocks/annotations/deployment/fixtures/` (annotated test interfaces)

**Interfaces:**
- Consumes: Jandex `CombinedIndexBuildItem`
- Produces: `PatternDescriptor` list for the recorder

- [ ] **Step 1: Write validation test — dual-annotation conflict**

```java
package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.annotations.*;
import org.jboss.jandex.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.io.*;

class PatternValidationTest {

    private Index indexClasses(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> clazz : classes) {
            String resourceName = "/" + clazz.getName().replace('.', '/') + ".class";
            try (InputStream stream = clazz.getResourceAsStream(resourceName)) {
                indexer.index(stream);
            }
        }
        // also index the annotation classes themselves
        for (Class<?> ann : new Class<?>[]{ Debate.class, Supervisor.class,
                Agent.class, Debater.class, Judge.class,
                io.casehub.engine.annotations.Worker.class }) {
            String resourceName = "/" + ann.getName().replace('.', '/') + ".class";
            try (InputStream stream = ann.getResourceAsStream(resourceName)) {
                indexer.index(stream);
            }
        }
        return indexer.complete();
    }

    // Fixture: dual pattern annotation — should fail
    interface DualPatternBad {
        @Debate(maxRounds = 3)
        @Supervisor
        String review(@Debater(role = "a", systemPrompt = "p") io.casehub.blocks.agentic.AgentRef a);
    }

    @Test
    void rejects_dual_pattern_annotations() throws IOException {
        Index index = indexClasses(DualPatternBad.class);
        var step = new PatternAnnotationStep();

        assertThatThrownBy(() -> step.scan(index))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("multiple pattern annotations");
    }

    // Fixture: missing role annotation — should fail
    interface MissingRoleBad {
        @Debate(maxRounds = 3)
        String review(io.casehub.blocks.agentic.AgentRef bare);
    }

    @Test
    void rejects_agentref_without_role_annotation() throws IOException {
        Index index = indexClasses(MissingRoleBad.class);
        var step = new PatternAnnotationStep();

        assertThatThrownBy(() -> step.scan(index))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no role annotation");
    }

    // Fixture: @Worker + pattern annotation — should fail
    interface WorkerAndPatternBad {
        @io.casehub.engine.annotations.Worker(capability = "review")
        @Debate(maxRounds = 3)
        String review(@Debater(role = "a", systemPrompt = "p") io.casehub.blocks.agentic.AgentRef a);
    }

    @Test
    void rejects_worker_and_pattern_on_same_method() throws IOException {
        Index index = indexClasses(WorkerAndPatternBad.class);
        var step = new PatternAnnotationStep();

        assertThatThrownBy(() -> step.scan(index))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("@Worker");
    }

    // Fixture: valid debate — should pass
    interface ValidDebate {
        @Debate(maxRounds = 5)
        String review(
            @Debater(role = "critic", systemPrompt = "Challenge") io.casehub.blocks.agentic.AgentRef critic,
            @Debater(role = "advocate", systemPrompt = "Defend") io.casehub.blocks.agentic.AgentRef advocate,
            @Judge(systemPrompt = "Judge") io.casehub.blocks.agentic.AgentRef judge,
            String document);
    }

    @Test
    void accepts_valid_debate() throws IOException {
        Index index = indexClasses(ValidDebate.class);
        var step = new PatternAnnotationStep();
        var descriptors = step.scan(index);

        assertThat(descriptors).hasSize(1);
        assertThat(descriptors.get(0).patternType())
            .isEqualTo(io.casehub.blocks.agentic.model.PatternType.DEBATE);
        assertThat(descriptors.get(0).participants()).hasSize(3);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl annotations/deployment -Dtest=PatternValidationTest`
Expected: FAIL — PatternAnnotationStep doesn't exist

- [ ] **Step 3: Implement PatternAnnotationStep**

Jandex-based scanner that:
1. Finds all methods with any of the 8 pattern annotation DotNames
2. Validates no method has multiple pattern annotations
3. Validates all `AgentRef` parameters carry a role annotation
4. Validates role annotations have exactly one of systemPrompt/agentId
5. Extracts `PatternDescriptor` from annotation values and parameters

- [ ] **Step 4: Implement BlocksAnnotationsProcessor stub**

```java
package io.casehub.blocks.annotations.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class BlocksAnnotationsProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("casehub-blocks-annotations");
    }
}
```

The main `@BuildStep` method (wired in Task 5) will use `@Consume(EngineAnnotationsCompleteBuildItem.class)` to enforce ordering after the engine build extension. This build item is produced by `casehub-engine-annotations-deployment` — if it doesn't exist yet, file an engine issue to add it.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl annotations/deployment -Dtest=PatternValidationTest`
Expected: PASS

- [ ] **Step 6: Commit**

```
feat(#116): pattern annotation scanning + validation rules
```

### Task 5: Recorder + ExecutionModel CDI bean generation

**Files:**
- Create: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/runtime/BlocksAnnotationsRecorder.java`
- Modify: `annotations/deployment/src/main/java/io/casehub/blocks/annotations/deployment/BlocksAnnotationsProcessor.java`
- Test: `annotations/deployment/src/test/java/io/casehub/blocks/annotations/deployment/RecorderWiringTest.java`

**Interfaces:**
- Consumes: `PatternDescriptor` list from PatternAnnotationStep
- Produces: `SyntheticBeanBuildItem` for each `ExecutionModel<Object>` with `@Named` qualifier

- [ ] **Step 1: Write recorder wiring test**

Test that the recorder creates `ExecutionModel` instances matching builder output for each pattern type. Focus on debate (most complex) and supervisor (most attributes).

```java
package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.annotations.runtime.*;
import io.casehub.blocks.agentic.model.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*;

class RecorderWiringTest {

    @Test
    void recorder_builds_debate_model_with_correct_wiring() {
        var recorder = new BlocksAnnotationsRecorder();
        var attrs = Map.<String, Object>of("maxRounds", 3);
        var participants = List.of(
            new PatternDescriptor.AgentParticipant("critic",
                PatternDescriptor.ParticipantRole.DEBATER, "Challenge", ""),
            new PatternDescriptor.AgentParticipant("advocate",
                PatternDescriptor.ParticipantRole.DEBATER, "Defend", ""),
            new PatternDescriptor.AgentParticipant("judge",
                PatternDescriptor.ParticipantRole.JUDGE, "Judge", "")
        );
        var desc = new PatternDescriptor(PatternType.DEBATE, attrs, participants, "review");

        var supplier = recorder.createExecutionModel(desc);
        var model = supplier.get();

        assertThat(model).isNotNull();
        assertThat(model.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(model.task()).isEqualTo("debate");
        // candidateSupplier produces all participants as RoutingCandidates
        assertThat(model.candidateSupplier().get()).hasSize(3);
        // JUDGE participant triggers JudgeConvergence termination
        assertThat(model.termination())
            .isInstanceOf(io.casehub.blocks.agentic.termination.JudgeConvergence.class);
    }

    @Test
    void recorder_builds_debate_without_judge_uses_maxIterations() {
        var recorder = new BlocksAnnotationsRecorder();
        var attrs = Map.<String, Object>of("maxRounds", 7);
        var participants = List.of(
            new PatternDescriptor.AgentParticipant("critic",
                PatternDescriptor.ParticipantRole.DEBATER, "Challenge", ""),
            new PatternDescriptor.AgentParticipant("advocate",
                PatternDescriptor.ParticipantRole.DEBATER, "Defend", "")
        );
        var desc = new PatternDescriptor(PatternType.DEBATE, attrs, participants, "review");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.termination())
            .isInstanceOf(io.casehub.blocks.agentic.termination.MaxIterationsTermination.class);
    }

    @Test
    void recorder_builds_supervisor_model_with_defaults() {
        var recorder = new BlocksAnnotationsRecorder();
        var attrs = Map.<String, Object>of("maxIterations", 10);
        var participants = List.of(
            new PatternDescriptor.AgentParticipant("agent1",
                PatternDescriptor.ParticipantRole.AGENT, "Do work", "")
        );
        var desc = new PatternDescriptor(PatternType.SUPERVISOR, attrs, participants, "triage");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.routing())
            .isInstanceOf(io.casehub.blocks.agentic.routing.FirstMatchRouting.class);
        assertThat(model.candidateSupplier().get()).hasSize(1);
    }

    @Test
    void recorder_builds_sequence_with_agents_before_build() {
        // SequenceBuilder sets routing/termination ONLY in agents() —
        // recorder must call agents() before build() to avoid NPE
        var recorder = new BlocksAnnotationsRecorder();
        var attrs = Map.<String, Object>of();
        var participants = List.of(
            new PatternDescriptor.AgentParticipant("step1",
                PatternDescriptor.ParticipantRole.AGENT, "First step", ""),
            new PatternDescriptor.AgentParticipant("step2",
                PatternDescriptor.ParticipantRole.AGENT, "Second step", "")
        );
        var desc = new PatternDescriptor(PatternType.SEQUENCE, attrs, participants, "pipeline");

        var model = recorder.createExecutionModel(desc).get();

        assertThat(model.patternType()).isEqualTo(PatternType.SEQUENCE);
        assertThat(model.routing()).isNotNull();
        assertThat(model.termination()).isNotNull();
        assertThat(model.candidateSupplier().get()).hasSize(2);
    }

    @Test
    void recorder_creates_ExternalAgent_for_inline_systemPrompt() {
        // Inline agent (systemPrompt specified): recorder creates
        // AgentRef.ExternalAgent wrapping AgentProvider.invoke()
        var recorder = new BlocksAnnotationsRecorder();
        var attrs = Map.<String, Object>of("maxIterations", 10);
        var participants = List.of(
            new PatternDescriptor.AgentParticipant("triage",
                PatternDescriptor.ParticipantRole.AGENT, "Triage the incident", "")
        );
        var desc = new PatternDescriptor(PatternType.SUPERVISOR, attrs, participants, "triage");

        var model = recorder.createExecutionModel(desc).get();
        var candidates = model.candidateSupplier().get();

        assertThat(candidates.get(0).ref())
            .isInstanceOf(io.casehub.blocks.agentic.AgentRef.ExternalAgent.class);
        assertThat(candidates.get(0).ref().name()).isEqualTo("triage");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl annotations/deployment -Dtest=RecorderWiringTest`
Expected: FAIL

- [ ] **Step 3: Implement BlocksAnnotationsRecorder**

`@Recorder` class in runtime module. `createExecutionModel(PatternDescriptor)` returns `Supplier<ExecutionModel<Object>>` that constructs via the corresponding `Patterns.*()` builder. Maps `PatternType` → builder, applies attributes, wires participants as `AgentRef` instances.

**Builder-specific ordering constraints:**
- **SequenceBuilder:** `agents()` MUST be called before `build()`. The constructor does NOT set `routing` or `termination` — these are only set inside `agents(AgentRef...)`. Calling `build()` without `agents()` causes NPE via `Objects.requireNonNull(routing)`.
- **DebateBuilder:** If a `JUDGE` participant exists, call `judge(AgentRef)` before `build()`. This triggers `JudgeConvergence` termination in `build()`. Do NOT also call `convergence()` — these are mutually exclusive.

**Inline agent wiring (systemPrompt specified):** The recorder creates `AgentRef.ExternalAgent` by capturing the system prompt and resolving `AgentProvider` from CDI at `RUNTIME_INIT` via `Arc.container().select(AgentProvider.class).get()`. The closure invokes `agentProvider.invoke(AgentSessionConfig.of(systemPrompt, context.toString()))`, collects the `Multi<AgentEvent>` into an `AgentResult`. The `label` parameter is the participant's `label` field.

**SPI default-value detection:** When a developer-specified SPI class equals the annotation's declared default (e.g., `routing = FirstMatchRouting.class` on `@Supervisor`), the recorder uses path §1 (hardcoded builder-equivalent construction) instead of attempting CDI/constructor resolution.

- [ ] **Step 4: Wire recorder into BlocksAnnotationsProcessor**

Add `@BuildStep @Record(RUNTIME_INIT) @Consume(EngineAnnotationsCompleteBuildItem.class)` method that:
1. Calls `PatternAnnotationStep.scan(index)` to get descriptors
2. For each descriptor, calls `recorder.createExecutionModel(desc)` to get a supplier
3. Produces `SyntheticBeanBuildItem.configure(ExecutionModel.class).setRuntimeInit().addQualifier().named(desc.beanName()).supplier(supplier).done()`

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl annotations/deployment`
Expected: PASS

- [ ] **Step 6: Commit**

```
feat(#116): BlocksAnnotationsRecorder — ExecutionModel CDI bean generation
```

---

## Batch 3: Deployment Module — governance + @Customize

### Task 6: Governance annotation processing

**Files:**
- Create: `annotations/deployment/src/main/java/io/casehub/blocks/annotations/deployment/GovernanceAnnotationStep.java`
- Modify: `annotations/deployment/src/main/java/io/casehub/blocks/annotations/deployment/BlocksAnnotationsProcessor.java`
- Test: `annotations/deployment/src/test/java/io/casehub/blocks/annotations/deployment/GovernanceValidationTest.java`

**Interfaces:**
- Consumes: Jandex `CombinedIndexBuildItem`, pattern descriptors (for context detection)
- Produces: `GovernanceDescriptor` list, CDI beans for risk classifiers, trust policy keys, CBR weights, attestation observers

- [ ] **Step 1: Write governance validation test**

```java
package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.annotations.*;
import io.casehub.blocks.annotations.runtime.GovernanceDescriptor;
import org.jboss.jandex.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.io.*;

class GovernanceValidationTest {

    private Index indexClasses(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> clazz : classes) {
            String resourceName = "/" + clazz.getName().replace('.', '/') + ".class";
            try (InputStream stream = clazz.getResourceAsStream(resourceName)) {
                indexer.index(stream);
            }
        }
        for (Class<?> ann : new Class<?>[]{ OversightGate.class, TrustRouted.class,
                CbrRouted.class, Attestation.class, Debate.class, Debater.class, Judge.class,
                io.casehub.engine.annotations.Worker.class }) {
            String resourceName = "/" + ann.getName().replace('.', '/') + ".class";
            try (InputStream stream = ann.getResourceAsStream(resourceName)) {
                indexer.index(stream);
            }
        }
        return indexer.complete();
    }

    // Fixture: governance without target — should fail
    interface OrphanGovernanceBad {
        @OversightGate(TestClassifier.class)
        String doWork(String input);
    }

    @Test
    void rejects_governance_without_worker_or_pattern() throws IOException {
        Index index = indexClasses(OrphanGovernanceBad.class);
        var step = new GovernanceAnnotationStep();

        assertThatThrownBy(() -> step.scan(index))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no @Worker or pattern annotation");
    }

    // Fixture: governance on pattern method — should pass
    interface GovernedDebate {
        @Debate(maxRounds = 5)
        @OversightGate(TestClassifier.class)
        @TrustRouted(threshold = 0.8)
        String review(
            @Debater(role = "critic", systemPrompt = "Challenge") io.casehub.blocks.agentic.AgentRef critic,
            @Judge(systemPrompt = "Judge") io.casehub.blocks.agentic.AgentRef judge,
            String document);
    }

    @Test
    void accepts_governance_on_pattern_method() throws IOException {
        Index index = indexClasses(GovernedDebate.class);
        var step = new GovernanceAnnotationStep();
        var descriptors = step.scan(index);

        assertThat(descriptors).hasSize(2);
        assertThat(descriptors).anySatisfy(d ->
            assertThat(d).isInstanceOf(GovernanceDescriptor.OversightGateDescriptor.class));
        assertThat(descriptors).anySatisfy(d ->
            assertThat(d).isInstanceOf(GovernanceDescriptor.TrustRoutedDescriptor.class));
    }

    @Test
    void trustRoutedDescriptor_captures_annotation_values() throws IOException {
        Index index = indexClasses(GovernedDebate.class);
        var step = new GovernanceAnnotationStep();
        var descriptors = step.scan(index);

        var trust = descriptors.stream()
            .filter(d -> d instanceof GovernanceDescriptor.TrustRoutedDescriptor)
            .map(d -> (GovernanceDescriptor.TrustRoutedDescriptor) d)
            .findFirst().orElseThrow();
        assertThat(trust.threshold()).isEqualTo(0.8);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement GovernanceAnnotationStep**

Jandex-based scanner that:
1. Finds all methods with governance annotations
2. Validates each has a `@Worker` or pattern annotation on the same method
3. Extracts `GovernanceDescriptor` from annotation values
4. Generates CDI bean configurations for each governance type

- [ ] **Step 4: Wire into BlocksAnnotationsProcessor**

Add governance processing to the main `@BuildStep` method.

- [ ] **Step 5: Run tests to verify they pass**

- [ ] **Step 6: Commit**

```
feat(#116): governance annotation processing — @OversightGate, @TrustRouted, @CbrRouted, @Attestation
```

### Task 7: Enhanced @Customize processing

**Files:**
- Modify: `annotations/deployment/src/main/java/io/casehub/blocks/annotations/deployment/BlocksAnnotationsProcessor.java`
- Modify: `annotations/runtime/src/main/java/io/casehub/blocks/annotations/runtime/BlocksAnnotationsRecorder.java`
- Test: `annotations/deployment/src/test/java/io/casehub/blocks/annotations/deployment/CustomizeTest.java`

**Interfaces:**
- Consumes: `@Customize` methods targeting pattern builder types (DebateBuilder, SupervisorBuilder, etc.)
- Produces: Enhanced recorder invocation with CDI parameter resolution

- [ ] **Step 1: Write @Customize test**

```java
package io.casehub.blocks.annotations.deployment;

import io.casehub.blocks.annotations.*;
import io.casehub.blocks.agentic.pattern.DebateBuilder;
import io.casehub.engine.annotations.Customize;
import io.casehub.platform.agent.AgentProvider;
import org.jboss.jandex.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.io.*;

class CustomizeTest {

    private Index indexClasses(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> clazz : classes) {
            String resourceName = "/" + clazz.getName().replace('.', '/') + ".class";
            try (InputStream stream = clazz.getResourceAsStream(resourceName)) {
                indexer.index(stream);
            }
        }
        return indexer.complete();
    }

    // Fixture: valid @Customize with CDI parameter
    interface CustomizedDebate {
        @Debate(maxRounds = 5)
        String review(
            @Debater(role = "critic", systemPrompt = "Challenge") io.casehub.blocks.agentic.AgentRef critic,
            @Judge(systemPrompt = "Judge") io.casehub.blocks.agentic.AgentRef judge,
            String document);

        @Customize
        static void customize(DebateBuilder<?> builder, AgentProvider agentProvider) {
            // CDI-resolved AgentProvider injected as second parameter
        }
    }

    @Test
    void detects_customize_method_with_pattern_builder() throws IOException {
        Index index = indexClasses(CustomizedDebate.class, Customize.class,
            Debate.class, Debater.class, Judge.class);
        // BlocksAnnotationsProcessor should detect the @Customize method,
        // match DebateBuilder to @Debate, and validate AgentProvider is CDI-resolvable
        var processor = new BlocksAnnotationsProcessor();
        // Validation should not throw — AgentProvider is CDI-managed
        assertThatCode(() -> processor.validateCustomizeMethods(index))
            .doesNotThrowAnyException();
    }

    // Fixture: @Customize with non-CDI parameter — should fail
    interface BadCustomize {
        @Debate(maxRounds = 5)
        String review(
            @Debater(role = "a", systemPrompt = "p") io.casehub.blocks.agentic.AgentRef a,
            String doc);

        @Customize
        static void customize(DebateBuilder<?> builder, String notCdiBean) {
            // String is not a CDI bean
        }
    }

    @Test
    void rejects_customize_with_non_cdi_parameter() throws IOException {
        Index index = indexClasses(BadCustomize.class, Customize.class,
            Debate.class, Debater.class);

        assertThatThrownBy(() -> new BlocksAnnotationsProcessor().validateCustomizeMethods(index))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not a CDI bean");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement enhanced @Customize scanning**

Detect `@Customize` methods where the first parameter is a pattern builder type. For additional parameters, verify CDI resolvability via Jandex. Generate recorder code that resolves CDI parameters via `Arc.container().select()`.

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Commit**

```
feat(#116): enhanced @Customize — CDI parameter resolution for pattern builders
```

---

## Batch 4: Examples

### Task 8: Incident response example (flagship)

**Files:**
- Create: `annotations/examples/incident-response-blocks/pom.xml`
- Create: `annotations/examples/incident-response-blocks/src/main/java/io/casehub/blocks/annotations/examples/incident/` — pattern interfaces with `@Supervisor` + `@Debate` + governance
- Test: `annotations/examples/incident-response-blocks/src/test/java/` — integration tests

**Interfaces:**
- Consumes: Engine#945 incident-response base case
- Produces: Working example demonstrating the full annotation model

- [ ] **Step 1: Create example pom with dependencies on blocks-annotations + engine-annotations**

- [ ] **Step 2: Create IncidentTriage interface**

`@Supervisor` + `@OversightGate` + `@TrustRouted` + `@Attestation` — the flagship governed supervisor.

- [ ] **Step 3: Create ContainmentDebate interface**

`@Debate` with `@Debater` and `@Judge` — containment strategy debate.

- [ ] **Step 4: Write integration test verifying ExecutionModel beans are produced**

- [ ] **Step 5: Commit**

```
feat(#116): incident-response-blocks example — governed supervisor + debate
```

### Task 9: Aircraft maintenance + wildfire response examples

**Files:**
- Create: `annotations/examples/aircraft-maintenance-blocks/` — `@Debate` (repair strategy) + `@OversightGate` + `@CbrRouted`
- Create: `annotations/examples/wildfire-response-blocks/` — `@Voting` (consensus) + `@Htn` (multi-phase) + `@OversightGate`
- Tests for both

- [ ] **Step 1: Create aircraft-maintenance-blocks example**

`@Debate` + `@OversightGate` (sign-off) + `@CbrRouted` — repair strategy debate with CBR evidence routing.

- [ ] **Step 2: Create wildfire-response-blocks example**

`@Voting` + `@Htn` + `@OversightGate` — multi-agency consensus with HTN decomposition.

- [ ] **Step 3: Write integration tests for both**

- [ ] **Step 4: Commit**

```
feat(#116): aircraft-maintenance + wildfire-response examples
```

---

## References

- [2026-08-22-blocks-annotations-design.md] — design spec this plan implements
- [decisions.md] — 8 architectural decisions (D1–D8)
- [ADR-0004] — own orchestration annotations, dual-track LC4j strategy
- [io/casehub/blocks/agentic/pattern/Patterns.java] — 8 pattern builders
- [io/casehub/blocks/agentic/model/ExecutionModel.java] — 5-SPI composition record
- [io/casehub/blocks/agentic/pattern/AbstractPatternBuilder.java] — base builder
- [io/casehub/blocks/agentic/pattern/DebateBuilder.java] — debate builder (maxRounds, judge)
- [io/casehub/blocks/agentic/pattern/SupervisorBuilder.java] — supervisor builder (defaults: FirstMatchRouting, PassThrough)
- [GE-20260819-e4a624] — Jandex Indexer API for build extension testing
- [GE-20260817-48caeb] — setRuntimeInit() for RUNTIME_INIT recorders
- [GE-20260818-d7915b] — SyntheticBeanBuildItem.supplier() non-recordable objects
- [GE-20260614-efee3b] — SyntheticBeanBuildItem without addInjectionPoint
- [GitHub #116] — blocks-annotations module issue
- [GitHub #150] — blocks-langchain4j integration (Track 2, separate)
- [tracker.md] — spec review findings (27 issues, 23 verified)
