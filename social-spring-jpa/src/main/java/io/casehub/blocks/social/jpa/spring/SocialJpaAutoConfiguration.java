package io.casehub.blocks.social.jpa.spring;

import io.casehub.blocks.agentic.social.MentalModelStore;
import io.casehub.blocks.agentic.social.StrategyStore;
import io.casehub.blocks.agentic.social.UserProfileStore;
import io.casehub.blocks.agentic.social.narrative.NarrativeStore;
import io.casehub.blocks.social.jpa.UserProfileEntity;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringUserProfileStore.class)
@EntityScan(basePackageClasses = UserProfileEntity.class)
@EnableJpaRepositories(basePackageClasses = UserProfileEntityRepository.class)
public class SocialJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(UserProfileStore.class)
    public SpringUserProfileStore springUserProfileStore(UserProfileEntityRepository repo) {
        return new SpringUserProfileStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(MentalModelStore.class)
    public SpringMentalModelStore springMentalModelStore(MentalModelEntityRepository repo) {
        return new SpringMentalModelStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(NarrativeStore.class)
    public SpringNarrativeStore springNarrativeStore(NarrativeEntityRepository repo) {
        return new SpringNarrativeStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(StrategyStore.class)
    public SpringStrategyStore springStrategyStore(
            StrategyProfileEntityRepository profileRepo,
            EngagementEvidenceEntityRepository evidenceRepo) {
        return new SpringStrategyStore(profileRepo, evidenceRepo);
    }
}
