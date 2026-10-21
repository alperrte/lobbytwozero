package com.ltz.auth.infrastructure.security;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.ltz.auth.domain.exception.PasswordHashingCapacityExceededException;

/** Bellek yoğun hash işlemlerini sabit sayıda eşzamanlı çağrıyla sınırlar. */
public class BoundedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;
    private final Semaphore permits;
    private final Duration acquireTimeout;

    public BoundedPasswordEncoder(PasswordEncoder delegate, int maxConcurrentHashes, Duration acquireTimeout) {
        this.delegate = delegate;
        this.permits = new Semaphore(maxConcurrentHashes, true);
        this.acquireTimeout = acquireTimeout;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return withPermit(() -> delegate.encode(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return withPermit(() -> delegate.matches(rawPassword, encodedPassword));
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }

    private <T> T withPermit(Supplier<T> operation) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(acquireTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PasswordHashingCapacityExceededException();
        }
        if (!acquired) {
            throw new PasswordHashingCapacityExceededException();
        }
        try {
            return operation.get();
        } finally {
            permits.release();
        }
    }
}
