package com.github.mstepan.demo_ai;

import com.github.mstepan.demo_ai.oci.OCIGenAiProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableConfigurationProperties(OCIGenAiProperties.class)
@EnableRetry
public class DemoAiApplication {

    static void main(String[] args) {
        SpringApplication.run(DemoAiApplication.class, args);
    }
}
