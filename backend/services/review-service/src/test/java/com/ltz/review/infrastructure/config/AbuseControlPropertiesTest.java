package com.ltz.review.infrastructure.config;

import com.ltz.review.domain.enums.AbuseAction;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbuseControlPropertiesTest {
    @Test void absentRulesFallBackToApprovedDefaults() {
        var properties = new AbuseControlProperties(null, null);
        assertEquals(new AbuseControlProperties.Rule(5, Duration.ofSeconds(60)), properties.rule(AbuseAction.REVIEW_CREATE));
        assertEquals(new AbuseControlProperties.Rule(60, Duration.ofSeconds(60)), properties.rule(AbuseAction.REVIEW_LIKE));
    }

    @Test void invalidLimitsAndWindowsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new AbuseControlProperties.Rule(0, Duration.ofSeconds(60)));
        assertThrows(IllegalArgumentException.class, () -> new AbuseControlProperties.Rule(100_001, Duration.ofSeconds(60)));
        assertThrows(IllegalArgumentException.class, () -> new AbuseControlProperties.Rule(5, null));
        assertThrows(IllegalArgumentException.class, () -> new AbuseControlProperties.Rule(5, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new AbuseControlProperties.Rule(5, Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> new AbuseControlProperties.Rule(5, Duration.ofMillis(1500)));
        assertThrows(IllegalArgumentException.class, () -> new AbuseControlProperties.Rule(5, Duration.ofDays(1).plusSeconds(1)));
    }
}
