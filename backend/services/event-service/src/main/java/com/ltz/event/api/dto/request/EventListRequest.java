package com.ltz.event.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Etkinlik listesi parametreleri (EVT-04 sayfalama, EVT-05 ada göre arama). Sayfa 0'dan başlar. Sınırlar sunucuda
 * zorunludur (docs/SECURITY.md §9): boyut en çok 50, sayfa en çok 1000, arama metni en çok 100 karakter ve kontrol
 * karakteri (NUL dahil) içeremez. Verilmeyen değerler varsayılanı alır (sayfa 0, boyut 20, arama yok). Boş veya yalnız
 * boşluktan oluşan arama metni "arama yok" sayılır. Oyuna göre arama EVT-17'ye, filtreleme EVT-06'ya bağlıdır.
 */
public record EventListRequest(
		@Min(0)
		@Max(EventListRequest.MAX_PAGE)
		Integer page,
		@Min(1)
		@Max(EventListRequest.MAX_SIZE)
		Integer size,
		@Size(max = EventListRequest.MAX_QUERY_LENGTH)
		@Pattern(regexp = "[\\P{Cc}]*", message = "must not contain control characters")
		String q) {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 50;
	public static final int MAX_PAGE = 1000;
	public static final int MAX_QUERY_LENGTH = 100;

	public int pageOrDefault() {
		return page == null ? 0 : page;
	}

	public int sizeOrDefault() {
		return size == null ? DEFAULT_SIZE : size;
	}

	/** Kırpılmış arama metni; verilmemişse veya boşsa {@code null} (arama yok). */
	public String searchTextOrNull() {
		if (q == null) {
			return null;
		}
		String stripped = q.strip();
		return stripped.isEmpty() ? null : stripped;
	}
}
