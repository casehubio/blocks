package io.casehub.blocks.speech.demo;

import io.casehub.blocks.speech.demo.ModelProvisioningService.ModelStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ModelProvisioningServiceTest {

    @Test
    void init_setsAllModelsToPending() {
        var service = new ModelProvisioningService() {
            @Override void provision(String modelName) {}
        };

        service.init();

        assertThat(service.status()).hasSize(ModelProvisioningService.MODELS.size());
        assertThat(service.status().values()).allMatch(s -> s == ModelStatus.PENDING);
        assertThat(service.allReady()).isFalse();
    }

    @Test
    void provisionAll_setsAllModelsToReady() {
        var provisioned = new ArrayList<String>();
        var service = new ModelProvisioningService() {
            @Override void provision(String modelName) { provisioned.add(modelName); }
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
            @Override void provision(String modelName) {
                if (modelName.contains("amy")) throw new RuntimeException("download failed");
            }
        };

        service.init();
        service.provisionAll();

        assertThat(service.allReady()).isFalse();
        assertThat(service.status().get("amy")).isEqualTo(ModelStatus.ERROR);

        var readyCount = service.status().values().stream()
                .filter(s -> s == ModelStatus.READY).count();
        assertThat(readyCount).isEqualTo(ModelProvisioningService.MODELS.size() - 1);
    }

    @Test
    void statusReturnsDefensiveCopy() {
        var service = new ModelProvisioningService() {
            @Override void provision(String modelName) {}
        };

        service.init();
        var status1 = service.status();
        service.provisionAll();
        var status2 = service.status();

        assertThat(status1.values()).allMatch(s -> s == ModelStatus.PENDING);
        assertThat(status2.values()).allMatch(s -> s == ModelStatus.READY);
    }
}
