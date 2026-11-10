package com.ltz.mail;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.ltz.mail.application.port.MailDeliveryException;
import com.ltz.mail.application.port.RecipientResolverPort;

/**
 * Testlerde Auth'un yerine geçen alıcı çözücü: hesap başına adres/durum programlanır, çağrılar sayılır, bir sonraki
 * çağrı için geçici ya da kalıcı hata verdirilebilir. Programlanmamış hesap "hesap yok" (kalıcı) döner.
 */
public class StubRecipientResolver implements RecipientResolverPort {

    private final Map<UUID, AccountContact> contacts = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile MailDeliveryException nextFailure;
    private volatile boolean configured = true;

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public AccountContact resolve(UUID accountId) {
        calls.incrementAndGet();
        MailDeliveryException failure = nextFailure;
        if (failure != null) {
            nextFailure = null;
            throw failure;
        }
        AccountContact contact = contacts.get(accountId);
        if (contact == null) {
            throw MailDeliveryException.permanent("RECIPIENT_NOT_FOUND", null);
        }
        return contact;
    }

    public void givenAccount(UUID accountId, String email, boolean verified, String status) {
        contacts.put(accountId, new AccountContact(email, verified, status));
    }

    public void failNext(MailDeliveryException failure) {
        this.nextFailure = failure;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        contacts.clear();
        calls.set(0);
        nextFailure = null;
        configured = true;
    }
}
