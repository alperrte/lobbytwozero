package com.ltz.community.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Sabit gönderi kategorileri (ürün kararı); veritabanında varchar + CHECK olarak tutulur. */
public enum PostCategory {
	GENERAL,
	GUIDE,
	QUESTION,
	SCREENSHOT;

	/**
	 * JSON'dan yalnız tam (büyük harfli) adla kabul edilir: sıra numarası ({@code 1}) ve boş metin gibi değerler
	 * iç sıralamaya bağımlı bir sözleşme yaratacağı için reddedilir (400).
	 */
	@JsonCreator
	public static PostCategory fromJson(String value) {
		for (PostCategory category : values()) {
			if (category.name().equals(value)) {
				return category;
			}
		}
		throw new IllegalArgumentException("Unknown post category.");
	}
}
