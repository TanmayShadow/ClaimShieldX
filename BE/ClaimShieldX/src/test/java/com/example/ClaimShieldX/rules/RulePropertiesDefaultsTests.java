package com.example.ClaimShieldX.rules;

import com.example.ClaimShieldX.rules.config.RuleProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RulePropertiesDefaultsTests extends RuleApiTestBase {
    @Autowired RuleProperties properties;

    @Test
    void theApplicationPropertiesCarryTheDocumentedDefaults() {
        assertThat(properties.snapshot()).isEqualTo(RuleProperties.defaults().snapshot());
        assertThat(properties.timeZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(properties.variancePercentThreshold()).isEqualByComparingTo("10.00");
        assertThat(properties.varianceMinimumIncrease()).isEqualByComparingTo("1000.00");
        assertThat(properties.roundOffAllowance()).isEqualByComparingTo("1.00");
    }
}
