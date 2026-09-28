package io.casehub.blocks.agentic.social;

public record CognitionConfig(
        boolean moodEnabled,
        boolean drivesEnabled,
        boolean mentalModelEnabled,
        boolean userModelEnabled,
        boolean strategyEnabled,
        boolean narrativeEnabled,
        boolean goalsEnabled,
        boolean memoryHygieneEnabled,
        boolean innerLifeEnabled,
        boolean directivePrompts,
        boolean characterDrivesEnabled,
        boolean needsPyramidEnabled,
        boolean attentionEnabled,
        boolean temporalFocusEnabled,
        boolean reflectionEnabled
) {
    public static CognitionConfig all() {return new CognitionConfig(true, true, true, true, true, true, true, true, true, false, true, true, true, true, true);}

    public static CognitionConfig none() {return new CognitionConfig(false, false, false, false, false, false, false, false, false, false, false, false, false, false, false);}

    public CognitionConfig withDirectives() {
        return new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled,
                                   userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled,
                                   memoryHygieneEnabled, innerLifeEnabled, true, characterDrivesEnabled,
                                   needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
    }

    public CognitionConfig with(String subsystem, boolean enabled) {
        return switch (subsystem) {
            case "mood" -> new CognitionConfig(enabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "drives" -> new CognitionConfig(moodEnabled, enabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "mentalModel" -> new CognitionConfig(moodEnabled, drivesEnabled, enabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "userModel" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, enabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "strategy" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, enabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "narrative" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, enabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "goals" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, enabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "memoryHygiene" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, enabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "innerLife" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, enabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "directivePrompts" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, enabled, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "characterDrives" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, enabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "needsPyramid" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, enabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled);
            case "attention" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, enabled, temporalFocusEnabled, reflectionEnabled);
            case "temporalFocus" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, enabled, reflectionEnabled);
            case "reflection" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, enabled);
            default -> throw new IllegalArgumentException("Unknown subsystem: " + subsystem);
        };
    }

    public CognitionConfig without(String... subsystems) {
        var config = this;
        for (var s : subsystems) {
            config = config.with(s, false);
        }
        return config;
    }
}
