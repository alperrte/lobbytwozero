package com.ltz.community.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.application.port.AccountErasurePort;

/**
 * Auth'un hesap silme çağrısını karşılar (docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md). Hesabın
 * kişisel verisi TEK işlemde silinir ya da anonimleştirilir; yarım kalan silme olmaz. Başka kullanıcıların verisi
 * (örn. silinen hesabın gönderisi altındaki başkalarının yorumları) silinmez. İşlem set tabanlıdır ve idempotenttir.
 * Günlüğe yalnız hesap kimliği yazılır; içerik, token ve başka kişisel veri yazılmaz.
 */
@Service
public class AccountErasureService {

	private static final Logger log = LoggerFactory.getLogger(AccountErasureService.class);

	private final AccountErasurePort eraser;
	private final Clock clock;

	public AccountErasureService(AccountErasurePort eraser, Clock clock) {
		this.eraser = eraser;
		this.clock = clock;
	}

	@Transactional
	public void erase(UUID accountId) {
		eraser.erase(accountId, Instant.now(clock));
		log.info("Community data erased for account {}", accountId);
	}
}
