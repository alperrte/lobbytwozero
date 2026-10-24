package com.ltz.community.infrastructure.messaging.outbox;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code outbox_events} okuma/durum işlemleri. Teslim sırasında bekleyen olayları birden fazla relay örneğinin çifte
 * göndermemesi için olaylar {@code for update skip locked} ile "kiralanır": seçilen satırların {@code next_attempt_at}
 * değeri kira süresi kadar ileri alınır, kira bitmeden başka örnek bu satırları görmez. Teslim dış çağrı olduğundan
 * kiralama kısa bir transaction'da yapılır, çağrı transaction dışında yürür.
 */
@Component
public class OutboxStore {

	/** Kiralanan olay; {@code payloadJson} yük JSON metnidir. */
	public record OutboxRecord(UUID id, String aggregateType, UUID aggregateId, String eventType, int eventVersion,
			String payloadJson, Instant occurredAt, int attempts) {
	}

	private final JdbcClient jdbc;

	public OutboxStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<OutboxRecord> claimDue(Instant now, int batchSize, Instant leaseUntil) {
		return jdbc.sql("""
				update outbox_events set next_attempt_at = :leaseUntil
				where id in (
				    select id from outbox_events
				    where status = 'PENDING' and next_attempt_at <= :now
				    order by occurred_at, id
				    limit :batchSize
				    for update skip locked)
				returning id, aggregate_type, aggregate_id, event_type, event_version, cast(payload as text) as payload,
				          occurred_at, attempts
				""")
				.param("leaseUntil", leaseUntil.atOffset(ZoneOffset.UTC))
				.param("now", now.atOffset(ZoneOffset.UTC))
				.param("batchSize", batchSize)
				.query((rs, rowNum) -> new OutboxRecord(
						rs.getObject("id", UUID.class),
						rs.getString("aggregate_type"),
						rs.getObject("aggregate_id", UUID.class),
						rs.getString("event_type"),
						rs.getInt("event_version"),
						rs.getString("payload"),
						rs.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant(),
						rs.getInt("attempts")))
				.list().stream()
				// RETURNING sırası garanti değildir; teslim sırası olay zamanına göredir.
				.sorted(Comparator.comparing(OutboxRecord::occurredAt).thenComparing(OutboxRecord::id))
				.toList();
	}

	public void markSent(UUID id, Instant now) {
		jdbc.sql("update outbox_events set status = 'SENT', sent_at = :now, next_attempt_at = null where id = :id")
				.param("now", now.atOffset(ZoneOffset.UTC))
				.param("id", id)
				.update();
	}

	/** Başarısız denemeyi kaydeder: {@code nextAttemptAt} null ise olay kalıcı olarak FAILED olur. */
	public void markFailedAttempt(UUID id, int attempts, Instant nextAttemptAt) {
		jdbc.sql("""
				update outbox_events set attempts = :attempts,
				    status = case when cast(:next as timestamptz) is null then 'FAILED' else 'PENDING' end,
				    next_attempt_at = cast(:next as timestamptz)
				where id = :id
				""")
				.param("attempts", attempts)
				.param("next", nextAttemptAt == null ? null : nextAttemptAt.atOffset(ZoneOffset.UTC))
				.param("id", id)
				.update();
	}

	/** Teslim edilmiş eski olayları siler (saklama süresi); silinen satır sayısını döndürür. */
	public int purgeSentBefore(Instant threshold) {
		return jdbc.sql("delete from outbox_events where status = 'SENT' and sent_at < :threshold")
				.param("threshold", threshold.atOffset(ZoneOffset.UTC))
				.update();
	}
}
