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
        boolean reflectionEnabled,
        boolean consolidationEnabled
) {
    public static CognitionConfig all()  {return new CognitionConfig(true, true, true, true, true, true, true, true, true, false, true, true, true, true, true, true);}

    public static CognitionConfig none() {return new CognitionConfig(false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false);}

    public CognitionConfig withDirectives() {
        return new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled,
                                   userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled,
                                   memoryHygieneEnabled, innerLifeEnabled, true, characterDrivesEnabled,
                                   needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
    }

    public CognitionConfig with(String subsystem, boolean enabled) {
        return switch (subsystem) {
            case "mood" -> new CognitionConfig(enabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "drives" -> new CognitionConfig(moodEnabled, enabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "mentalModel" -> new CognitionConfig(moodEnabled, drivesEnabled, enabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "userModel" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, enabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "strategy" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, enabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "narrative" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, enabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "goals" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, enabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "memoryHygiene" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, enabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "innerLife" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, enabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "directivePrompts" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, enabled, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "characterDrives" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, enabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "needsPyramid" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, enabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "attention" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, enabled, temporalFocusEnabled, reflectionEnabled, consolidationEnabled);
            case "temporalFocus" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, enabled, reflectionEnabled, consolidationEnabled);
            case "reflection" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, enabled, consolidationEnabled);
            case "consolidation" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts, characterDrivesEnabled, needsPyramidEnabled, attentionEnabled, temporalFocusEnabled, reflectionEnabled, enabled);
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
