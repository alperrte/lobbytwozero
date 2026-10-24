package com.ltz.library.application.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountErasureService {
    /** Henüz kişisel veri tablosu yoktur. Tablo eklenen görevde silme işlemi de eklenmelidir. */
    @Transactional(timeout = 4)
    public void erase(UUID accountId) {
        java.util.Objects.requireNonNull(accountId);
    }
}
