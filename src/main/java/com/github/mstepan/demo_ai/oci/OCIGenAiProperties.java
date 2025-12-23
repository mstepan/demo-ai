package com.github.mstepan.demo_ai.oci;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds properties under 'oci.genai' from application.yaml.
 *
 * <p>Example: oci: genai: region: us-chicago-1 profile: bmc_operator_access compartment:
 * ocid1.compartment.... model: meta.llama-4-maverick-17b-128e-instruct-fp8
 */
@Component
@ConfigurationProperties(prefix = "oci.genai")
public class OCIGenAiProperties {

    private String region;
    private String profile;
    private String compartment;
    private String model;
    private Integer connectionTimeout;
    private Integer readTimeout;

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getCompartment() {
        return compartment;
    }

    public void setCompartment(String compartment) {
        this.compartment = compartment;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Integer getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
    }
}
