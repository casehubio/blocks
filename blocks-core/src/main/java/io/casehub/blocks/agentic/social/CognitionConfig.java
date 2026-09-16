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
        boolean directivePrompts
) {
    public static CognitionConfig all() {
        return new CognitionConfig(true, true, true, true, true, true, true, true, true, false);
    }

    public static CognitionConfig none() {
        return new CognitionConfig(false, false, false, false, false, false, false, false, false, false);
    }

    public CognitionConfig withDirectives() {
        return new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled,
                userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled,
                memoryHygieneEnabled, innerLifeEnabled, true);
    }

    public CognitionConfig with(String subsystem, boolean enabled) {
        return switch (subsystem) {
            case "mood" -> new CognitionConfig(enabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts);
            case "drives" -> new CognitionConfig(moodEnabled, enabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts);
            case "mentalModel" -> new CognitionConfig(moodEnabled, drivesEnabled, enabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts);
            case "userModel" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, enabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts);
            case "strategy" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, enabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts);
            case "narrative" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, enabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts);
            case "goals" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, enabled, memoryHygieneEnabled, innerLifeEnabled, directivePrompts);
            case "memoryHygiene" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, enabled, innerLifeEnabled, directivePrompts);
            case "innerLife" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, enabled, directivePrompts);
            case "directivePrompts" -> new CognitionConfig(moodEnabled, drivesEnabled, mentalModelEnabled, userModelEnabled, strategyEnabled, narrativeEnabled, goalsEnabled, memoryHygieneEnabled, innerLifeEnabled, enabled);
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
