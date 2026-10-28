package com.ltz.community.domain.enums;

/**
 * JSON'dan enum okurken yalnız tam (büyük harfli) ada izin verir: sıra numarası ({@code 1}), küçük harf ve boş metin gibi
 * değerler iç sıralamaya bağımlı bir sözleşme yaratacağı için reddedilir (istek 400 döner).
 */
final class StrictEnums {

	private StrictEnums() {
	}

	static <E extends Enum<E>> E exact(Class<E> type, String value) {
		for (E constant : type.getEnumConstants()) {
			if (constant.name().equals(value)) {
				return constant;
			}
		}
		throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ".");
	}
}
