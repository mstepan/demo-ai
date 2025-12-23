package com.github.mstepan.demo_ai.oci;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds properties under 'oci.genai' from application.yaml.
 *
 * Example:
 * oci:
 *   genai:
 *     region: us-chicago-1
 *     profile: bmc_operator_access
 *     compartment: ocid1.compartment....
 *     model: meta.llama-4-maverick-17b-128e-instruct-fp8
 */
@ConfigurationProperties(prefix = "oci.genai")
public record OCIGenAiProperties(
        String region,
        String profile,
        String compartment,
        String model,
        Integer connectionTimeout,
        Integer readTimeout
) {}
