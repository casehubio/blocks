package io.casehub.blocks.speech.demo;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/models")
public class ModelStatusEndpoint {

    @Inject
    ModelProvisioningService provisioningService;

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> status() {
        return provisioningService.status().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name()));
    }
}
