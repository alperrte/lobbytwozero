package com.ltz.event.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.event.infrastructure.persistence.repository.EventParticipationRepository;

/**
 * Hesap silme (KD-3). Tek transaction'da sırayla: (1) düzenleyicisi bu hesap olan açık etkinliklerin katılımcıları
 * etkinlik tarafından iptal edilir, (2) o etkinlikler iptal edilir, (3) hesabın tüm etkinliklerinde düzenleyici bağlantısı
 * kaldırılır, (4) hesabın kendi katılımlarının tuttuğu kontenjan geri verilir, (5) hesabın katılım kayıtları silinir.
 * Tekrar çağrı zararsızdır (idempotent).
 */
@Service
public class AccountErasureService {

	private final EventParticipationRepository participationRepository;
	private final Clock clock;

	public AccountErasureService(EventParticipationRepository participationRepository, Clock clock) {
		this.participationRepository = participationRepository;
		this.clock = clock;
	}

	@Transactional
	public void erase(UUID accountId) {
		Instant now = Instant.now(clock);
		participationRepository.cancelParticipationsOfOrganizerEvents(accountId, now);
		participationRepository.cancelOpenEventsOfOrganizer(accountId, now);
		participationRepository.detachOrganizer(accountId, now);
		participationRepository.releaseSeatsOfUser(accountId, now);
		participationRepository.deleteByUser(accountId);
	}
}
