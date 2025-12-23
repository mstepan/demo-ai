package com.github.mstepan.demo_ai.oci;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

/** Binds properties under 'oci.genai' from application.yaml. */
@ConfigurationProperties(prefix = "oci.genai")
@Validated
public record OCIGenAiProperties(
        String region,
        @Name("base.url") String baseUrl,
        @NotBlank String profile,
        @NotBlank String compartment,
        @NotBlank String model,
        Integer connectionTimeout,
        Integer readTimeout) {}
