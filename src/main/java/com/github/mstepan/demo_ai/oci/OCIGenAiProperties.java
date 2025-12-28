package com.github.mstepan.demo_ai.oci;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Binds properties under 'oci.genai' from 'application.yaml'. */
@ConfigurationProperties(prefix = "oci.genai")
@Validated
public record OCIGenAiProperties(
        @NotBlank
                @Pattern(
                        regexp = "https?://.+",
                        message = "base.url must start with 'http://' or 'https://'")
                @Name("base.url")
                String baseUrl,
        @NotBlank String profile,
        @NotBlank
                @Pattern(
                        regexp = COMPARTMENT_OCID_REGEX,
                        message = "compartment must be a valid OCI compartment OCID")
                String
                        compartment, // e.g.
                                     // ocid1.compartment.oc1..aaaaaaaadwjibfornz4simrjcqftsoxvnyn5syxqklv76e5rjmbucvkbvuwa
        @NotBlank String model,
        @DurationMin(seconds = 5) @DurationMax(seconds = 60) @DefaultValue("10s")
                Duration connectionTimeout,
        @DurationMin(seconds = 5) @DurationMax(seconds = 120) @DefaultValue("60s")
                Duration readTimeout) {
    // Accepts both 'ocid1.compartment.oc1..xxxxx' (no region) and
    // 'ocid1.compartment.oc1.<region>.xxxxx'
    public static final String COMPARTMENT_OCID_REGEX =
            "^ocid1\\.compartment\\.oc\\d+\\.(?:[a-z0-9-]+\\.|\\.)[A-Za-z0-9]+$";
}
