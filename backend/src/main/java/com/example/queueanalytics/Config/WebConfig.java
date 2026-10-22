package com.example.queueanalytics.Config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final AnalyticsProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(properties.getCorsOrigins().split(",")).map(String::trim).filter(origin -> !origin.isBlank()).toArray(String[]::new);
        if (origins.length > 0) {
            registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET");
        }
    }
}
