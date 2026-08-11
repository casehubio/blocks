package io.casehub.blocks.summarisation.examples.logistics;

public record PackageAnomaly(AnomalyType type, String severity, String details, String rationale) {}
