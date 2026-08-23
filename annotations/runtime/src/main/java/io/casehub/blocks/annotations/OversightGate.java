package io.casehub.blocks.annotations;

import io.casehub.api.spi.ActionRiskClassifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OversightGate {

    Class<? extends ActionRiskClassifier> value();

    boolean reversible() default true;

    String[] candidateGroups() default {};
}
