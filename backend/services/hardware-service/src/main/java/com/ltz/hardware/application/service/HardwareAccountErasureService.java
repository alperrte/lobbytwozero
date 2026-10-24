package com.ltz.hardware.application.service;

import com.ltz.hardware.application.port.HardwareAccountErasure;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareAccountErasureService {
    private final HardwareAccountErasure erasure;
    public HardwareAccountErasureService(HardwareAccountErasure erasure) { this.erasure = erasure; }

    @Transactional(timeout = 4)
    public boolean erase(UUID accountId) { return erasure.erase(accountId); }
}
