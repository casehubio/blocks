package io.casehub.blocks.social.jpa.spring;

import io.casehub.blocks.agentic.social.MentalModelStore;
import io.casehub.blocks.agentic.social.StrategyStore;
import io.casehub.blocks.agentic.social.UserProfileStore;
import io.casehub.blocks.agentic.social.narrative.NarrativeStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SocialJpaAutoConfigurationTest {

    @SpringBootApplication
    static class TestApp {}

    @Autowired
    ApplicationContext context;

    @Test
    void beansAreRegistered() {
        assertThat(context.getBean(UserProfileStore.class))
                .isInstanceOf(SpringUserProfileStore.class);
        assertThat(context.getBean(MentalModelStore.class))
                .isInstanceOf(SpringMentalModelStore.class);
        assertThat(context.getBean(NarrativeStore.class))
                .isInstanceOf(SpringNarrativeStore.class);
        assertThat(context.getBean(StrategyStore.class))
                .isInstanceOf(SpringStrategyStore.class);
    }
}
