package com.ltz.content.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * İçerik türü. {@code ANNOUNCEMENT} resmî LTZ duyurusudur ve yalnız yönetici (ADMIN) oluşturabilir; {@code NEWS} ve
 * {@code USER_CONTENT} kayıtlı her kullanıcıya açıktır. Veritabanında varchar + CHECK olarak tutulur.
 */
public enum ContentType {
	NEWS,
	ANNOUNCEMENT,
	USER_CONTENT;

	/**
	 * JSON'dan yalnız tam (büyük harfli) adla kabul edilir: sıra numarası ({@code 1}), küçük harf ve boş metin gibi
	 * değerler iç sıralamaya bağımlı bir sözleşme yaratacağı için reddedilir (400).
	 */
	@JsonCreator
	public static ContentType fromJson(String value) {
		for (ContentType type : values()) {
			if (type.name().equals(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown content type.");
	}
}
