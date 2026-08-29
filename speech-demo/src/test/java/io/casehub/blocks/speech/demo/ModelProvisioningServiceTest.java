package io.casehub.blocks.speech.demo;

import io.casehub.blocks.speech.demo.ModelProvisioningService.ModelStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ModelProvisioningServiceTest {

    @Test
    void init_setsAllModelsToPending() {
        var service = new ModelProvisioningService() {
            @Override
            void provision(String modelName) {}
        };

        service.init();

        int expectedSize = ModelProvisioningService.MODELS.size()
                           + ModelProvisioningService.KOKORO_MODELS.size()
                           + ModelProvisioningService.AUDIO8_MODELS.size()
                           + 1; // streaming-stt
        assertThat(service.status()).hasSize(expectedSize);
        assertThat(service.status().values()).allMatch(s -> s == ModelStatus.PENDING);
        assertThat(service.allReady()).isFalse();}

    @Test
    void provisionAll_setsAllModelsToReady() {
        var provisioned = new ArrayList<String>();
        var service = new ModelProvisioningService() {
            @Override
            void provision(String modelName)       {provisioned.add(modelName);}

            @Override
            void provisionKokoro(String modelName) {}

            @Override
            void provisionAudio8(String variant)   {}

            @Override
            void provisionStreamingStt()           {}
        };

        service.init();
        service.provisionAll();

        assertThat(service.allReady()).isTrue();
        assertThat(service.status().values()).allMatch(s -> s == ModelStatus.READY);
        assertThat(provisioned).containsExactlyInAnyOrderElementsOf(
                ModelProvisioningService.MODELS.values());
    }

    @Test
    void provisionAll_setsErrorOnFailure() {
        var service = new ModelProvisioningService() {
            @Override
            void provision(String modelName) {
                if (modelName.contains("amy")) {throw new RuntimeException("download failed");}
            }

            @Override
            void provisionKokoro(String modelName) {}

            @Override
            void provisionAudio8(String variant)   {}

            @Override
            void provisionStreamingStt()           {}
        };

        service.init();
        service.provisionAll();

        assertThat(service.allReady()).isFalse();
        assertThat(service.status().get("amy")).isEqualTo(ModelStatus.ERROR);

        int expectedReady = ModelProvisioningService.MODELS.size()
                            + ModelProvisioningService.KOKORO_MODELS.size()
                            + ModelProvisioningService.AUDIO8_MODELS.size()
                            + 1  // streaming-stt
                            - 1; // amy failed
        var readyCount = service.status().values().stream()
                                .filter(s -> s == ModelStatus.READY).count();
        assertThat(readyCount).isEqualTo(expectedReady);
    }

    @Test
    void statusReturnsDefensiveCopy() {
        var service = new ModelProvisioningService() {
            @Override
            void provision(String modelName)       {}

            @Override
            void provisionKokoro(String modelName) {}

            @Override
            void provisionAudio8(String variant)   {}

            @Override
            void provisionStreamingStt()           {}
        };

        service.init();
        var status1 = service.status();
        service.provisionAll();
        var status2 = service.status();

        assertThat(status1.values()).allMatch(s -> s == ModelStatus.PENDING);
        assertThat(status2.values()).allMatch(s -> s == ModelStatus.READY);
    }
}
