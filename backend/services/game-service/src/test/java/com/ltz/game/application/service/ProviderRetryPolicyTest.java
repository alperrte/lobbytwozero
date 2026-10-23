package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProviderRetryPolicyTest {
    private final ProviderRetryPolicy policy = new ProviderRetryPolicy();
    @Test void retryBudgetAndBackoffAreBounded() {
        var error = new ProviderException("PROVIDER_TRANSPORT_ERROR");
        assertEquals(Duration.ofSeconds(5), policy.delay(error, 1));
        assertEquals(Duration.ofSeconds(80), policy.delay(error, 5));
        assertNull(policy.delay(error, 6)); assertNull(policy.delay(error, 0));
        assertNull(policy.delay(new ProviderException("PROVIDER_ACCESS_DENIED"), 1));
    }
    @Test void rateLimitsHonorLongerProviderDelayAndDoNotRetryEarly() {
        assertEquals(Duration.ofSeconds(60), policy.delay(new ProviderException("RATE_LIMITED"), 1));
        assertEquals(Duration.ofHours(4), policy.delay(new ProviderException("RATE_LIMITED",Duration.ofHours(4)), 1));
        assertNull(policy.delay(new ProviderException("RATE_LIMITED",Duration.ofDays(2)), 1));
        assertEquals("PROVIDER_ERROR",new ProviderException("unsafe secret in message").code());
    }
    @Test void invalidAndEmptyDetailPayloadsAreRejectedBeforePersistence() {
        Set<String> empty = Set.of();
        Set<String> blankNames = Set.of(" ");
        String oversized = "x".repeat(16001);
        assertThrows(ProviderException.class, () -> new GameProvider.Details(null,null,empty,empty,null));
        assertThrows(ProviderException.class, () -> new GameProvider.Details(oversized,null,empty,empty,null));
        assertThrows(ProviderException.class, () -> new GameProvider.Details("ok",null,null,empty,null));
        var names = new HashSet<String>(); names.add(null);
        assertThrows(ProviderException.class, () -> new GameProvider.Details("ok",null,names,empty,null));
        assertThrows(ProviderException.class, () -> new GameProvider.Details("ok",null,blankNames,empty,null));
        GameProvider provider = new GameProvider() {
            @Override public String provider() { return "TEST"; }
            @Override public Set<Capability> capabilities() { return Set.of(); }
            @Override public CatalogPage catalog(CatalogQuery query) { throw new UnsupportedOperationException(); }
        };
        assertEquals("UNSUPPORTED_CAPABILITY", assertThrows(ProviderException.class, () -> provider.details("10")).code());
    }
}
