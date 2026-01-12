package com.github.mstepan.demo_ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RestCorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/ask/**")
                .allowedOrigins("http://localhost:7170")
                .allowedMethods("POST")
                .allowedHeaders("*");
    }
}
