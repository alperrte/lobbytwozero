package com.ltz.community.application.port;

import java.util.Map;
import java.util.UUID;

/**
 * Başka servislere iletilecek olayları yayına hazırlar. Olay, çağıranın iş verisiyle AYNI transaction'da yazılır
 * (outbox deseni); gerçek teslim ayrı bir relay ile yapılır. Aktif transaction yoksa uygulama hatası fırlatılır.
 */
public interface DomainEventPublisher {

	/**
	 * @param aggregateType olayın ilgili olduğu kök varlık türü (ör. {@code POST})
	 * @param aggregateId   kök varlığın kimliği
	 * @param eventType     olay adı (docs/architecture.md §6.3 kataloğundan)
	 * @param payload       en az gerekli alanlar; kişisel veri minimumda tutulur, serbest metin içerik taşınmaz
	 */
	void publish(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload);
}
