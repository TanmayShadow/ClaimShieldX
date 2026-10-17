package com.example.ClaimShieldX.extraction.config;

import com.example.ClaimShieldX.extraction.validate.ExtractionProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExtractionProperties.class)
public class ExtractionConfig {
    @Bean
    ExtractionProcessor extractionProcessor(ExtractionProperties properties) {
        return new ExtractionProcessor(properties);
    }
}
