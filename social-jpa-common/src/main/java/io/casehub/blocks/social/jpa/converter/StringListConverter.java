package io.casehub.blocks.social.jpa.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import io.casehub.blocks.social.jpa.SocialJsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        return attribute == null ? "[]" : SocialJsonMapper.toJson(attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? List.of() : SocialJsonMapper.fromJson(dbData, TYPE);
    }
}
