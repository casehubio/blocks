package io.casehub.blocks.annotations;

import io.casehub.blocks.attestation.LifecycleAttestationObserver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Attestation {

    Class<? extends LifecycleAttestationObserver> observer();

    String capabilityTag() default "";
}
