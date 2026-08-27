package io.casehub.blocks.speech.demo;

import io.casehub.blocks.speech.sherpa.VitsTextToSpeech;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ModelProvisioningService {

    public enum ModelStatus { PENDING, DOWNLOADING, READY, ERROR }

    static final Map<String, String> MODELS = new LinkedHashMap<>(Map.of(
            "lessac-medium", "vits-piper-en_US-lessac-medium",
            "lessac-high", "vits-piper-en_US-lessac-high",
            "amy", "vits-piper-en_US-amy-medium",
            "ryan", "vits-piper-en_US-ryan-high",
            "jenny", "vits-piper-en_GB-jenny_dioco-medium"));

    private static final System.Logger LOG = System.getLogger("speech-demo.provisioning");

    private final ConcurrentHashMap<String, ModelStatus> statuses = new ConcurrentHashMap<>();

    void onStartup(@Observes StartupEvent ev) {
        init();
        Thread.ofVirtual().name("model-provisioner").start(this::provisionAll);
    }

    void init() {
        MODELS.keySet().forEach(key -> statuses.put(key, ModelStatus.PENDING));
    }

    void provisionAll() {
        for (var entry : MODELS.entrySet()) {
            statuses.put(entry.getKey(), ModelStatus.DOWNLOADING);
            try {
                LOG.log(System.Logger.Level.INFO, "Provisioning model: {0}", entry.getValue());
                provision(entry.getValue());
                statuses.put(entry.getKey(), ModelStatus.READY);
                LOG.log(System.Logger.Level.INFO, "Model ready: {0}", entry.getKey());
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to provision " + entry.getKey() + ": " + e.getMessage());
                statuses.put(entry.getKey(), ModelStatus.ERROR);
            }
        }
    }

    void provision(String modelName) {
        VitsTextToSpeech.ensureProvisioned(modelName);
    }


    public Map<String, ModelStatus> status() {
        return Map.copyOf(statuses);
    }

    public boolean allReady() {
        return !statuses.isEmpty() && statuses.values().stream().allMatch(s -> s == ModelStatus.READY);
    }
}
