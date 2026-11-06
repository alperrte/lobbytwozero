package com.ltz.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Savepoint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentImage;
import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.domain.entity.ContentTranslationId;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.infrastructure.persistence.repository.ContentImageMeta;
import com.ltz.content.infrastructure.persistence.repository.ContentImageRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentTranslationRepository;

import jakarta.persistence.EntityManager;

/**
 * V101/V102 migration'larını gerçek PostgreSQL üzerinde çalıştırır ve Hibernate şema doğrulamasıyla (ddl-auto=validate)
 * varlık eşlemelerini sınar; her test geri alınır.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ContentRepositoryIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private ContentTranslationRepository translationRepository;

	@Autowired
	private ContentImageRepository imageRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static String hash(char fill) {
		return String.valueOf(fill).repeat(64);
	}

	private Content saveDraft(UUID author, ContentType type, String contentHash) {
		return contentRepository.saveAndFlush(Content.draft(author, type, "tr", contentHash, NOW));
	}

	/**
	 * İhlal PostgreSQL'de transaction'ı iptal eder; bu yüzden her deneme bir savepoint içinde yapılır ve geri alınır, böylece
	 * aynı testte art arda ihlal denenebilir.
	 */
	private void assertRejected(String constraint, String sql, Object... args) {
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			Savepoint savepoint = connection.setSavepoint();
			try {
				assertThatThrownBy(() -> jdbcTemplate.update(sql, args))
						.isInstanceOf(DataIntegrityViolationException.class)
						.hasMessageContaining(constraint);
			} finally {
				connection.rollback(savepoint);
			}
			return null;
		});
	}

	private ContentImage image(UUID contentId, int sortOrder) {
		return ContentImage.attach(contentId, new byte[] { 1, 2, 3, (byte) sortOrder }, "image/png", 10, 20, sortOrder, NOW);
	}

	// ------------------------------------------------------------------ eşleme

	@Test
	void storesAndLoadsAContentWithItsTranslation() {
		UUID author = UUID.randomUUID();
		Content saved = saveDraft(author, ContentType.NEWS, hash('a'));
		translationRepository.saveAndFlush(ContentTranslation.of(saved.getId(), "tr", "Başlık", "Özet", "Gövde"));
		entityManager.clear();

		Content loaded = contentRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();
		ContentTranslation translation = translationRepository
				.findById(new ContentTranslationId(saved.getId(), "tr")).orElseThrow();

		assertThat(loaded.getAuthorId()).isEqualTo(author);
		assertThat(loaded.getType()).isEqualTo(ContentType.NEWS);
		assertThat(loaded.getStatus()).isEqualTo(ContentStatus.DRAFT);
		assertThat(loaded.getDefaultLocale()).isEqualTo("tr");
		assertThat(loaded.getContentHash()).isEqualTo(hash('a'));
		assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
		assertThat(loaded.getVersion()).isNotNull();
		assertThat(translation.getTitle()).isEqualTo("Başlık");
		assertThat(translation.getSummary()).isEqualTo("Özet");
		assertThat(translation.getBody()).isEqualTo("Gövde");
	}

	@Test
	void summaryAndBodyMayBeNullInADraft() {
		Content saved = saveDraft(UUID.randomUUID(), ContentType.USER_CONTENT, hash('b'));
		translationRepository.saveAndFlush(ContentTranslation.of(saved.getId(), "tr", "Sadece başlık", null, null));
		entityManager.clear();

		ContentTranslation loaded = translationRepository.findById(new ContentTranslationId(saved.getId(), "tr")).orElseThrow();

		assertThat(loaded.getSummary()).isNull();
		assertThat(loaded.getBody()).isNull();
	}

	@Test
	void storesAndLoadsImageBytesWithMetadata() {
		UUID contentId = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('c')).getId();
		ContentImage saved = imageRepository.saveAndFlush(image(contentId, 2));
		entityManager.clear();

		ContentImage loaded = imageRepository.findByIdAndContentId(saved.getId(), contentId).orElseThrow();

		assertThat(loaded.getContent()).containsExactly(1, 2, 3, 2);
		assertThat(loaded.getMimeType()).isEqualTo("image/png");
		assertThat(loaded.getSizeBytes()).isEqualTo(4);
		assertThat(loaded.getWidth()).isEqualTo(10);
		assertThat(loaded.getHeight()).isEqualTo(20);
		assertThat(loaded.getSortOrder()).isEqualTo(2);
		assertThat(imageRepository.findByIdAndContentId(saved.getId(), UUID.randomUUID())).isEmpty();
	}

	@Test
	void listsImageMetadataInSortOrderWithoutLoadingBytes() {
		UUID contentId = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('d')).getId();
		imageRepository.save(image(contentId, 3));
		imageRepository.save(image(contentId, 0));
		imageRepository.saveAndFlush(image(contentId, 1));
		entityManager.clear();

		List<ContentImageMeta> metas = imageRepository.findByContentIdOrderBySortOrderAsc(contentId);

		assertThat(metas).extracting(ContentImageMeta::getSortOrder).containsExactly(0, 1, 3);
		assertThat(metas).allSatisfy(meta -> {
			assertThat(meta.getContentId()).isEqualTo(contentId);
			assertThat(meta.getSizeBytes()).isEqualTo(4);
		});
		assertThat(imageRepository.findSortOrdersByContentId(contentId)).containsExactlyInAnyOrder(0, 1, 3);
	}

	// ------------------------------------------------------------------ mükerrer içerik (CONT-18)

	@Test
	void theSameAuthorTypeAndTextCannotBeStoredTwice() {
		UUID author = UUID.randomUUID();
		saveDraft(author, ContentType.NEWS, hash('e'));

		String duplicateHash = hash('e');
		assertThatThrownBy(() -> saveDraft(author, ContentType.NEWS, duplicateHash))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uq_contents_author_id_type_content_hash");
	}

	@Test
	void theSameTextIsAllowedForAnotherTypeOrAnotherAuthor() {
		UUID author = UUID.randomUUID();
		saveDraft(author, ContentType.NEWS, hash('f'));

		saveDraft(author, ContentType.USER_CONTENT, hash('f'));
		saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('f'));

		assertThat(contentRepository.count()).isEqualTo(3);
	}

	@Test
	void aSoftDeletedContentNoLongerBlocksTheSameText() {
		UUID author = UUID.randomUUID();
		Content first = saveDraft(author, ContentType.NEWS, hash('1'));
		first.markDeleted(NOW);
		contentRepository.saveAndFlush(first);

		saveDraft(author, ContentType.NEWS, hash('1'));

		assertThat(contentRepository.count()).isEqualTo(2);
	}

	@Test
	void duplicateLookupsIgnoreDeletedRowsAndCanExcludeTheContentItself() {
		UUID author = UUID.randomUUID();
		Content active = saveDraft(author, ContentType.NEWS, hash('2'));
		Content deleted = saveDraft(author, ContentType.NEWS, hash('3'));
		deleted.markDeleted(NOW);
		contentRepository.saveAndFlush(deleted);

		assertThat(contentRepository.existsByAuthorIdAndTypeAndContentHashAndDeletedAtIsNull(author, ContentType.NEWS, hash('2'))).isTrue();
		assertThat(contentRepository.existsByAuthorIdAndTypeAndContentHashAndDeletedAtIsNull(author, ContentType.NEWS, hash('3'))).isFalse();
		assertThat(contentRepository.existsByAuthorIdAndTypeAndContentHashAndDeletedAtIsNullAndIdNot(author, ContentType.NEWS,
				hash('2'), active.getId())).isFalse();
		assertThat(contentRepository.existsByAuthorIdAndTypeAndContentHashAndDeletedAtIsNullAndIdNot(author, ContentType.NEWS,
				hash('2'), UUID.randomUUID())).isTrue();
		assertThat(contentRepository.existsByAuthorIdAndTypeAndContentHashAndDeletedAtIsNull(UUID.randomUUID(), ContentType.NEWS, hash('2'))).isFalse();
	}

	// ------------------------------------------------------------------ silme ve okuma

	@Test
	void softDeletedContentIsHiddenFromEveryLookup() {
		Content content = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('4'));
		content.markDeleted(NOW);
		contentRepository.saveAndFlush(content);
		entityManager.clear();

		assertThat(contentRepository.findByIdAndDeletedAtIsNull(content.getId())).isEmpty();
		assertThat(contentRepository.findActiveByIdForUpdate(content.getId())).isEmpty();
		// Satır fiziksel olarak silinmez (soft delete politikası).
		assertThat(contentRepository.findById(content.getId())).isPresent();
	}

	@Test
	void activeContentCanBeLockedForUpdate() {
		Content content = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('5'));

		assertThat(contentRepository.findActiveByIdForUpdate(content.getId())).isPresent();
	}

	@Test
	void theTextOfAContentCanBePurgedWithoutTouchingOthers() {
		UUID contentId = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('6')).getId();
		UUID otherId = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('7')).getId();
		translationRepository.save(ContentTranslation.of(contentId, "tr", "Silinecek", "özet", "gövde"));
		translationRepository.save(ContentTranslation.of(contentId, "en", "To delete", null, null));
		translationRepository.saveAndFlush(ContentTranslation.of(otherId, "tr", "Kalacak", null, null));

		int purged = translationRepository.deleteByContentId(contentId);

		assertThat(purged).isEqualTo(2);
		assertThat(translationRepository.findById(new ContentTranslationId(contentId, "tr"))).isEmpty();
		assertThat(translationRepository.findById(new ContentTranslationId(contentId, "en"))).isEmpty();
		assertThat(translationRepository.findById(new ContentTranslationId(otherId, "tr"))).isPresent();
		// contents satırının kendisi bu işlemle silinmez.
		assertThat(contentRepository.findById(contentId)).isPresent();
	}

	@Test
	void imagesOfAContentCanBePurgedInOneStatement() {
		UUID contentId = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('6')).getId();
		UUID otherId = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('7')).getId();
		imageRepository.save(image(contentId, 0));
		imageRepository.save(image(contentId, 1));
		imageRepository.saveAndFlush(image(otherId, 0));

		int purged = imageRepository.deleteByContentId(contentId);

		assertThat(purged).isEqualTo(2);
		assertThat(imageRepository.findSortOrdersByContentId(contentId)).isEmpty();
		assertThat(imageRepository.findSortOrdersByContentId(otherId)).containsExactly(0);
	}

	// ------------------------------------------------------------------ sayaçlar (kota)

	@Test
	void creationCountIncludesDeletedRowsButDraftCountDoesNot() {
		UUID author = UUID.randomUUID();
		saveDraft(author, ContentType.NEWS, hash('8'));
		Content deleted = saveDraft(author, ContentType.NEWS, hash('9'));
		deleted.markDeleted(NOW);
		contentRepository.saveAndFlush(deleted);
		saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('a'));

		assertThat(contentRepository.countByAuthorIdAndCreatedAtAfter(author, NOW.minusSeconds(3600))).isEqualTo(2);
		assertThat(contentRepository.countByAuthorIdAndCreatedAtAfter(author, NOW.plusSeconds(1))).isZero();
		assertThat(contentRepository.countByAuthorIdAndStatusAndDeletedAtIsNull(author, ContentStatus.DRAFT)).isEqualTo(1);
		assertThat(contentRepository.countByAuthorIdAndStatusAndDeletedAtIsNull(author, ContentStatus.PUBLISHED)).isZero();
	}

	// ------------------------------------------------------------------ veritabanı kısıtları

	@Test
	void databaseRejectsUnknownStatusTypeAndLocale() {
		UUID id = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('b')).getId();

		assertRejected("ck_contents_status", "update contents set status = 'DELETED', published_at = created_at where id = ?", id);
		assertRejected("ck_contents_type", "update contents set type = 'POEM' where id = ?", id);
		assertRejected("ck_contents_default_locale", "update contents set default_locale = 'de' where id = ?", id);
		assertRejected("ck_contents_content_hash", "update contents set content_hash = 'kisa' where id = ?", id);
	}

	@Test
	void databaseRejectsBlankTitleAndOversizedTexts() {
		UUID id = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('c')).getId();
		String insert = "insert into content_translations (content_id, locale, title, summary, body) values (?, 'tr', ?, ?, ?)";

		assertRejected("ck_content_translations_title", insert, id, "   ", null, null);
		assertRejected("ck_content_translations_summary", insert, id, "Başlık", "   ", null);
		assertRejected("ck_content_translations_body", insert, id, "Başlık", null, "a".repeat(20_001));
		assertRejected("", insert, id, "Başlık", "a".repeat(501), null);
	}

	@Test
	void aTranslationNeedsAnExistingContentAndIsUniquePerLocale() {
		UUID id = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('d')).getId();
		translationRepository.saveAndFlush(ContentTranslation.of(id, "tr", "Başlık", null, null));
		String insert = "insert into content_translations (content_id, locale, title) values (?, ?, 'x')";

		assertRejected("fk_content_translations_content", insert, UUID.randomUUID(), "tr");
		assertRejected("pk_content_translations", insert, id, "tr");
		assertRejected("ck_content_translations_locale", insert, id, "de");
	}

	@Test
	void databaseRejectsInvalidImages() {
		UUID id = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('e')).getId();
		String insert = "insert into content_images (id, content_id, content, mime_type, size_bytes, width, height, sort_order, created_at)"
				+ " values (?, ?, ?, ?, ?, 10, 10, ?, now())";
		byte[] bytes = { 1 };

		assertRejected("ck_content_images_mime_type", insert, UUID.randomUUID(), id, bytes, "image/svg+xml", 1, 0);
		assertRejected("ck_content_images_mime_type", insert, UUID.randomUUID(), id, bytes, "text/html", 1, 0);
		assertRejected("ck_content_images_size_bytes", insert, UUID.randomUUID(), id, bytes, "image/png", 2 * 1024 * 1024 + 1, 0);
		assertRejected("ck_content_images_sort_order", insert, UUID.randomUUID(), id, bytes, "image/png", 1, 6);
		assertRejected("fk_content_images_content", insert, UUID.randomUUID(), UUID.randomUUID(), bytes, "image/png", 1, 0);
	}

	@Test
	void twoImagesCannotShareASlot() {
		UUID contentId = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('f')).getId();
		imageRepository.saveAndFlush(image(contentId, 1));

		ContentImage sameSlot = image(contentId, 1);
		assertThatThrownBy(() -> imageRepository.saveAndFlush(sameSlot))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uq_content_images_content_id_sort_order");
	}

	@Test
	void removingAContentRowCascadesToItsTranslationsAndImages() {
		UUID contentId = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('0')).getId();
		translationRepository.save(ContentTranslation.of(contentId, "tr", "Başlık", null, null));
		imageRepository.saveAndFlush(image(contentId, 0));

		jdbcTemplate.update("delete from contents where id = ?", contentId);

		assertThat(jdbcTemplate.queryForObject("select count(*) from content_translations where content_id = ?", Integer.class,
				contentId)).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from content_images where content_id = ?", Integer.class,
				contentId)).isZero();
	}
	// ------------------------------------------------------------------ V103 / V104 (CONT-02)

	@Test
	void theFixedCategoryListIsSeededWithBothKinds() {
		assertThat(jdbcTemplate.queryForObject("select count(*) from content_categories", Integer.class)).isEqualTo(14);
		assertThat(jdbcTemplate.queryForObject("select count(*) from content_categories where kind = 'PLATFORM'", Integer.class)).isEqualTo(6);
		assertThat(jdbcTemplate.queryForObject("select count(*) from content_categories where kind = 'TOPIC'", Integer.class)).isEqualTo(8);
		assertThat(jdbcTemplate.queryForList("select code from content_categories order by sort_order", String.class))
				.startsWith("PC", "PLAYSTATION", "XBOX").contains("UPDATE", "ESPORTS");
		assertRejected("uq_content_categories_code", "insert into content_categories (id, code, kind, name_tr, name_en, sort_order) values (?, 'PC', 'PLATFORM', 'x', 'x', 99)",
				UUID.randomUUID());
	}

	@Test
	void categoryAssignmentsAreUniquePerContentAndCascadeOnDelete() {
		UUID id = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('c')).getId();
		jdbcTemplate.update("insert into content_category_assignments (content_id, category_id) select ?, id from content_categories where code = 'PC'", id);

		assertRejected("pk_content_category_assignments",
				"insert into content_category_assignments (content_id, category_id) select ?, id from content_categories where code = 'PC'", id);
		assertRejected("fk_content_category_assignments_category",
				"insert into content_category_assignments (content_id, category_id) values (?, ?)", id, UUID.randomUUID());
		assertRejected("fk_content_category_assignments_content",
				"insert into content_category_assignments (content_id, category_id) select ?, id from content_categories where code = 'XBOX'", UUID.randomUUID());
		assertRejected("fk_content_category_assignments_category", "delete from content_categories where code = 'PC'");

		jdbcTemplate.update("delete from contents where id = ?", id);
		assertThat(jdbcTemplate.queryForObject("select count(*) from content_category_assignments where content_id = ?", Integer.class, id)).isZero();
	}

	@Test
	void sourcesAcceptOnlyHttpsLinksAndAtMostThreeSlots() {
		UUID id = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('d')).getId();
		String insert = "insert into content_sources (id, content_id, title, url, source_date, sort_order) values (?, ?, ?, ?, null, ?)";
		jdbcTemplate.update(insert, UUID.randomUUID(), id, "IGN", "https://www.ign.com/a", 0);

		assertRejected("uq_content_sources_content_id_sort_order", insert, UUID.randomUUID(), id, "IGN", "https://www.ign.com/b", 0);
		assertRejected("ck_content_sources_sort_order", insert, UUID.randomUUID(), id, "IGN", "https://www.ign.com/b", 3);
		for (String unsafe : new String[] { "http://www.ign.com/a", "javascript:alert(1)", "data:text/html,x", "https://a b.com", "ftp://x.com/a" }) {
			assertRejected("ck_content_sources_url", insert, UUID.randomUUID(), id, "IGN", unsafe, 1);
		}
		assertRejected("ck_content_sources_title", insert, UUID.randomUUID(), id, "   ", "https://www.ign.com/b", 1);

		jdbcTemplate.update("delete from contents where id = ?", id);
		assertThat(jdbcTemplate.queryForObject("select count(*) from content_sources where content_id = ?", Integer.class, id)).isZero();
	}

	@Test
	void publicationColumnsFollowTheStatusRules() {
		UUID id = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('e')).getId();

		assertRejected("ck_contents_published_at", "update contents set status = 'PUBLISHED' where id = ?", id);
		assertRejected("ck_contents_published_at", "update contents set published_at = now() where id = ?", id);
		assertRejected("ck_contents_featured", "update contents set featured = true where id = ?", id);

		jdbcTemplate.update("update contents set status = 'PUBLISHED', published_at = now() where id = ?", id);
		assertRejected("ck_contents_featured", "update contents set featured = true where id = ?", id);
		assertRejected("ck_contents_featured", "update contents set featured = true, featured_at = now() where id = ?", id);
		jdbcTemplate.update("update contents set featured = true, featured_at = now(), featured_by = ? where id = ?", UUID.randomUUID(), id);
		assertThat(jdbcTemplate.queryForObject("select featured from contents where id = ?", Boolean.class, id)).isTrue();
	}

	@Test
	void gameLinkIsAnOptionalReferenceWithoutAForeignKey() {
		UUID id = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('f')).getId();
		UUID game = UUID.randomUUID();

		jdbcTemplate.update("update contents set game_id = ? where id = ?", game, id);

		assertThat(jdbcTemplate.queryForObject("select game_id from contents where id = ?", UUID.class, id)).isEqualTo(game);
	}
	// ------------------------------------------------------------------ V106 (CONT-10)

	@Test
	void removalDetailsAndTheRemovedStatusGoTogether() {
		UUID id = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('g')).getId();
		jdbcTemplate.update("update contents set status = 'PUBLISHED', published_at = now() where id = ?", id);
		UUID moderator = UUID.randomUUID();

		assertRejected("ck_contents_removal", "update contents set status = 'REMOVED' where id = ?", id);
		assertRejected("ck_contents_removal",
				"update contents set status = 'REMOVED', removed_at = now(), removed_by = ? where id = ?", moderator, id);
		assertRejected("ck_contents_removal",
				"update contents set status = 'PUBLISHED', removed_at = now(), removed_by = ?, removed_reason = 'x' where id = ?", moderator, id);
		assertRejected("ck_contents_removal",
				"update contents set status = 'REMOVED', removed_at = now(), removed_by = ?, removed_reason = '   ' where id = ?", moderator, id);
		// 500 karakteri aşan gerekçe sütun uzunluğuyla (varchar(500)) reddedilir.
		assertRejected("(500)",
				"update contents set status = 'REMOVED', removed_at = now(), removed_by = ?, removed_reason = ? where id = ?", moderator,
				"a".repeat(501), id);

		jdbcTemplate.update(
				"update contents set status = 'REMOVED', removed_at = now(), removed_by = ?, removed_reason = 'Spam' where id = ?", moderator, id);
		assertThat(jdbcTemplate.queryForObject("select removed_reason from contents where id = ?", String.class, id)).isEqualTo("Spam");
		// Kaldırılmış içerik öne çıkarılamaz (ck_contents_featured: yalnız PUBLISHED).
		assertRejected("ck_contents_featured",
				"update contents set featured = true, featured_at = now(), featured_by = ? where id = ?", moderator, id);
	}

	@Test
	void moderationActionsAreValidatedAndFollowTheContentOnDelete() {
		UUID id = saveDraft(UUID.randomUUID(), ContentType.NEWS, hash('h')).getId();
		String insert = "insert into content_moderation_actions (id, content_id, action, actor_id, actor_role, source, reason, created_at) "
				+ "values (?, ?, ?, ?, ?, ?, ?, now())";
		UUID actor = UUID.randomUUID();
		jdbcTemplate.update(insert, UUID.randomUUID(), id, "REMOVE", actor, "MODERATOR", "ADMIN_SERVICE", "Spam");
		jdbcTemplate.update(insert, UUID.randomUUID(), id, "RESTORE", actor, "ADMIN", "ADMIN_SERVICE", "Düzeltildi");

		assertRejected("ck_content_moderation_actions_action", insert, UUID.randomUUID(), id, "HIDE", actor, "MODERATOR",
				"ADMIN_SERVICE", "Spam");
		assertRejected("ck_content_moderation_actions_actor_role", insert, UUID.randomUUID(), id, "REMOVE", actor, "PLAYER",
				"ADMIN_SERVICE", "Spam");
		// Doğrudan moderatör yolu kaldırıldı: karar yolu yalnız ADMIN_SERVICE olabilir.
		assertRejected("ck_content_moderation_actions_source", insert, UUID.randomUUID(), id, "REMOVE", actor, "MODERATOR",
				"MODERATOR_API", "Spam");
		assertRejected("ck_content_moderation_actions_source", insert, UUID.randomUUID(), id, "REMOVE", actor, "MODERATOR",
				"CLIENT", "Spam");
		assertRejected("ck_content_moderation_actions_reason", insert, UUID.randomUUID(), id, "REMOVE", actor, "MODERATOR",
				"ADMIN_SERVICE", "  ");
		assertRejected("fk_content_moderation_actions_content", insert, UUID.randomUUID(), UUID.randomUUID(), "REMOVE", actor,
				"MODERATOR", "ADMIN_SERVICE", "Spam");

		jdbcTemplate.update("delete from contents where id = ?", id);
		assertThat(jdbcTemplate.queryForObject("select count(*) from content_moderation_actions where content_id = ?", Integer.class, id))
				.isZero();
	}

	@Test
	void theInboxRejectsTheSameEventTwice() {
		UUID eventId = UUID.randomUUID();
		String insert = "insert into inbox_events (event_id, event_type, producer, processed_at) values (?, 'ModerationActionTaken', 'admin-service', now())";
		jdbcTemplate.update(insert, eventId);

		assertRejected("pk_inbox_events", insert, eventId);
	}
}
