package com.ltz.community.application.port;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.ltz.community.domain.enums.PostCategory;
import com.ltz.community.domain.enums.PostSort;
import com.ltz.community.domain.enums.PostStatus;

/**
 * Gönderi akışı sorgusunun kalıcılık katmanından bağımsız tanımı. Sayfalama keyset tabanlıdır: {@code after}
 * verilirse sıralamaya göre yalnız o konumdan sonraki kayıtlar döner. {@code text} verilirse gönderi içeriğinde
 * (büyük/küçük harf duyarsız) aranır; LIKE özel karakterleri kalıcılık katmanında kaçırılır. Filtreler
 * (null = uygulanmaz): {@code gameId}, {@code category}, {@code createdFrom} (dahil), {@code createdTo} (hariç).
 */
public record PostSearchCriteria(
		Set<PostStatus> statuses,
		UUID authorId,
		String text,
		UUID gameId,
		PostCategory category,
		Instant createdFrom,
		Instant createdTo,
		PostSort sort,
		FeedPosition after,
		int limit) {

	/** Bir sayfanın son kaydının konumu; {@code score} yalnız POPULAR sıralamasında anlamlıdır. */
	public record FeedPosition(Instant createdAt, UUID id, int score) {
	}
}
