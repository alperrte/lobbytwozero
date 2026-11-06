package com.ltz.content.domain.exception;

/** İşlem yalnız yayımlanmış içerik için geçerlidir (ör. öne çıkarma). */
public class ContentNotPublishedException extends RuntimeException {

	public ContentNotPublishedException() {
		super("Only published content can be featured.");
	}
}
