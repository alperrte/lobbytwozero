package com.ltz.content.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

import com.ltz.content.application.port.ContentFeedPort;
import com.ltz.content.domain.enums.ContentType;

/**
 * Akış sorgusu parametrik SQL ile yazılır (filtreler isteğe bağlı olduğundan WHERE koşulları dinamik eklenir; hiçbir kullanıcı
 * girdisi SQL metnine birleştirilmez, hepsi bağlı parametredir). Gösterilecek çeviri, istenen dil varsa o, yoksa içeriğin ilk
 * dilidir. Arama (ILIKE benzeri "içerir"), içeriğin herhangi bir dilindeki başlık, özet ve gövdede yapılır.
 */
@Repository
public class JdbcContentFeed implements ContentFeedPort {

	/**
	 * Arama koşulu (başlık, özet ve gövdede "içerir"). LIKE kaçış karakteri ünlemdir ({@code !%}, {@code !_}, {@code !!}); ters
	 * bölü SQL ayrıştırıcılarıyla karışmasın diye seçilmedi. İfadeler V105'teki {@code pg_trgm} GIN indekslerinin ifadeleriyle
	 * birebir aynıdır ({@code lower(sütun)}); bu yüzden sorgu indeksi kullanabilir. Desen de aynı {@code lower()} ile katlanır
	 * (tek katlama kaynağı veritabanıdır). Kullanıcı metni yalnız {@code :q} bağlı parametresidir.
	 */
	static final String SEARCH_MATCH = "(lower(s.title) like lower(:q) escape '!' or lower(s.summary) like lower(:q) escape '!'"
			+ " or lower(s.body) like lower(:q) escape '!')";

	private final JdbcClient jdbc;

	public JdbcContentFeed(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Card> find(Criteria criteria) {
		StringBuilder sql = new StringBuilder("""
				select c.id, c.author_id, c.type, c.game_id, c.featured, c.published_at, t.locale, t.title, t.summary
				from contents c
				join content_translations t on t.content_id = c.id and t.locale = coalesce(
				    (select r.locale from content_translations r where r.content_id = c.id and r.locale = :locale),
				    c.default_locale)
				where c.status = 'PUBLISHED' and c.deleted_at is null
				""");
		Map<String, Object> params = new HashMap<>();
		params.put("locale", criteria.locale());
		if (criteria.type() != null) {
			sql.append(" and c.type = :type");
			params.put("type", criteria.type().name());
		}
		if (criteria.gameId() != null) {
			sql.append(" and c.game_id = :gameId");
			params.put("gameId", criteria.gameId());
		}
		if (criteria.featured() != null) {
			sql.append(Boolean.TRUE.equals(criteria.featured()) ? " and c.featured" : " and not c.featured");
		}
		if (criteria.from() != null) {
			sql.append(" and c.published_at >= :from");
			params.put("from", utc(criteria.from()));
		}
		if (criteria.to() != null) {
			sql.append(" and c.published_at < :to");
			params.put("to", utc(criteria.to()));
		}
		if (criteria.categoryCode() != null) {
			sql.append(" and exists (select 1 from content_category_assignments a")
					.append(" join content_categories k on k.id = a.category_id")
					.append(" where a.content_id = c.id and k.code = :category)");
			params.put("category", criteria.categoryCode());
		}
		if (criteria.searchPattern() != null) {
			sql.append(" and exists (select 1 from content_translations s where s.content_id = c.id and ").append(SEARCH_MATCH)
					.append(")");
			params.put("q", criteria.searchPattern());
		}
		if (criteria.after() != null) {
			sql.append(" and (c.published_at, c.id) < (:afterAt, :afterId)");
			params.put("afterAt", utc(criteria.after().publishedAt()));
			params.put("afterId", criteria.after().id());
		}
		sql.append(" order by c.published_at desc, c.id desc limit :limit");
		params.put("limit", criteria.limit());

		StatementSpec statement = jdbc.sql(sql.toString());
		for (Map.Entry<String, Object> param : params.entrySet()) {
			statement = statement.param(param.getKey(), param.getValue());
		}
		return statement.query(JdbcContentFeed::toCard).list();
	}

	@Override
	public Map<UUID, List<String>> categoryCodes(Collection<UUID> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, List<String>> codes = new HashMap<>();
		jdbc.sql("""
				select a.content_id, k.code
				from content_category_assignments a
				join content_categories k on k.id = a.category_id
				where a.content_id in (:ids)
				order by k.sort_order
				""").param("ids", contentIds).query((rs, row) -> {
					codes.computeIfAbsent(rs.getObject("content_id", UUID.class), key -> new ArrayList<>())
							.add(rs.getString("code"));
					return row;
				}).list();
		return codes;
	}

	@Override
	public Map<UUID, UUID> coverImageIds(Collection<UUID> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, UUID> covers = new LinkedHashMap<>();
		jdbc.sql("""
				select distinct on (content_id) content_id, id
				from content_images
				where content_id in (:ids)
				order by content_id, sort_order
				""").param("ids", contentIds).query((rs, row) -> {
					covers.put(rs.getObject("content_id", UUID.class), rs.getObject("id", UUID.class));
					return row;
				}).list();
		return covers;
	}

	private static Card toCard(ResultSet rs, int row) throws SQLException {
		return new Card(
				rs.getObject("id", UUID.class),
				rs.getObject("author_id", UUID.class),
				ContentType.valueOf(rs.getString("type")),
				rs.getString("locale"),
				rs.getString("title"),
				rs.getString("summary"),
				rs.getObject("game_id", UUID.class),
				rs.getBoolean("featured"),
				rs.getObject("published_at", OffsetDateTime.class).toInstant());
	}

	private static OffsetDateTime utc(java.time.Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
