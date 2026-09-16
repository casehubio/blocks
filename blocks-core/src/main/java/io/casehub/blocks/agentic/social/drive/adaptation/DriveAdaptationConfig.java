package io.casehub.blocks.agentic.social.drive.adaptation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.drive-adaptation")
public interface DriveAdaptationConfig {
    @WithDefault("0.05")
    double learningRate();

    @WithDefault("0.3")
    double arousalWeight();

    @WithDefault("0.1")
    double minIntensity();

    @WithDefault("1.0")
    double maxIntensity();

    @WithDefault("20")
    int maxPerPass();
}
