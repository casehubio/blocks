package io.casehub.blocks.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrustRouted {

    double threshold() default 0.7;

    int minimumObservations() default 10;

    double borderlineMargin() default 0.1;

    double blendFactor() default 0.6;

    double cbrWeight() default 0.0;
}
