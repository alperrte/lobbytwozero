package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ltz.content.api.dto.request.ContentFeedRequest;
import com.ltz.content.api.dto.response.ContentPageResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.ContentFeedPort;
import com.ltz.content.application.port.ContentFeedPort.Card;
import com.ltz.content.application.port.ContentFeedPort.Criteria;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.InvalidContentQueryException;
import com.ltz.content.infrastructure.persistence.repository.ContentCategoryRepository;

/** Herkese açık akış (CONT-01/12/13): limit+1 okuma, keyset imleci, arama deseni ve sorgu doğrulaması. */
class ContentFeedServiceTest {

	private static final Instant PUBLISHED = Instant.parse("2026-11-07T09:00:00Z");

	private final ContentFeedPort feed = mock(ContentFeedPort.class);
	private final ContentFeedService service = new ContentFeedService(feed, mock(ContentCategoryRepository.class),
			new ContentMapper());

	private static ContentFeedRequest request(String q, String category, UUID gameId, ContentType type, Boolean featured,
			Instant from, Instant to, String locale, Integer limit, String cursor) {
		return new ContentFeedRequest(q, category, gameId, type, featured, from, to, locale, limit, cursor);
	}

	private static ContentFeedRequest empty() {
		return request(null, null, null, null, null, null, null, null, null, null);
	}

	private static Card card(int index) {
		return new Card(UUID.fromString(String.format("0198a3c2-0000-7d44-9a55-%012d", index)), UUID.randomUUID(),
				ContentType.NEWS, "tr", "Haber " + index, "Özet", null, false, PUBLISHED.minusSeconds(index));
	}

	private Criteria capturedCriteria() {
		ArgumentCaptor<Criteria> captor = ArgumentCaptor.forClass(Criteria.class);
		verify(feed).find(captor.capture());
		return captor.getValue();
	}

	@Test
	void defaultsAreTwentyItemsAndTurkish() {
		when(feed.find(any())).thenReturn(List.of());

		ContentPageResponse page = service.list(empty());

		Criteria criteria = capturedCriteria();
		assertThat(criteria.limit()).isEqualTo(21);
		assertThat(criteria.locale()).isEqualTo("tr");
		assertThat(criteria.after()).isNull();
		assertThat(criteria.searchPattern()).isNull();
		assertThat(page.items()).isEmpty();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void filtersArePassedThroughUnchanged() {
		when(feed.find(any())).thenReturn(List.of());
		UUID game = UUID.randomUUID();
		Instant from = PUBLISHED.minusSeconds(3600);

		service.list(request(null, "PC", game, ContentType.ANNOUNCEMENT, true, from, PUBLISHED, "en", 5, null));

		Criteria criteria = capturedCriteria();
		assertThat(criteria.categoryCode()).isEqualTo("PC");
		assertThat(criteria.gameId()).isEqualTo(game);
		assertThat(criteria.type()).isEqualTo(ContentType.ANNOUNCEMENT);
		assertThat(criteria.featured()).isTrue();
		assertThat(criteria.from()).isEqualTo(from);
		assertThat(criteria.to()).isEqualTo(PUBLISHED);
		assertThat(criteria.locale()).isEqualTo("en");
		assertThat(criteria.limit()).isEqualTo(6);
	}

	@Test
	void anExtraRowMeansANextPageAndTheCursorPointsAtTheLastReturnedItem() {
		List<Card> rows = List.of(card(1), card(2), card(3));
		when(feed.find(any())).thenReturn(rows);
		when(feed.categoryCodes(any())).thenReturn(Map.of(rows.get(0).id(), List.of("PC", "UPDATE")));
		UUID cover = UUID.randomUUID();
		when(feed.coverImageIds(any())).thenReturn(Map.of(rows.get(1).id(), cover));

		ContentPageResponse page = service.list(request(null, null, null, null, null, null, null, null, 2, null));

		assertThat(page.items()).hasSize(2);
		assertThat(page.items().get(0).categories()).containsExactly("PC", "UPDATE");
		assertThat(page.items().get(0).coverImageUrl()).isNull();
		assertThat(page.items().get(1).categories()).isEmpty();
		assertThat(page.items().get(1).coverImageUrl())
				.isEqualTo("/api/v1/content/contents/" + rows.get(1).id() + "/images/" + cover);
		var position = PublishedCursor.decode(page.nextCursor());
		assertThat(position.id()).isEqualTo(rows.get(1).id());
		assertThat(position.publishedAt()).isEqualTo(rows.get(1).publishedAt());
	}

	@Test
	void theLastPageHasNoCursor() {
		when(feed.find(any())).thenReturn(List.of(card(1), card(2)));

		ContentPageResponse page = service.list(request(null, null, null, null, null, null, null, null, 2, null));

		assertThat(page.items()).hasSize(2);
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void aCursorFromThePreviousPageSetsTheStartPosition() {
		when(feed.find(any())).thenReturn(List.of());
		UUID id = UUID.randomUUID();

		service.list(request(null, null, null, null, null, null, null, null, null, PublishedCursor.encode(PUBLISHED, id)));

		assertThat(capturedCriteria().after().id()).isEqualTo(id);
		assertThat(capturedCriteria().after().publishedAt()).isEqualTo(PUBLISHED);
	}

	@Test
	void aBrokenCursorIsABadRequest() {
		for (String cursor : new String[] { "!!!", "YWJj", "", "cDF8bm90LWEtZGF0ZXx4" }) {
			ContentFeedRequest request = request(null, null, null, null, null, null, null, null, null, cursor);

			assertThatThrownBy(() -> service.list(request)).isInstanceOf(InvalidContentQueryException.class);
		}
	}

	@Test
	void theFromDateMustBeBeforeTheToDate() {
		ContentFeedRequest reversed = request(null, null, null, null, null, PUBLISHED, PUBLISHED.minusSeconds(1), null, null, null);
		ContentFeedRequest equal = request(null, null, null, null, null, PUBLISHED, PUBLISHED, null, null, null);

		assertThatThrownBy(() -> service.list(reversed)).isInstanceOf(InvalidContentQueryException.class);
		assertThatThrownBy(() -> service.list(equal)).isInstanceOf(InvalidContentQueryException.class);
	}

	// ------------------------------------------------------------------ arama

	@Test
	void theSearchTextIsTrimmedKeepsItsCaseAndIsWrappedInWildcards() {
		when(feed.find(any())).thenReturn(List.of());

		service.list(request("  Yeni GÜNCELLEME  ", null, null, null, null, null, null, null, null, null));

		assertThat(capturedCriteria().searchPattern()).isEqualTo("%Yeni GÜNCELLEME%");
	}

	@Test
	void likeWildcardsAndTheEscapeCharacterInTheSearchTextAreEscaped() {
		assertThat(ContentFeedService.searchPattern("100%_!")).isEqualTo("%100!%!_!!%");
		assertThat(ContentFeedService.searchPattern("a_b")).isEqualTo("%a!_b%");
	}

	@Test
	void aBlankSearchMeansNoSearchAndASingleCharacterIsRejected() {
		assertThat(ContentFeedService.searchPattern(null)).isNull();
		assertThat(ContentFeedService.searchPattern("   ")).isNull();
		assertThatThrownBy(() -> ContentFeedService.searchPattern(" a ")).isInstanceOf(InvalidContentQueryException.class);
		assertThat(ContentFeedService.searchPattern("ab")).isEqualTo("%ab%");
		assertThat(ContentFeedService.searchPattern("İSTANBUL")).isEqualTo("%İSTANBUL%");
	}
}
