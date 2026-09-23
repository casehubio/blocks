package io.casehub.blocks.social.jpa.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import io.casehub.blocks.social.jpa.SocialJsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

@Converter
public class StringDoubleMapConverter implements AttributeConverter<Map<String, Double>, String> {

    private static final TypeReference<Map<String, Double>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Double> attribute) {
        return attribute == null ? "{}" : SocialJsonMapper.toJson(attribute);
    }

    @Override
    public Map<String, Double> convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? Map.of() : SocialJsonMapper.fromJson(dbData, TYPE);
    }
}
