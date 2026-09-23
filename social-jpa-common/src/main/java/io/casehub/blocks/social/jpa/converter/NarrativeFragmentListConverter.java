package io.casehub.blocks.social.jpa.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import io.casehub.blocks.agentic.social.narrative.NarrativeFragment;
import io.casehub.blocks.social.jpa.SocialJsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class NarrativeFragmentListConverter implements AttributeConverter<List<NarrativeFragment>, String> {

    private static final TypeReference<List<NarrativeFragment>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<NarrativeFragment> attribute) {
        return attribute == null ? "[]" : SocialJsonMapper.toJson(attribute, TYPE);
    }

    @Override
    public List<NarrativeFragment> convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? List.of() : SocialJsonMapper.fromJson(dbData, TYPE);
    }
}
