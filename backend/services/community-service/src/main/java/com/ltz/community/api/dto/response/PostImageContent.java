package com.ltz.community.api.dto.response;

import java.util.Arrays;
import java.util.Objects;

/** Görsel içeriği; {@code mimeType} istemciden değil, sunucunun doğruladığı biçimden gelir. */
public record PostImageContent(byte[] content, String mimeType) {

	@Override
	public boolean equals(Object other) {
		return other instanceof PostImageContent(byte[] otherContent, String otherMimeType)
				&& Arrays.equals(content, otherContent)
				&& Objects.equals(mimeType, otherMimeType);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(content) + Objects.hashCode(mimeType);
	}

	@Override
	public String toString() {
		return "PostImageContent[mimeType=" + mimeType + ", size=" + content.length + "]";
	}
}
