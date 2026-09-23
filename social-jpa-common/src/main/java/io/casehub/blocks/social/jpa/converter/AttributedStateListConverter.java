package io.casehub.blocks.social.jpa.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import io.casehub.blocks.agentic.social.AttributedState;
import io.casehub.blocks.social.jpa.SocialJsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class AttributedStateListConverter implements AttributeConverter<List<AttributedState>, String> {

    private static final TypeReference<List<AttributedState>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<AttributedState> attribute) {
        return attribute == null ? "[]" : SocialJsonMapper.toJson(attribute);
    }

    @Override
    public List<AttributedState> convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? List.of() : SocialJsonMapper.fromJson(dbData, TYPE);
    }
}
