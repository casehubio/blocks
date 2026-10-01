package io.casehub.blocks.agentic.yaml.spec.cognition;

import io.casehub.neocortex.cognition.relationship.StageTier;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record RelationshipStageConfigSpec(
        @Nullable List<StageTier> tiers,
        @Nullable Double decayRate,
        @Nullable Double positiveWeight,
        @Nullable Double negativeWeight) {}
