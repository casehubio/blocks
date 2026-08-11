package io.casehub.blocks.summarisation.examples.clinical;

public record ClinicalEvent(ClinicalCategory category, Severity severity, String description, String rationale) {}
