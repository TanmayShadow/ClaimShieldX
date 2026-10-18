package com.example.ClaimShieldX.analysis;

import com.example.ClaimShieldX.analysis.findings.ScoreProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScoreProperties.class)
public class AnalysisConfig {
}
