package io.casehub.blocks.annotations;

import io.casehub.blocks.agentic.aggregation.AggregationStrategy;
import io.casehub.blocks.agentic.aggregation.MajorityVote;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Voting {

    String name() default "";

    Class<? extends AggregationStrategy> strategy() default MajorityVote.class;
}
