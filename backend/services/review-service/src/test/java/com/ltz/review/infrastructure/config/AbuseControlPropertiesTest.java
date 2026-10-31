package com.ltz.review.infrastructure.config;

import com.ltz.review.domain.enums.AbuseAction;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbuseControlPropertiesTest {
    static final Duration MINUTE = Duration.ofSeconds(60);

    @Test void absentRulesFallBackToApprovedDefaults() {
        var properties = new AbuseControlProperties(null, null);
        assertEquals(new AbuseControlProperties.Rule(5, MINUTE), properties.rule(AbuseAction.REVIEW_CREATE));
        assertEquals(new AbuseControlProperties.Rule(60, MINUTE), properties.rule(AbuseAction.REVIEW_LIKE));
    }

    @Test void invalidLimitsAndWindowsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new AbuseControlProperties.Rule(0, MINUTE));
        assertThrows(IllegalArgumentException.class, () -> new AbuseControlProperties.Rule(100_001, MINUTE));
        assertThrows(IllegalArgumentException.class, () -> new AbuseControlProperties.Rule(5, null));
        for (Duration window : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMillis(1500), Duration.ofDays(1).plusSeconds(1)})
            assertThrows(IllegalArgumentException.class, () -> new AbuseControlProperties.Rule(5, window));
    }
}
