package com.ltz.event.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Etkinlik listesi sayfalama parametreleri (EVT-04). Sayfa 0'dan başlar. Sınırlar sunucuda zorunludur
 * (docs/SECURITY.md §9): boyut en çok 50, sayfa en çok 1000. Verilmeyen değerler varsayılanı alır (sayfa 0, boyut 20).
 * Arama, filtreleme ve sıralama seçenekleri EVT-05/EVT-06 kapsamındadır.
 */
public record EventListRequest(
		@Min(0)
		@Max(EventListRequest.MAX_PAGE)
		Integer page,
		@Min(1)
		@Max(EventListRequest.MAX_SIZE)
		Integer size) {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 50;
	public static final int MAX_PAGE = 1000;

	public int pageOrDefault() {
		return page == null ? 0 : page;
	}

	public int sizeOrDefault() {
		return size == null ? DEFAULT_SIZE : size;
	}
}
