package io.casehub.blocks.speech;

import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

public interface AvatarCognition {

    SpeechPromptAssembler wrapAssembler(SpeechPromptAssembler base, String agentId, String tenantId,
                                        Supplier<String> subjectIdSupplier);

    default void initialize(String agentId, String tenantId) {}

    default void tick(String agentId, String tenantId, Set<String> activeSubjects) {}

    default @Nullable String evaluateProactive(String agentId, String tenantId,
                                                String channelContext) {
        return null;
    }

    default void recordInteraction(String agentId, String tenantId,
                                    @Nullable String subjectId,
                                    String userMessage, String response) {}
}
