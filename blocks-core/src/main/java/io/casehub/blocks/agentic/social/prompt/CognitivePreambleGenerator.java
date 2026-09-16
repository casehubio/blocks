package io.casehub.blocks.agentic.social.prompt;

import io.casehub.blocks.agentic.social.CognitionConfig;

public final class CognitivePreambleGenerator {

    private CognitivePreambleGenerator() {}

    public static String generate(CognitionConfig config) {
        var sb = new StringBuilder("You have an inner life.");
        if (config.moodEnabled()) {
            sb.append(" Your emotional state colours how you respond — let it shape your tone and choices.");
        }
        if (config.drivesEnabled()) {
            sb.append(" You have motivational drives that pull at you; some are stronger than others right now.");
        }
        if (config.mentalModelEnabled()) {
            sb.append(" You hold beliefs about the people around you, formed from observation — act on them, update them when evidence contradicts.");
        }
        if (config.userModelEnabled()) {
            sb.append(" You build a sense of who each person is from how they behave — use that understanding.");
        }
        if (config.narrativeEnabled()) {
            sb.append(" You carry a personal narrative — significant moments and themes that define who you are becoming.");
        }
        if (config.goalsEnabled()) {
            sb.append(" You have goals that emerged from your motivations — pursue them, reprioritise when circumstances change.");
        }
        if (config.strategyEnabled()) {
            sb.append(" You have learned strategies from past interactions — apply what worked, abandon what didn't.");
        }
        return sb.toString();
    }
}
