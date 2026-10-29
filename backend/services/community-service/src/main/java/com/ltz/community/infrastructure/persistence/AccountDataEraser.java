package com.ltz.community.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.application.port.AccountErasurePort;

/**
 * {@link AccountErasurePort}'un SQL uygulaması. Tablo tablo karar (kişisel veri = hesabı tanımlayan her şey):
 * <ul>
 *   <li><b>Gönderiler ve yorumlar</b> (yazarı bu hesap): metin sabit bir yer tutucuyla değiştirilir, yazar referansı
 *       "silinmiş hesap" kimliğine çevrilir, durum DELETED olur. Satırlar silinmez; çünkü başkalarının yorumları,
 *       yanıtları ve repostları bu satırlara FK ile bağlıdır ve başkasının verisi silinmez. Soft delete'te saklanan eski
 *       metinler de bu yolla temizlenir. Gönderi görselleri silinir.</li>
 *   <li><b>Beğeniler</b>: hesabın beğenileri silinir ve ilgili gönderilerin sayacı düşürülür. Silinen yorumlar sayaçtan
 *       çıkarılır.</li>
 *   <li><b>Raporlar</b>: hesabın yaptığı raporlar silinir; hesabın içeriği hakkındaki raporlarda yalnız içerik yazarı
 *       referansı anonimleşir (moderasyon kaydı kalır, kişi tanımlanmaz).</li>
 *   <li><b>Engellemeler</b> (social-service yansıması): hesabın taraf olduğu tüm satırlar silinir.</li>
 *   <li><b>Forumlar</b>: forumu oluşturan referansı anonimleşir (forum içeriği kişisel değildir).</li>
 *   <li><b>Outbox</b>: hesabın kimliğini taşıyan tüm olaylar (bekleyen, başarısız ve teslim edilmiş) silinir.</li>
 * </ul>
 * Şema değişikliği gerekmez (community migration aralığı dolu): yer tutucu metin mevcut CHECK kısıtlarını sağlar.
 */
@Component
public class AccountDataEraser implements AccountErasurePort {

	/** SQL adlandırılmış parametre adları (:account, :anonymous). */
	private static final String ACCOUNT = "account";
	private static final String ANONYMOUS = "anonymous";

	private final JdbcClient jdbc;

	public AccountDataEraser(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void erase(UUID accountId, Instant now) {
		OffsetDateTime at = now.atOffset(ZoneOffset.UTC);
		eraseLikes(accountId);
		eraseComments(accountId, at);
		erasePosts(accountId, at);
		eraseReports(accountId, at);
		eraseBlocks(accountId);
		anonymizeForums(accountId, at);
		eraseOutboxEvents(accountId);
	}

	private void eraseLikes(UUID accountId) {
		jdbc.sql("""
				update posts set like_count = like_count - 1
				where like_count > 0 and id in (select post_id from post_likes where user_id = :account)
				""").param(ACCOUNT, accountId).update();
		jdbc.sql("delete from post_likes where user_id = :account").param(ACCOUNT, accountId).update();
	}

	private void eraseComments(UUID accountId, OffsetDateTime at) {
		// Yalnız sayılan (VISIBLE) yorumlar gönderinin comment_count değerinden düşer.
		jdbc.sql("""
				update posts p set comment_count = greatest(p.comment_count - c.total, 0)
				from (select post_id, count(*) as total from comments
				      where author_id = :account and status = 'VISIBLE' group by post_id) c
				where p.id = c.post_id
				""").param(ACCOUNT, accountId).update();
		jdbc.sql("""
				update comments set author_id = :anonymous, body = :removed, status = 'DELETED',
				    deleted_at = coalesce(deleted_at, :at), updated_at = :at, version = version + 1
				where author_id = :account
				""")
				.param(ANONYMOUS, DELETED_ACCOUNT_ID).param("removed", REMOVED_CONTENT)
				.param("at", at).param(ACCOUNT, accountId).update();
	}

	private void erasePosts(UUID accountId, OffsetDateTime at) {
		jdbc.sql("delete from post_images where post_id in (select id from posts where author_id = :account)")
				.param(ACCOUNT, accountId).update();
		// Repost'un metni isteğe bağlıdır (null olabilir); diğer gönderilerde CHECK en az 1 karakter ister.
		jdbc.sql("""
				update posts set author_id = :anonymous,
				    body = case when repost_of_id is null then :removed else null end,
				    status = 'DELETED', deleted_at = coalesce(deleted_at, :at), updated_at = :at, version = version + 1
				where author_id = :account
				""")
				.param(ANONYMOUS, DELETED_ACCOUNT_ID).param("removed", REMOVED_CONTENT)
				.param("at", at).param(ACCOUNT, accountId).update();
	}

	private void eraseReports(UUID accountId, OffsetDateTime at) {
		jdbc.sql("delete from community_reports where reporter_id = :account").param(ACCOUNT, accountId).update();
		jdbc.sql("""
				update community_reports set target_author_id = :anonymous, updated_at = :at, version = version + 1
				where target_author_id = :account
				""").param(ANONYMOUS, DELETED_ACCOUNT_ID).param("at", at).param(ACCOUNT, accountId).update();
	}

	private void eraseBlocks(UUID accountId) {
		jdbc.sql("delete from user_blocks where blocker_id = :account or blocked_id = :account")
				.param(ACCOUNT, accountId).update();
	}

	private void anonymizeForums(UUID accountId, OffsetDateTime at) {
		jdbc.sql("""
				update forums set created_by = :anonymous, updated_at = :at, version = version + 1
				where created_by = :account
				""").param(ANONYMOUS, DELETED_ACCOUNT_ID).param("at", at).param(ACCOUNT, accountId).update();
	}

	/** Olay yükleri kimlikleri metin olarak taşır (UUID); hesabın kimliğini içeren her olay silinir. */
	private void eraseOutboxEvents(UUID accountId) {
		jdbc.sql("delete from outbox_events where position(:account in cast(payload as text)) > 0")
				.param(ACCOUNT, accountId.toString()).update();
	}
}
