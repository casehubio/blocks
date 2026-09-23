package io.casehub.blocks.social.jpa.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import io.casehub.blocks.social.jpa.SocialJsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

@Converter
public class StringStringMapConverter implements AttributeConverter<Map<String, String>, String> {

    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        return attribute == null ? "{}" : SocialJsonMapper.toJson(attribute);
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? Map.of() : SocialJsonMapper.fromJson(dbData, TYPE);
    }
}
