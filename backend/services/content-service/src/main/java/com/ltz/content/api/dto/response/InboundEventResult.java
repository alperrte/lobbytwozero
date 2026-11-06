package com.ltz.content.api.dto.response;

import java.util.UUID;

/**
 * Olayın işlenme sonucu. PROCESSED: olay işlendi (içerik değiştiyse etki uygulandı; içerik yok ya da durum zaten uygunsa
 * etkisiz); DUPLICATE: daha önce işlenmişti, hiçbir şey yapılmadı; IGNORED: bu servisin ilgilenmediği olay türü (üretici
 * yeniden denemesin diye başarı döner).
 */
public record InboundEventResult(UUID eventId, Outcome outcome) {

	public enum Outcome {
		PROCESSED,
		DUPLICATE,
		IGNORED
	}
}
