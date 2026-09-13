package io.casehub.blocks.agentic.yaml.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.runtime.yaml.EidosDescriptorModule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DescriptorLoader {

    @SuppressWarnings("unchecked")
    public static List<AgentDescriptor> load(String scenario) {
        String path = "/examples/" + scenario + "/descriptors.yaml";
        try (InputStream is = DescriptorLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Descriptors not found: " + path);
            }
            var plainMapper = new ObjectMapper(new YAMLFactory());
            List<Map<String, Object>> rawList = plainMapper.readValue(is, List.class);
            for (var desc : rawList) {
                if (desc.containsKey("name") && !desc.containsKey("agentId")) {
                    desc.put("agentId", desc.get("name"));
                }
            }
            var wrapped = new LinkedHashMap<String, Object>();
            wrapped.put("descriptors", rawList);
            var wrappedYaml = plainMapper.writeValueAsBytes(wrapped);
            var registrar = new io.casehub.eidos.runtime.registrar.ClasspathYamlDescriptorRegistrar();
            return registrar.loadFrom(new ByteArrayInputStream(wrappedYaml), null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + path, e);
        }
    }
}
