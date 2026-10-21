package com.ltz.auth.application.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.auth.api.dto.response.PendingDeletionTaskResponse;
import com.ltz.auth.domain.enums.DeletionTaskStatus;
import com.ltz.auth.infrastructure.config.DeletionProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountDeletionTaskRepository;

import lombok.RequiredArgsConstructor;

/**
 * Takılı silme görevlerini görünür kılar: eksik/ulaşılamayan servis sessizce başarı sayılmaz, burada listelenir.
 * Salt okunurdur; görev durumunu değiştirmez.
 */
@Service
@RequiredArgsConstructor
public class PendingDeletionQueryService {

    private final AccountDeletionTaskRepository taskRepository;
    private final DeletionProperties properties;

    @Transactional(readOnly = true)
    public List<PendingDeletionTaskResponse> pending(int limit) {
        return taskRepository.findByStatusOrdered(DeletionTaskStatus.PENDING, PageRequest.of(0, limit)).stream()
                .map(task -> new PendingDeletionTaskResponse(task.getRequestId(), task.getAccountId(),
                        task.getService(), task.getAttempts(), task.getLastError(), task.getNextAttemptAt(),
                        task.getCreatedAt(), task.getAttempts() >= properties.stuckAttempts()))
                .toList();
    }
}
