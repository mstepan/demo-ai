package com.github.mstepan.demo_ai.oci;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.io.IOException;

@Configuration
public class GenAiClientFactoryFactory {

    private static final int DEFAULT_CONNECTION_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 60_000;

    private final OCIGenAiProperties properties;

    public GenAiClientFactoryFactory(OCIGenAiProperties properties) {
        this.properties = properties;
    }

    /**
     * Prototype-scoped bean: each getObject() (via ObjectProvider/Provider) returns a new client.
     * Caller is responsible for closing the client after use.
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public GenerativeAiInferenceClient newClient() {
        try {
            var authProvider = new SessionTokenAuthenticationDetailsProvider(properties.profile());

            int connectionTimeoutMs =
                    (properties.connectionTimeout() != null)
                            ? (int) properties.connectionTimeout().toMillis()
                            : DEFAULT_CONNECTION_TIMEOUT_MS;

            int readTimeoutMs =
                    (properties.readTimeout() != null)
                            ? (int) properties.readTimeout().toMillis()
                            : DEFAULT_READ_TIMEOUT_MS;

            var clientConfig =
                    ClientConfiguration.builder()
                            .connectionTimeoutMillis(connectionTimeoutMs)
                            .readTimeoutMillis(readTimeoutMs)
                            .build();

            var client =
                    GenerativeAiInferenceClient.builder()
                            .configuration(clientConfig)
                            .build(authProvider);

            // Configure region or endpoint as needed
            // client.setRegion(Region.US_CHICAGO_1);
            client.setEndpoint(properties.baseUrl());

            return client;
        } catch (IOException ioEx) {
            throw new IllegalStateException(ioEx);
        }
    }
}
