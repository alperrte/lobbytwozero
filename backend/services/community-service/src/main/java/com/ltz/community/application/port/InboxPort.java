package com.ltz.community.application.port;

import java.util.UUID;

/** Gelen olayların tekilleştirilmesi (inbox). Aktif transaction içinde, olayın işlenmesiyle birlikte çağrılmalıdır. */
public interface InboxPort {

	/**
	 * Olayı işlenmiş olarak kaydeder.
	 *
	 * @return olay ilk kez görülüyorsa true; daha önce işlenmişse false (işlem tekrarlanmamalıdır)
	 */
	boolean markProcessed(UUID eventId, String eventType, String producer);
}
