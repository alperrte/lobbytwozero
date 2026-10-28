package com.ltz.community.domain.enums;

/**
 * Akış sıralama ölçütleri (allowlist; SQL'e kullanıcı metni girmez). {@code POPULAR}: popülerlik skoru
 * ({@code like_count + comment_count}) azalan, eşitlikte yenilik.
 */
public enum PostSort {
	NEWEST,
	OLDEST,
	POPULAR
}
