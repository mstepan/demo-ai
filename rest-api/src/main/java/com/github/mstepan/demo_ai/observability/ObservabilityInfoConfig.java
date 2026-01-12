package com.github.mstepan.demo_ai.observability;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures the /actuator/info endpoint contains stable application metadata for local/dev use.
 * Avoids leaking sensitive data and keeps cardinality bounded.
 */
@Configuration
public class ObservabilityInfoConfig {

    @Bean
    public InfoContributor appInfoContributor(@Value("${spring.application.name:demo-ai}") String appName) {
        return builder -> builder.withDetail("app", Map.of("name", appName));
    }
}
