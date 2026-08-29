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

    public enum ModelStatus {PENDING, DOWNLOADING, READY, ERROR}

    static final Map<String, String> MODELS = new LinkedHashMap<>(Map.of(
            "lessac-medium", "vits-piper-en_US-lessac-medium",
            "lessac-high", "vits-piper-en_US-lessac-high",
            "amy", "vits-piper-en_US-amy-medium",
            "ryan", "vits-piper-en_US-ryan-high",
            "jenny", "vits-piper-en_GB-jenny_dioco-medium"));

    static final Map<String, String> KOKORO_MODELS = new LinkedHashMap<>(Map.of(
            "kokoro", "kokoro-en-v0_19"));

    static final Map<String, String> AUDIO8_MODELS = new LinkedHashMap<>(Map.of(
            "audio8", "0.1b",
            "audio8:0.6b", "0.6b"));

    private static final System.Logger LOG = System.getLogger("speech-demo.provisioning");

    private final ConcurrentHashMap<String, ModelStatus> statuses = new ConcurrentHashMap<>();

    void onStartup(@Observes StartupEvent ev) {
        init();
        Thread.ofVirtual().name("model-provisioner").start(this::provisionAll);
    }

    void init() {
        MODELS.keySet().forEach(key -> statuses.put(key, ModelStatus.PENDING));
        KOKORO_MODELS.keySet().forEach(key -> statuses.put(key, ModelStatus.PENDING));
        AUDIO8_MODELS.keySet().forEach(key -> statuses.put(key, ModelStatus.PENDING));
        statuses.put("streaming-stt", ModelStatus.PENDING);}

    void provisionAll() {
        for (var entry : MODELS.entrySet()) {
            provisionEntry(entry.getKey(), () -> provision(entry.getValue()));
        }
        for (var entry : KOKORO_MODELS.entrySet()) {
            provisionEntry(entry.getKey(), () -> provisionKokoro(entry.getValue()));
        }
        for (var entry : AUDIO8_MODELS.entrySet()) {
            provisionEntry(entry.getKey(), () -> provisionAudio8(entry.getValue()));
        }
        provisionEntry("streaming-stt", this::provisionStreamingStt);}

    void provision(String modelName) {
        VitsTextToSpeech.ensureProvisioned(modelName);
    }

    private void provisionEntry(String key, Runnable provisioner) {
        statuses.put(key, ModelStatus.DOWNLOADING);
        try {
            LOG.log(System.Logger.Level.INFO, "Provisioning model: {0}", key);
            provisioner.run();
            statuses.put(key, ModelStatus.READY);
            LOG.log(System.Logger.Level.INFO, "Model ready: {0}", key);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to provision " + key + ": " + e.getMessage());
            statuses.put(key, ModelStatus.ERROR);
        }
    }

    void provisionKokoro(String modelName) {
        io.casehub.blocks.speech.sherpa.KokoroTextToSpeech.ensureProvisioned();
    }

    void provisionAudio8(String variant) {
        io.casehub.blocks.speech.sherpa.Audio8TextToSpeech.ensureProvisioned(variant);
    }

    void provisionStreamingStt() {
        io.casehub.blocks.speech.sherpa.SherpaOnnxStreamingSpeechToText.ensureProvisioned();
    }


    public Map<String, ModelStatus> status() {
        return Map.copyOf(statuses);
    }

    public boolean allReady() {
        return !statuses.isEmpty() && statuses.values().stream().allMatch(s -> s == ModelStatus.READY);
    }
}
