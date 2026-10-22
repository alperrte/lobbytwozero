package com.ltz.auth;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ltz.auth.application.port.AccountDataEraserPort;
import com.ltz.auth.application.port.AccountErasureException;

/** Yalnız test kapsamında: hedef servislerin silmeyi onayladığı (veya servis bazında reddettiği) durumu temsil eder. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeAccountDataEraserConfiguration {

    @Bean
    @Primary
    FakeEraser fakeEraser() {
        return new FakeEraser();
    }

    public static class FakeEraser implements AccountDataEraserPort {

        private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        private final Set<String> failing = ConcurrentHashMap.newKeySet();

        @Override
        public void erase(String service, UUID requestId, UUID accountId) {
            calls.computeIfAbsent(service + ":" + accountId, key -> new AtomicInteger()).incrementAndGet();
            if (failing.contains(service)) {
                throw new AccountErasureException("TEST_SERVICE_DOWN");
            }
        }

        public int calls(String service, UUID accountId) {
            AtomicInteger counter = calls.get(service + ":" + accountId);
            return counter == null ? 0 : counter.get();
        }

        public void failFor(String service) {
            failing.add(service);
        }

        public void recover(String service) {
            failing.remove(service);
        }
    }
}
