package io.casehub.blocks.speech.ws;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.avatar")
public interface AvatarConfig {

    @WithDefault("16000")
    int sampleRate();

    @WithDefault("2")
    int maxDestructiveness();

    Optional<String> systemPrompt();

    Optional<String> agentId();

    Optional<String> tenantId();

    @WithDefault("30")
    int proactiveTickIntervalSeconds();
}
