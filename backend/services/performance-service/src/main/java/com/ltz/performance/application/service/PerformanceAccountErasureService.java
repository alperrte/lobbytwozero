package com.ltz.performance.application.service;

import com.ltz.performance.application.port.AccountErasure;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerformanceAccountErasureService {
    private final AccountErasure erasure;

    public PerformanceAccountErasureService(AccountErasure erasure) { this.erasure = erasure; }

    /** Auth'un beş saniyelik HTTP zaman aşımının altında kalan sınırlı işlem. */
    @Transactional(timeout = 4)
    public boolean erase(UUID accountId) { return erasure.erase(accountId); }
}
