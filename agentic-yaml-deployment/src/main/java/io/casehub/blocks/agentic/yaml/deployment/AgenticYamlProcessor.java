package io.casehub.blocks.agentic.yaml.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.casehub.blocks.agentic.yaml.spec.world.WorldDefinition;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class AgenticYamlProcessor {

    private static final String AGENTIC_YAML_PATH = "META-INF/agentic/";
    private static final ObjectMapper YAML = YamlMappers.create()
            .registerModule(new JavaTimeModule());

    @BuildStep
    List<HotDeploymentWatchedFileBuildItem> watchYamlFiles() {
        return List.of(new HotDeploymentWatchedFileBuildItem(AGENTIC_YAML_PATH));
    }

    @BuildStep
    AgenticPatternsBuildItem discoverAndValidatePatterns() throws IOException {
        var patterns = new ArrayList<PatternSpec>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = cl.getResources(AGENTIC_YAML_PATH);

        while (resources.hasMoreElements()) {
            URL dir = resources.nextElement();
            if ("file".equals(dir.getProtocol())) {
                try {
                    var path = Path.of(dir.toURI());
                    if (Files.isDirectory(path)) {
                        try (var files = Files.list(path)) {
                            files.filter(f -> {
                                        var name = f.toString();
                                        return name.endsWith(".yaml") || name.endsWith(".yml");
                                    })
                                    .forEach(f -> {
                                        try (InputStream in = Files.newInputStream(f)) {
                                            var spec = YAML.readValue(in, PatternSpec.class);
                                            patterns.add(spec);
                                        } catch (IOException e) {
                                            throw new RuntimeException(
                                                    "Failed to parse agentic YAML: " + f, e);
                                        }
                                    });
                        }
                    }
                } catch (URISyntaxException e) {
                    throw new IOException("Invalid URI for resource: " + dir, e);
                }
            }
        }

        return new AgenticPatternsBuildItem(patterns);
    }

    @BuildStep
    WorldConfigBuildItem discoverWorldConfig() throws IOException {
        ClassLoader cl       = Thread.currentThread().getContextClassLoader();
        URL         resource = cl.getResource("META-INF/world.yaml");
        if (resource == null) {return new WorldConfigBuildItem(null);}
        try (InputStream in = resource.openStream()) {
            var definition = YAML.readValue(in, WorldDefinition.class);
            return new WorldConfigBuildItem(definition);
        }
    }

    @BuildStep
    PromptOptimisationBuildItem discoverPromptOptimisationConfig() throws IOException {
        ClassLoader cl       = Thread.currentThread().getContextClassLoader();
        URL         resource = cl.getResource("META-INF/prompt-optimisation.yaml");
        if (resource == null) {
            resource = cl.getResource("META-INF/prompt-optimisation.yml");
        }
        if (resource == null) {return new PromptOptimisationBuildItem(null);}
        try (InputStream in = resource.openStream()) {
            var definition = YAML.readValue(in,
                                            io.casehub.blocks.agentic.yaml.spec.PromptOptimisationDefinition.class);
            return new PromptOptimisationBuildItem(definition);
        }
    }


    @BuildStep
    CognitionConfigBuildItem discoverCognitionConfig() throws IOException {
        ClassLoader cl       = Thread.currentThread().getContextClassLoader();
        URL         resource = cl.getResource("META-INF/cognition.yaml");
        if (resource == null) {return new CognitionConfigBuildItem(null);}
        try (InputStream in = resource.openStream()) {
            var definition = YAML.readValue(in, io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition.class);
            return new CognitionConfigBuildItem(definition);
        }
    }

}
