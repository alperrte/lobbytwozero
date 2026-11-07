package com.ltz.community.api.dto.request;

import com.ltz.community.domain.enums.GameContentType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Oyuna bağlı içerik listesi sorgusu (GAME-11). Sayfalama Game Service modül ucuyla aynıdır: {@code page} 0 tabanlı
 * (varsayılan 0, en çok 10000), {@code size} varsayılan 20, 1-50. {@code type} verilmezse üç türün tümü listelenir.
 */
public record GameContentListRequest(
		GameContentType type,
		@Min(0) @Max(10000) Integer page,
		@Min(1) @Max(50) Integer size) {

	public static final int DEFAULT_SIZE = 20;

	public int pageOrDefault() {
		return page == null ? 0 : page;
	}

	public int sizeOrDefault() {
		return size == null ? DEFAULT_SIZE : size;
	}
}
