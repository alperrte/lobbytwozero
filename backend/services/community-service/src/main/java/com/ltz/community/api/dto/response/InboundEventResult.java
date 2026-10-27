package com.ltz.community.api.dto.response;

import java.util.UUID;

/**
 * Olayın işlenme sonucu. PROCESSED: etki uygulandı; DUPLICATE: daha önce işlenmişti, hiçbir şey yapılmadı;
 * IGNORED: bu servisin ilgilenmediği olay türü (üretici yeniden denemesin diye başarı döner).
 */
public record InboundEventResult(UUID eventId, Outcome outcome) {

	public enum Outcome {
		PROCESSED,
		DUPLICATE,
		IGNORED
	}
}
