package com.example.ClaimShieldX.investigation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.random.RandomGenerator;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(InvestigationProperties.class)
public class InvestigationConfig {
    /** All queue timing reads this clock, so tests can move time instead of waiting. */
    @Bean
    public Clock investigationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RandomGenerator investigationRandom() {
        return new java.security.SecureRandom();
    }
}
