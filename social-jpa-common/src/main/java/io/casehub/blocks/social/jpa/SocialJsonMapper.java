package io.casehub.blocks.social.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.GroupEpisode;
import io.casehub.blocks.agentic.social.narrative.IndividualEpisode;
import io.casehub.blocks.agentic.social.narrative.NarrativeFragment;

public final class SocialJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .addMixIn(NarrativeFragment.class, NarrativeFragmentMixin.class)
            .addMixIn(IndividualEpisode.class, NarrativeFragmentMixin.class)
            .addMixIn(GroupEpisode.class, NarrativeFragmentMixin.class)
            .addMixIn(DerivedTheme.class, NarrativeFragmentMixin.class)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private SocialJsonMapper() {}

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    public static <T> String toJson(T value, TypeReference<T> type) {
        try {
            return MAPPER.writerFor(type).writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }


    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON deserialization failed", e);
        }
    }
}
