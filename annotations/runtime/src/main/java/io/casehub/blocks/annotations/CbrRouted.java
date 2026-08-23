package io.casehub.blocks.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CbrRouted {

    double successWeight() default 1.0;

    double gateExpiredWeight() default 0.5;

    double gateRejectedWeight() default 0.25;

    double failureWeight() default 0.0;
}
