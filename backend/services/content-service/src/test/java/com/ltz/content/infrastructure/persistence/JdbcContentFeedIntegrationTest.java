package com.ltz.content.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.ltz.content.application.port.ContentFeedPort.Card;
import com.ltz.content.application.port.ContentFeedPort.Criteria;
import com.ltz.content.application.port.ContentFeedPort.Position;
import com.ltz.content.application.service.ContentFeedService;
import com.ltz.content.domain.enums.ContentType;

/**
 * Akış sorgusu gerçek PostgreSQL üzerinde: yalnız yayımlanmış ve silinmemiş içerik, filtreler, keyset sayfalama, dil seçimi,
 * "içerir" araması ve kaçış karakterleri. Satırlar doğrudan SQL ile eklenir; böylece yayın zamanları ve sıralama kesin kontrol edilir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ JdbcContentFeed.class, JdbcContentFeedIntegrationTest.JdbcClientConfig.class })
class JdbcContentFeedIntegrationTest extends PostgreSqlIntegrationTest {

	@TestConfiguration
	static class JdbcClientConfig {
		@Bean
		JdbcClient jdbcClient(JdbcTemplate jdbcTemplate) {
			return JdbcClient.create(jdbcTemplate);
		}
	}

	private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
	private static final UUID AUTHOR = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID ADMIN = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID GAME = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");

	private static final UUID A = UUID.fromString("0198a3c2-0000-7000-8000-00000000000a");
	private static final UUID B = UUID.fromString("0198a3c2-0000-7000-8000-00000000000b");
	private static final UUID C = UUID.fromString("0198a3c2-0000-7000-8000-00000000000c");
	private static final UUID DRAFT = UUID.fromString("0198a3c2-0000-7000-8000-00000000000d");
	private static final UUID DELETED = UUID.fromString("0198a3c2-0000-7000-8000-00000000000e");
	private static final UUID F = UUID.fromString("0198a3c2-0000-7000-8000-00000000000f");

	@Autowired
	private JdbcContentFeed feed;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void seed() {
		// A: öne çıkan haber, oyunlu, PC+UPDATE, iki dilli.
		content(A, "NEWS", "PUBLISHED", "tr", T0.plusSeconds(3600), GAME, true, null);
		translation(A, "tr", "Yeni güncelleme çıktı", "Büyük yama yayında", "Yama notları ve değişiklikler burada.");
		translation(A, "en", "New update released", "Big patch is live", "Patch notes and changes are here.");
		categorize(A, "PC", "UPDATE");
		image(A, 1);
		image(A, 0);

		// B ve C aynı anda yayımlandı: sıralamayı id belirler (büyük id önce).
		content(B, "ANNOUNCEMENT", "PUBLISHED", "tr", T0.plusSeconds(7200), null, false, null);
		translation(B, "tr", "Sunucu bakımı", null, "Pazartesi sabahı planlı bakım yapılacaktır.");
		content(C, "NEWS", "PUBLISHED", "tr", T0.plusSeconds(7200), GAME, false, null);
		translation(C, "tr", "Rehber: ilk adımlar", null, "Kazanma oranı 100% olan stratejiler ve ipuçları.");
		categorize(C, "PC");

		// Taslak ve silinmiş içerik akışta hiçbir koşulda görünmez.
		content(DRAFT, "NEWS", "DRAFT", "tr", null, null, false, null);
		translation(DRAFT, "tr", "Taslak haber", null, "Henüz yayımlanmadı.");
		content(DELETED, "NEWS", "PUBLISHED", "tr", T0.plusSeconds(1800), null, false, T0.plusSeconds(9000));
		translation(DELETED, "tr", "Silinmiş haber", null, "Bu içerik silindi.");

		// F: yalnız İngilizce (ilk dil en).
		content(F, "NEWS", "PUBLISHED", "en", T0.plusSeconds(10_800), null, false, null);
		translation(F, "en", "English only story", null, "A story that exists in English only.");
	}

	// ------------------------------------------------------------------ yardımcılar

	private static OffsetDateTime utc(Instant instant) {
		return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private void content(UUID id, String type, String status, String locale, Instant publishedAt, UUID gameId, boolean featured,
			Instant deletedAt) {
		String hash = id.toString().replace("-", "").repeat(2);
		Instant created = T0.minusSeconds(86_400);
		jdbc.update("""
				insert into contents (id, author_id, type, status, default_locale, content_hash, created_at, updated_at,
				    deleted_at, version, game_id, published_at, featured, featured_at, featured_by)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
				""", id, AUTHOR, type, status, locale, hash, utc(created), utc(created), utc(deletedAt), gameId, utc(publishedAt),
				featured, featured ? utc(publishedAt) : null, featured ? ADMIN : null);
	}

	private void translation(UUID contentId, String locale, String title, String summary, String body) {
		jdbc.update("insert into content_translations (content_id, locale, title, summary, body) values (?, ?, ?, ?, ?)",
				contentId, locale, title, summary, body);
	}

	private void categorize(UUID contentId, String... codes) {
		for (String code : codes) {
			jdbc.update("""
					insert into content_category_assignments (content_id, category_id)
					select ?, id from content_categories where code = ?
					""", contentId, code);
		}
	}

	private void image(UUID contentId, int sortOrder) {
		jdbc.update("""
				insert into content_images (id, content_id, content, mime_type, size_bytes, width, height, sort_order, created_at)
				values (?, ?, ?, 'image/png', 4, 10, 10, ?, ?)
				""", UUID.randomUUID(), contentId, new byte[] { 1, 2, 3, 4 }, sortOrder, utc(T0));
	}

	private static Criteria all() {
		return criteria(null, null, null, null, null, null, null, "tr", null, 50);
	}

	private static Criteria criteria(String search, String category, UUID gameId, ContentType type, Boolean featured, Instant from,
			Instant to, String locale, Position after, int limit) {
		return new Criteria(search, category, gameId, type, featured, from, to, locale, after, limit);
	}

	private List<UUID> ids(Criteria criteria) {
		return feed.find(criteria).stream().map(Card::id).toList();
	}

	// ------------------------------------------------------------------ görünürlük ve sıralama

	@Test
	void onlyPublishedAndNotDeletedContentIsListedNewestFirstWithIdAsTieBreaker() {
		assertThat(ids(all())).containsExactly(F, C, B, A);
	}

	@Test
	void keysetPagingWalksEveryItemExactlyOnceAcrossTheTie() {
		List<UUID> seen = new ArrayList<>();
		Position after = null;
		for (int page = 0; page < 4; page++) {
			List<Card> cards = feed.find(criteria(null, null, null, null, null, null, null, "tr", after, 2));
			if (cards.isEmpty()) {
				break;
			}
			cards.forEach(card -> seen.add(card.id()));
			Card last = cards.get(cards.size() - 1);
			after = new Position(last.publishedAt(), last.id());
		}

		assertThat(seen).containsExactly(F, C, B, A);
	}

	@Test
	void theLimitIsHonoured() {
		assertThat(feed.find(criteria(null, null, null, null, null, null, null, "tr", null, 1))).hasSize(1);
	}

	// ------------------------------------------------------------------ filtreler

	@Test
	void filtersByTypeGameFeaturedAndCategory() {
		assertThat(ids(criteria(null, null, null, ContentType.ANNOUNCEMENT, null, null, null, "tr", null, 50))).containsExactly(B);
		assertThat(ids(criteria(null, null, GAME, null, null, null, null, "tr", null, 50))).containsExactly(C, A);
		assertThat(ids(criteria(null, null, null, null, true, null, null, "tr", null, 50))).containsExactly(A);
		assertThat(ids(criteria(null, null, null, null, false, null, null, "tr", null, 50))).containsExactly(F, C, B);
		assertThat(ids(criteria(null, "PC", null, null, null, null, null, "tr", null, 50))).containsExactly(C, A);
		assertThat(ids(criteria(null, "UPDATE", null, null, null, null, null, "tr", null, 50))).containsExactly(A);
		assertThat(ids(criteria(null, "ESPORTS", null, null, null, null, null, "tr", null, 50))).isEmpty();
	}

	@Test
	void filtersCombineWithAnd() {
		assertThat(ids(criteria(null, "PC", GAME, ContentType.NEWS, false, null, null, "tr", null, 50))).containsExactly(C);
		assertThat(ids(criteria(null, "UPDATE", GAME, ContentType.NEWS, false, null, null, "tr", null, 50))).isEmpty();
	}

	@Test
	void theDateRangeIncludesFromAndExcludesTo() {
		Instant a = T0.plusSeconds(3600);
		Instant bc = T0.plusSeconds(7200);

		assertThat(ids(criteria(null, null, null, null, null, a, bc, "tr", null, 50))).containsExactly(A);
		assertThat(ids(criteria(null, null, null, null, null, bc, null, "tr", null, 50))).containsExactly(F, C, B);
		assertThat(ids(criteria(null, null, null, null, null, null, a, "tr", null, 50))).isEmpty();
	}

	@Test
	void aMaliciousCategoryCodeIsJustANonMatchingParameter() {
		assertThat(ids(criteria(null, "PC' OR '1'='1", null, null, null, null, null, "tr", null, 50))).isEmpty();
		assertThat(ids(criteria(null, "PC'; DROP TABLE contents; --", null, null, null, null, null, "tr", null, 50))).isEmpty();
		assertThat(ids(all())).hasSize(4);
	}

	// ------------------------------------------------------------------ dil seçimi (CONT-15)

	@Test
	void theRequestedLanguageIsShownWhenATranslationExistsElseTheOriginalLanguage() {
		Map<UUID, Card> english = byId(criteria(null, null, null, null, null, null, null, "en", null, 50));
		Map<UUID, Card> turkish = byId(all());

		assertThat(english.get(A).title()).isEqualTo("New update released");
		assertThat(english.get(A).locale()).isEqualTo("en");
		assertThat(english.get(B).title()).isEqualTo("Sunucu bakımı");
		assertThat(english.get(B).locale()).isEqualTo("tr");
		assertThat(turkish.get(A).title()).isEqualTo("Yeni güncelleme çıktı");
		assertThat(turkish.get(A).summary()).isEqualTo("Büyük yama yayında");
		assertThat(turkish.get(F).title()).isEqualTo("English only story");
		assertThat(turkish.get(F).locale()).isEqualTo("en");
	}

	private Map<UUID, Card> byId(Criteria criteria) {
		return feed.find(criteria).stream().collect(java.util.stream.Collectors.toMap(Card::id, card -> card));
	}

	// ------------------------------------------------------------------ arama (CONT-12)

	private List<UUID> search(String text) {
		return ids(criteria(ContentFeedService.searchPattern(text), null, null, null, null, null, null, "tr", null, 50));
	}

	@Test
	void searchIsACaseInsensitiveContainsOverTitleSummaryAndBody() {
		assertThat(search("GÜNCELLEME")).containsExactly(A);
		assertThat(search("güncel")).containsExactly(A);
		assertThat(search("yama")).containsExactly(A);
		assertThat(search("bakım")).containsExactly(B);
		assertThat(search("planlı")).containsExactly(B);
		assertThat(search("zzzz")).isEmpty();
	}

	@Test
	void searchAlsoMatchesOtherLanguagesOfTheSameContent() {
		assertThat(search("released")).containsExactly(A);
		assertThat(search("english only")).containsExactly(F);
	}

	@Test
	void draftsAndDeletedContentNeverMatchASearch() {
		assertThat(search("taslak")).isEmpty();
		assertThat(search("silinmiş")).isEmpty();
	}

	@Test
	void likeWildcardsInTheSearchTextAreMatchedLiterally() {
		assertThat(search("0%")).containsExactly(C);
		assertThat(search("0_")).isEmpty();
		assertThat(search("a%b")).isEmpty();
		assertThat(search("!!")).isEmpty();
	}

	@Test
	void searchCombinesWithFilters() {
		assertThat(ids(criteria(ContentFeedService.searchPattern("yama"), "PC", GAME, ContentType.NEWS, true, null, null, "tr", null, 50)))
				.containsExactly(A);
		assertThat(ids(criteria(ContentFeedService.searchPattern("yama"), null, null, ContentType.ANNOUNCEMENT, null, null, null, "tr", null, 50)))
				.isEmpty();
	}

	// ------------------------------------------------------------------ Türkçe harfler ve indeks (V105)

	/**
	 * Büyük/küçük harf katlamasını veritabanının {@code lower()} fonksiyonu yapar (hem sütun hem desen). Gözlenen davranış
	 * (postgres:17-alpine, en_US.utf8, libc): {@code lower('İ') = 'i'}, {@code lower('I') = 'i'}, {@code ı} aynı kalır. Yani
	 * İ/i ve I/i birbirine eşleşir; dotless {@code ı}, {@code I} ile eşleşmez (bilinen sınır, NOTLAR.md).
	 */
	@Test
	void turkishLettersAreFoldedByTheDatabaseOnBothSides() {
		UUID g = UUID.fromString("0198a3c2-0000-7000-8000-0000000000a1");
		UUID h = UUID.fromString("0198a3c2-0000-7000-8000-0000000000a2");
		content(g, "NEWS", "PUBLISHED", "tr", T0.plusSeconds(20_000), null, false, null);
		translation(g, "tr", "İstanbul Işık Rehberi", null, "Gezi notları");
		content(h, "NEWS", "PUBLISHED", "tr", T0.plusSeconds(20_100), null, false, null);
		translation(h, "tr", "ISPARTA gezisi", null, "Gül bahçeleri");

		// İ → i ve I → i: üç yazım da aynı içeriği bulur.
		assertThat(search("istanbul")).contains(g);
		assertThat(search("İstanbul")).contains(g);
		assertThat(search("ISTANBUL")).contains(g);
		assertThat(search("İSTANBUL")).contains(g);
		// Büyük I içeren başlık küçük i ile ve büyük harfle bulunur.
		assertThat(search("isparta")).contains(h);
		assertThat(search("ISPARTA")).contains(h);
		assertThat(search("İsparta")).contains(h);
		// Sınır: I → i katlandığı için noktasız ı ile yazılan arama "Işık" / "ISPARTA" ile eşleşmez.
		assertThat(search("Işık")).contains(g);
		assertThat(search("IŞıK")).contains(g);
		assertThat(search("ışık")).doesNotContain(g);
		assertThat(search("ısparta")).doesNotContain(h);
	}

	@Test
	void theTrigramIndexesExistAndTheSearchPredicateCanUseThem() {
		assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname = 'pg_trgm'", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForList("select indexname from pg_indexes where tablename = 'content_translations' and indexname like '%_trgm'",
				String.class)).containsExactlyInAnyOrder("ix_content_translations_title_trgm", "ix_content_translations_summary_trgm",
						"ix_content_translations_body_trgm");

		// Küçük tabloda planlayıcı sıralı taramayı seçer; indeksin kullanılabildiğini göstermek için kapatılır (yalnız bu transaction).
		jdbc.execute("set local enable_seqscan = off");
		String predicate = JdbcContentFeed.SEARCH_MATCH.replace(":q", "'%abc%'");
		String plan = String.join(System.lineSeparator(), jdbc.queryForList("explain select 1 from content_translations s where " + predicate, String.class));

		assertThat(plan).contains("ix_content_translations_title_trgm", "ix_content_translations_summary_trgm",
				"ix_content_translations_body_trgm");
	}

	// ------------------------------------------------------------------ kategori ve kapak

	@Test
	void categoryCodesAreOrderedByTheFixedSortOrderAndEmptyInputIsEmpty() {
		Map<UUID, List<String>> codes = feed.categoryCodes(List.of(A, B, C));

		assertThat(codes.get(A)).containsExactly("PC", "UPDATE");
		assertThat(codes.get(C)).containsExactly("PC");
		assertThat(codes).doesNotContainKey(B);
		assertThat(feed.categoryCodes(List.of())).isEmpty();
	}

	@Test
	void theCoverIsTheImageWithTheLowestSortOrder() {
		UUID first = jdbc.queryForObject("select id from content_images where content_id = ? and sort_order = 0", UUID.class, A);

		Map<UUID, UUID> covers = feed.coverImageIds(List.of(A, B));

		assertThat(covers).containsEntry(A, first).doesNotContainKey(B);
		assertThat(feed.coverImageIds(List.of())).isEmpty();
	}
}
