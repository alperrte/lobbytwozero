package com.ltz.community.application.port;

import java.time.Instant;
import java.util.UUID;

/** Bir hesabın community_db'deki kişisel verisini siler/anonimleştirir (Auth'un hesap silme sözleşmesi, D4). */
public interface AccountErasurePort {

	/**
	 * Anonimleştirilen içeriğin yazar referansı olarak yazılan sabit kimlik ("silinmiş hesap"). Hiçbir gerçek hesapla
	 * eşleşmez; içeriğin sahibini belirlemeye yaramaz.
	 */
	UUID DELETED_ACCOUNT_ID = new UUID(0L, 0L);

	/** Anonimleştirilen gönderi/yorum metninin yerine yazılan sabit metin (gerçek içerik saklanmaz). */
	String REMOVED_CONTENT = "[removed]";

	/**
	 * Hesabın tüm kişisel verisini tek işlemde siler ya da anonimleştirir. İdempotenttir: veri yoksa ya da zaten
	 * işlenmişse hiçbir şey yapmaz. Aktif bir transaction içinde çağrılmalıdır.
	 */
	void erase(UUID accountId, Instant now);
}
